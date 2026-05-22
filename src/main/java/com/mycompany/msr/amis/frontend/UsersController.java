package com.mycompany.msr.amis;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;

public class UsersController implements Initializable {
    private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
    private static final String DEFAULT_SETUP_DEPARTMENT = "MSR";
    private static final class SetupCounts {
        private final int adminCount;
        private final int userCount;

        private SetupCounts(int adminCount, int userCount) {
            this.adminCount = adminCount;
            this.userCount = userCount;
        }
    }


    @FXML private TextField txtName;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtPasswordVisible;
    @FXML private CheckBox chkShowPassword;
    @FXML private ComboBox<String> cmbRole;
    @FXML private ComboBox<String> cmbDepartment;
    @FXML private TextField txtEmail;
    @FXML private Label lblUserStatus;
    @FXML private Label lblPageSubtitle;
    @FXML private TitledPane createUserPane;
    @FXML private TitledPane userDirectoryPane;
    @FXML private javafx.scene.control.Button btnBackToLogin;
    @FXML private javafx.scene.control.Button btnCompleteSetup;

    @FXML private TableView<User> tableUsers;

    @FXML private TableColumn<User, Void> colNo;
    @FXML private TableColumn<User, Integer> colId;
    @FXML private TableColumn<User, String> colName;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, String> colDepartment;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, String> colStatus;

    private final UserService userService = ServiceRegistry.getUserService();
    private ObservableList<User> data;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (!Session.isSetupMode()) {
            AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);
        }
        configureRoleChoices(cmbRole);
        loadDepartments();
        configurePasswordToggle();
        configureSetupMode();

        TableNumbering.install(colNo);
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colDepartment.setCellValueFactory(new PropertyValueFactory<>("department"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        if (!Session.isSetupMode()) {
            setupUsersTableMenu();
            loadUsers();
        } else {
            loadUsers();
            updateSetupProgress();
        }
    }

    private void configureSetupMode() {
        if (!Session.isSetupMode()) {
            return;
        }

        boolean bootstrapAdminFlow = isBootstrapAdminSetup();

        if (lblPageSubtitle != null) {
            lblPageSubtitle.setText(bootstrapAdminFlow
                    ? "Bootstrap account detected. Create the real account that should enter the system next."
                    : "First-time setup detected. Create the main administrator account to continue.");
        }
        if (createUserPane != null) {
            createUserPane.setText(bootstrapAdminFlow
                    ? "Create Real Login Account"
                    : "Create Main Administrator Account");
        }
        if (userDirectoryPane != null) {
            userDirectoryPane.setManaged(true);
            userDirectoryPane.setVisible(true);
            userDirectoryPane.setText("Created Permanent Accounts");
        }
        if (btnBackToLogin != null) {
            btnBackToLogin.setManaged(true);
            btnBackToLogin.setVisible(true);
        }
        if (btnCompleteSetup != null) {
            btnCompleteSetup.setManaged(true);
            btnCompleteSetup.setVisible(true);
            btnCompleteSetup.setDisable(true);
        }
        if (cmbRole != null) {
            if (bootstrapAdminFlow) {
                cmbRole.getItems().setAll(AccessControl.ROLE_ADMIN, AccessControl.ROLE_USER);
                cmbRole.setDisable(false);
            } else {
                cmbRole.getItems().setAll(AccessControl.ROLE_ADMIN);
                cmbRole.setValue(AccessControl.ROLE_ADMIN);
                cmbRole.setDisable(true);
            }
        }
        if (cmbDepartment != null) {
            if (!cmbDepartment.getItems().contains(DEFAULT_SETUP_DEPARTMENT)) {
                cmbDepartment.getItems().add(DEFAULT_SETUP_DEPARTMENT);
            }
            cmbDepartment.setValue(DEFAULT_SETUP_DEPARTMENT);
            cmbDepartment.getEditor().setText(DEFAULT_SETUP_DEPARTMENT);
        }
        updateSetupProgress();
    }

    private void setupUsersTableMenu() {
        if (tableUsers == null) {
            return;
        }

        tableUsers.setRowFactory(tv -> {
            TableRow<User> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();

            MenuItem edit = new MenuItem("Edit User");
            edit.setOnAction(e -> editUser(row.getItem()));

            MenuItem delete = new MenuItem("Delete User");
            delete.setOnAction(e -> deleteUser(row.getItem()));

            MenuItem freeze = new MenuItem("Freeze User");
            freeze.setOnAction(e -> updateUserStatus(row.getItem(), AccessControl.STATUS_FROZEN));

            MenuItem unfreeze = new MenuItem("Unfreeze User");
            unfreeze.setOnAction(e -> updateUserStatus(row.getItem(), AccessControl.STATUS_ACTIVE));

            MenuItem refresh = new MenuItem("Refresh User Management");
            refresh.setOnAction(e -> refreshUsers());

            menu.getItems().add(edit);
            menu.getItems().add(delete);
            menu.getItems().addAll(freeze, unfreeze, refresh);

            row.contextMenuProperty().bind(
                    Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(menu)
            );

            return row;
        });
    }

    private void loadUsers() {
        data = javafx.collections.FXCollections.observableArrayList(userService.getUsers());
        tableUsers.setItems(null);
        tableUsers.setItems(data);
    }

    private void loadDepartments() {
        if (cmbDepartment == null) {
            return;
        }
        cmbDepartment.getItems().clear();
        cmbDepartment.getItems().addAll(userService.getDepartments());
        cmbDepartment.setEditable(true);
        if (Session.isSetupMode()) {
            cmbDepartment.setValue(DEFAULT_SETUP_DEPARTMENT);
            cmbDepartment.getEditor().setText(DEFAULT_SETUP_DEPARTMENT);
        }
    }

    @FXML
    private void handleAddUser(ActionEvent event) {
        String name = txtName.getText().trim();
        String email = txtEmail.getText().trim().toLowerCase();
        String password = currentPasswordInput().trim();
        String role = Session.isSetupMode()
                ? (isBootstrapAdminSetup()
                    ? (cmbRole.getValue() != null ? cmbRole.getValue() : AccessControl.ROLE_USER)
                    : AccessControl.ROLE_ADMIN)
                : (cmbRole.getValue() != null ? cmbRole.getValue() : AccessControl.ROLE_USER);
        String department = comboText(cmbDepartment);

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || department.isEmpty()) {
            showAlert("Error", "Please fill full name, department, email, and password.");
            return;
        }

        if (!email.matches(EMAIL_PATTERN)) {
            showAlert("Error", "Enter a valid email address.");
            return;
        }

        try {
            userService.createUser(name, password, role, department, email);
            if (Session.isSetupMode()) {
                clearForm();
                loadDepartments();
                loadUsers();
                updateSetupProgress();
                showAlert("Success", "Permanent account created. Add the remaining required account type, then finish setup.");
                return;
            }

            clearForm();
            loadDepartments();
            loadUsers();
            showStatus("User information added successfully.");
            showAlert("Success", "User added successfully.");

        } catch (SecurityException e) {
            showAlert("Access Denied", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", resolveUserCreationError(e));
        }
    }

    private void returnToLoginPage() {
        try {
            App.showLoginPage();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            showAlert("Error", "User created, but returning to login failed.");
        }
    }

    private String resolveUserCreationError(Exception exception) {
        if (exception == null) {
            return "User creation failed.";
        }

        String message = exception.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }

        Throwable cause = exception.getCause();
        while (cause != null) {
            String causeMessage = cause.getMessage();
            if (causeMessage != null && !causeMessage.isBlank()) {
                return causeMessage;
            }
            cause = cause.getCause();
        }

        return "User creation failed.";
    }

    @FXML
    private void handleRefreshUsers(ActionEvent event) {
        refreshUsers();
    }

    @FXML
    private void handleEditSelectedUser(ActionEvent event) {
        editUser(selectedUser());
    }

    @FXML
    private void handleFreezeSelectedUser(ActionEvent event) {
        updateUserStatus(selectedUser(), AccessControl.STATUS_FROZEN);
    }

    @FXML
    private void handleUnfreezeSelectedUser(ActionEvent event) {
        updateUserStatus(selectedUser(), AccessControl.STATUS_ACTIVE);
    }

    @FXML
    private void handleDeleteSelectedUser(ActionEvent event) {
        deleteUser(selectedUser());
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) {
        Session.clear();
        returnToLoginPage();
    }

    @FXML
    private void handleCompleteSetup(ActionEvent event) {
        try {
            userService.completeTemporarySetup(Session.getCurrentUser() == null ? "" : Session.getCurrentUser().getEmail());
            Session.clear();
            Platform.runLater(this::returnToLoginPage);
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", resolveUserCreationError(e));
            updateSetupProgress();
        }
    }

    private void editUser(User selected) {
        if (selected == null) {
            showAlert("Error", "Select a user first.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit User");

        TextField nameField = new TextField(selected.getFullName());
        TextField emailField = new TextField(selected.getEmail());
        ComboBox<String> roleField = new ComboBox<>();
        configureRoleChoices(roleField);
        roleField.setValue(selected.getRole());
        ComboBox<String> departmentField = new ComboBox<>();
        departmentField.getItems().addAll(userService.getDepartments());
        departmentField.setEditable(true);
        departmentField.getEditor().setText(selected.getDepartment());
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Leave blank to keep current password");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(new Label("Full Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Email:"), 0, 1);
        grid.add(emailField, 1, 1);
        grid.add(new Label("Role:"), 0, 2);
        grid.add(roleField, 1, 2);
        grid.add(new Label("Department:"), 0, 3);
        grid.add(departmentField, 1, 3);
        grid.add(new Label("New Password:"), 0, 4);
        grid.add(passwordField, 1, 4);

        dialog.getDialogPane().setContent(grid);
        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(
                saveButton,
                ButtonType.CANCEL
        );

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveButton) {
            return;
        }

        String name = nameField.getText().trim();
        String email = emailField.getText().trim().toLowerCase();
        String role = roleField.getValue() != null ? roleField.getValue() : "USER";
        String department = comboText(departmentField);
        String password = passwordField.getText().trim();

        if (name.isEmpty() || email.isEmpty() || department.isEmpty()) {
            showAlert("Error", "Full name, department, and email are required.");
            return;
        }

        if (!email.matches(EMAIL_PATTERN)) {
            showAlert("Error", "Enter a valid email address.");
            return;
        }

        try {
            boolean updated = userService.updateUser(selected.getId(), name, password, role, department, email);

            if (!updated) {
                showAlert("Error", "User was not updated.");
                return;
            }

            loadDepartments();
            loadUsers();
            selectUserById(selected.getId());
            showStatus("User information has been edited successfully.");
            showAlert("Success", "User updated successfully.");

        } catch (SecurityException e) {
            showAlert("Access Denied", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", resolveUserCreationError(e));
        }
    }

    private void deleteUser(User selected) {
        if (selected == null) {
            showAlert("Error", "Select a user first.");
            return;
        }

        try {
            userService.deleteUser(selected.getId());
            loadUsers();
            loadDepartments();
            tableUsers.getSelectionModel().clearSelection();
            showStatus("User information deleted successfully.");
            showAlert("Success", "User deleted successfully.");

        } catch (SecurityException e) {
            showAlert("Access Denied", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Delete failed.");
        }
    }

    private void updateUserStatus(User selected, String nextStatus) {
        if (selected == null) {
            showAlert("Error", "Select a user first.");
            return;
        }

        try {
            boolean updated = userService.updateUserStatus(selected.getId(), nextStatus);
            if (!updated) {
                showAlert("Error", "User status was not updated.");
                return;
            }

            loadUsers();
            showStatus("User status updated to " + nextStatus + ".");
            showAlert("Success", "User status updated successfully.");
        } catch (SecurityException e) {
            showAlert("Access Denied", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Status update failed.");
        }
    }

    private User selectedUser() {
        return tableUsers == null ? null : tableUsers.getSelectionModel().getSelectedItem();
    }

    private void refreshUsers() {
        loadDepartments();
        loadUsers();
        if (tableUsers != null) {
            tableUsers.getSelectionModel().clearSelection();
        }
        if (Session.isSetupMode()) {
            updateSetupProgress();
        } else {
            showStatus("User Management refreshed.");
        }
    }

    private void clearForm() {
        txtName.clear();
        txtPassword.clear();
        if (txtPasswordVisible != null) {
            txtPasswordVisible.clear();
        }
        txtEmail.clear();
        cmbRole.setValue(null);
        cmbRole.setDisable(false);
        cmbDepartment.setValue(null);
        cmbDepartment.getEditor().clear();
        if (chkShowPassword != null) {
            chkShowPassword.setSelected(false);
        }
    }

    private void showAlert(String title, String msg) {
        if (title != null && title.toLowerCase().contains("error")) {
            OperationFeedbackHelper.showError(title, msg);
        } else if (title != null && title.toLowerCase().contains("denied")) {
            OperationFeedbackHelper.showWarning(title, msg);
        } else {
            OperationFeedbackHelper.showInfo(title, msg);
        }
    }

    private String comboText(ComboBox<String> comboBox) {
        if (comboBox == null) {
            return "";
        }
        String editorText = comboBox.getEditor().getText();
        if (editorText != null && !editorText.isBlank()) {
            return editorText.trim();
        }
        if (comboBox.getValue() != null && !comboBox.getValue().isBlank()) {
            return comboBox.getValue().trim();
        }
        return "";
    }

    private void showStatus(String message) {
        if (lblUserStatus != null) {
            lblUserStatus.setText(message);
        }
    }

    private void selectUserById(int userId) {
        if (tableUsers == null || data == null) {
            return;
        }

        for (User user : data) {
            if (user.getId() == userId) {
                tableUsers.getSelectionModel().select(user);
                tableUsers.scrollTo(user);
                return;
            }
        }
    }

    private void configureRoleChoices(ComboBox<String> comboBox) {
        if (comboBox == null) {
            return;
        }

        comboBox.getItems().clear();
        if (Session.isSetupMode()) {
            if (isBootstrapAdminSetup()) {
                comboBox.getItems().addAll(AccessControl.ROLE_ADMIN, AccessControl.ROLE_USER);
                comboBox.setValue(AccessControl.ROLE_USER);
            } else {
                comboBox.getItems().add(AccessControl.ROLE_ADMIN);
                comboBox.setValue(AccessControl.ROLE_ADMIN);
            }
        } else if (Session.hasRole(AccessControl.ROLE_SUPER_ADMIN)) {
            comboBox.getItems().addAll(
                    AccessControl.ROLE_SUPER_ADMIN,
                    AccessControl.ROLE_ADMIN,
                    AccessControl.ROLE_USER
            );
        } else if (Session.hasRole(AccessControl.ROLE_ADMIN)) {
            comboBox.getItems().addAll(
                    AccessControl.ROLE_ADMIN,
                    AccessControl.ROLE_USER
            );
        } else {
            comboBox.getItems().add(AccessControl.ROLE_USER);
        }
    }

    private void configurePasswordToggle() {
        if (txtPassword == null || txtPasswordVisible == null || chkShowPassword == null) {
            return;
        }

        txtPasswordVisible.textProperty().bindBidirectional(txtPassword.textProperty());
        txtPasswordVisible.setManaged(false);
        txtPasswordVisible.setVisible(false);

        chkShowPassword.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            txtPassword.setManaged(!isSelected);
            txtPassword.setVisible(!isSelected);
            txtPasswordVisible.setManaged(isSelected);
            txtPasswordVisible.setVisible(isSelected);
        });
    }

    private String currentPasswordInput() {
        if (chkShowPassword != null && chkShowPassword.isSelected() && txtPasswordVisible != null) {
            return txtPasswordVisible.getText() == null ? "" : txtPasswordVisible.getText();
        }
        return txtPassword == null || txtPassword.getText() == null ? "" : txtPassword.getText();
    }

    private boolean isBootstrapAdminSetup() {
        User currentUser = Session.getCurrentUser();
        return currentUser != null
                && AccessControl.isTemporarySetupAccountEmail(currentUser.getEmail());
    }

    private void updateSetupProgress() {
        if (!Session.isSetupMode()) {
            return;
        }

        try {
            SetupCounts counts = readSetupCounts();
            int adminCount = counts.adminCount;
            int userCount = counts.userCount;
            configureNextSetupRole(counts);

            boolean canFinish = adminCount >= 1 && userCount >= 1;
            if (btnCompleteSetup != null) {
                btnCompleteSetup.setDisable(!canFinish);
            }
            if (canFinish) {
                showStatus("Setup requirements met. Finish setup to disable "
                        + AccessControl.DEFAULT_ADMIN_EMAIL + " and "
                        + AccessControl.DEFAULT_USER_EMAIL + ".");
            } else {
                String nextRole;
                if (adminCount < 1) {
                    nextRole = "ADMIN";
                } else if (userCount < 1) {
                    nextRole = "USER";
                } else {
                    nextRole = "ADMIN or USER";
                }
                showStatus("Bootstrap progress: " + adminCount + " permanent admin(s), " + userCount
                        + " permanent user(s). Next required account: " + nextRole + ".");
            }
        } catch (RuntimeException exception) {
            showStatus("Setup progress could not be loaded. Refresh and try again.");
            if (btnCompleteSetup != null) {
                btnCompleteSetup.setDisable(true);
            }
            return;
        }
    }

    private SetupCounts readSetupCounts() {
        int adminCount = 0;
        int userCount = 0;
        for (User user : userService.getUsers()) {
            if (!AccessControl.STATUS_ACTIVE.equalsIgnoreCase(user.getStatus())) {
                continue;
            }
            if (AccessControl.ROLE_ADMIN.equalsIgnoreCase(user.getRole())) {
                adminCount++;
            } else if (AccessControl.ROLE_USER.equalsIgnoreCase(user.getRole())) {
                userCount++;
            }
        }
        return new SetupCounts(adminCount, userCount);
    }

    private void configureNextSetupRole(SetupCounts counts) {
        if (cmbRole == null || !isBootstrapAdminSetup()) {
            return;
        }
        if (counts.adminCount < 1) {
            cmbRole.setValue(AccessControl.ROLE_ADMIN);
            return;
        }
        if (counts.userCount < 1) {
            cmbRole.setValue(AccessControl.ROLE_USER);
        }
    }

}
