package com.mycompany.msr.amis;

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public class CreateAssignmentController implements Initializable {

    private static final String DEFAULT_DEPARTMENT = "NLGFC";

    @FXML private ComboBox<String> cmbPerson;
    @FXML private TextField txtDepartment;
    @FXML private ComboBox<String> cmbEquipmentType;
    @FXML private TextField txtReason;
    @FXML private TextField txtQuantity;
    @FXML private Label lblAvailableStock;

    @FXML private TableView<Assignment> tableAssignments;

    @FXML private TableColumn<Assignment, Void> colNo;
    @FXML private TableColumn<Assignment, String> colPerson;
    @FXML private TableColumn<Assignment, String> colDepartment;
    @FXML private TableColumn<Assignment, String> colEquipment;
    @FXML private TableColumn<Assignment, String> colReason;
    @FXML private TableColumn<Assignment, Integer> colQty;
    @FXML private TableColumn<Assignment, String> colStatus;
    @FXML private TableColumn<Assignment, String> colDate;

    private final ObservableList<Assignment> assignmentList = FXCollections.observableArrayList();
    private final Map<String, Integer> availableStockByCategory = new LinkedHashMap<>();
    private final Map<String, User> usersByName = new LinkedHashMap<>();
    private final AssignmentService assignmentService = ServiceRegistry.getAssignmentService();
    private final UserService userService = ServiceRegistry.getUserService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        setupRightClickMenu();
        loadUsers();
        loadEquipmentTypes();
        loadAssignments();

        if (cmbPerson != null) {
            cmbPerson.setPromptText("Select Responsible Person");
            cmbPerson.getSelectionModel().selectedItemProperty().addListener(
                    (obs, oldValue, newValue) -> fillDepartmentFromSelectedUser()
            );
        }

        if (txtDepartment != null) {
            txtDepartment.setText(DEFAULT_DEPARTMENT);
        }

        if (cmbEquipmentType != null) {
            cmbEquipmentType.setPromptText("Select Existing Category");
            cmbEquipmentType.setOnAction(event -> updateAvailableStockLabel());
        }

        updateAvailableStockLabel();
    }

    private void loadUsers() {
        if (cmbPerson == null) {
            return;
        }

        cmbPerson.getItems().clear();
        usersByName.clear();

        try {
            for (User u : userService.getUsers()) {
                if (!AccessControl.STATUS_ACTIVE.equalsIgnoreCase(u.getStatus())) {
                    continue;
                }
                if (AccessControl.isTemporarySetupAccountEmail(u.getEmail())) {
                    continue;
                }
                cmbPerson.getItems().add(u.getFullName());
                usersByName.put(u.getFullName(), u);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fillDepartmentFromSelectedUser() {
        if (cmbPerson == null || txtDepartment == null) {
            return;
        }

        User selectedUser = usersByName.get(cmbPerson.getValue());
        if (selectedUser == null) {
            txtDepartment.setText(DEFAULT_DEPARTMENT);
            return;
        }

        String department = selectedUser.getDepartment();
        txtDepartment.setText((department == null || department.isBlank()) ? DEFAULT_DEPARTMENT : department);
    }

    private void loadEquipmentTypes() {
        if (cmbEquipmentType == null) {
            return;
        }

        availableStockByCategory.clear();
        availableStockByCategory.putAll(assignmentService.getAvailableStockByCategory());

        cmbEquipmentType.getItems().clear();
        cmbEquipmentType.getItems().addAll(availableStockByCategory.keySet());
    }

    private void updateAvailableStockLabel() {
        if (lblAvailableStock == null) {
            return;
        }

        String type = cmbEquipmentType != null ? cmbEquipmentType.getValue() : null;
        if (type == null || type.isBlank()) {
            lblAvailableStock.setText("Available in stock: Select existing category");
            return;
        }

        int available = availableStockByCategory.getOrDefault(type, 0);
        lblAvailableStock.setText("Available in stock: " + available);
    }

    private void setupTable() {
        TableNumbering.install(colNo);
        colPerson.setCellValueFactory(c -> c.getValue().personProperty());
        colDepartment.setCellValueFactory(c -> c.getValue().departmentProperty());
        colEquipment.setCellValueFactory(c -> c.getValue().equipmentTypeProperty());
        colReason.setCellValueFactory(c -> c.getValue().reasonProperty());
        colQty.setCellValueFactory(c -> c.getValue().quantityProperty().asObject());
        colStatus.setCellValueFactory(c -> {
            Assignment assignment = c.getValue();
            return new javafx.beans.property.SimpleStringProperty(assignment.getStatus());
        });
        colDate.setCellValueFactory(c -> c.getValue().dateProperty());

        tableAssignments.setItems(assignmentList);
    }

    private void loadAssignments() {
        assignmentList.clear();
        assignmentList.addAll(assignmentService.getAssignments());
    }

    private void setupRightClickMenu() {
        tableAssignments.setRowFactory(tv -> {
            TableRow<Assignment> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem freeze = new MenuItem("Freeze Assignment");
            MenuItem unfreeze = new MenuItem("Unfreeze Assignment");
            MenuItem retire = new MenuItem("Retire Assignment");
            MenuItem restore = new MenuItem("Restore Assignment");
            freeze.setOnAction(e -> updateAssignmentStatus(row.getItem(), AccessControl.STATUS_FROZEN));
            unfreeze.setOnAction(e -> updateAssignmentStatus(row.getItem(), AccessControl.STATUS_ACTIVE));
            retire.setOnAction(e -> updateAssignmentStatus(row.getItem(), AccessControl.STATUS_RETIRED));
            restore.setOnAction(e -> updateAssignmentStatus(row.getItem(), AccessControl.STATUS_ACTIVE));
            menu.getItems().addAll(freeze, unfreeze, retire, restore);
            MenuItem delete = new MenuItem("Delete Assignment");
            delete.setOnAction(e -> {
                Assignment selected = row.getItem();
                if (selected != null) {
                    try {
                        assignmentService.deleteAssignment(selected.getId());
                        loadAssignments();
                        loadEquipmentTypes();
                        updateAvailableStockLabel();
                    } catch (Exception ex) {
                        showError("Error", ex.getMessage());
                    }
                }
            });
            menu.getItems().add(delete);

            row.contextMenuProperty().bind(
                    Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(menu)
            );

            return row;
        });
    }

    private void updateAssignmentStatus(Assignment assignment, String status) {
        if (assignment == null) {
            showError("Error", "Select an assignment first.");
            return;
        }
        try {
            assignmentService.updateAssignmentStatus(assignment.getId(), status);
            loadAssignments();
            loadEquipmentTypes();
            updateAvailableStockLabel();
        } catch (Exception e) {
            showError("Error", e.getMessage());
        }
    }

    @FXML
    private void saveAssignment() {
        String person = cmbPerson != null && cmbPerson.getValue() != null ? cmbPerson.getValue() : "";
        String dept = safe(txtDepartment);
        String type = cmbEquipmentType != null && cmbEquipmentType.getValue() != null
                ? cmbEquipmentType.getValue().trim()
                : "";
        String reason = safe(txtReason);
        String qtyText = safe(txtQuantity);

        if (dept.isEmpty()) {
            User selectedUser = usersByName.get(person);
            if (selectedUser != null && selectedUser.getDepartment() != null && !selectedUser.getDepartment().isBlank()) {
                dept = selectedUser.getDepartment().trim();
                txtDepartment.setText(dept);
            }
        }

        if (person.isEmpty() || type.isEmpty() || reason.isEmpty() || qtyText.isEmpty()) {
            showWarning("Missing Fields", "Responsible person, equipment category, reason, and quantity are required.");
            return;
        }

        int qty;
        try {
            qty = Integer.parseInt(qtyText);
            if (qty <= 0) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            showWarning("Invalid", "Quantity must be a valid positive number.");
            return;
        }

        try {
            int available = assignmentService.getAvailableStock(type);
            if (qty > available) {
                showWarning("Stock", "Only " + available + " " + type + " item(s) are currently available.");
                return;
            }

            assignmentService.createAssignment(person, dept.isEmpty() ? DEFAULT_DEPARTMENT : dept, type, reason, qty);
            clearForm();
            loadAssignments();
            loadEquipmentTypes();
            updateAvailableStockLabel();

        } catch (Exception e) {
            showError("DB Error", e.getMessage());
        }
    }

    @FXML
    private void clearForm() {
        if (cmbPerson != null) {
            cmbPerson.setValue(null);
        }
        if (cmbEquipmentType != null) {
            cmbEquipmentType.setValue(null);
        }
        if (txtReason != null) {
            txtReason.clear();
        }
        txtDepartment.setText(DEFAULT_DEPARTMENT);
        txtQuantity.clear();
        updateAvailableStockLabel();
    }

    @FXML
    private void exportAssignments() {
        ObservableList<Assignment> itemsToExport = tableAssignments.getItems();

        if (itemsToExport == null || itemsToExport.isEmpty()) {
            showWarning("No Data", "There are no assignment records to export.");
            return;
        }

        File file = FileLocationHelper.fileInDownloads("assignment_list.csv");
        OperationFeedbackHelper.showInfo(
                "Export Starting",
                "Preparing assignment list export.\n\nRows to export: " + itemsToExport.size()
        );

        try (FileWriter writer = new FileWriter(file)) {
            writer.append("Person,Department,Equipment,Reason,Quantity,Status,Date\n");

            for (Assignment assignment : itemsToExport) {
                writer.append(csvSafe(assignment.getPerson())).append(",")
                        .append(csvSafe(assignment.getDepartment())).append(",")
                        .append(csvSafe(assignment.getEquipmentType())).append(",")
                        .append(csvSafe(assignment.getReason())).append(",")
                        .append(String.valueOf(assignment.getQuantity())).append(",")
                        .append(csvSafe(assignment.getStatus())).append(",")
                        .append(csvSafe(assignment.getDate())).append("\n");
            }

            OperationFeedbackHelper.showInfo(
                    "Export Complete",
                    "Assignment list exported successfully to:\n" + file.getAbsolutePath()
            );

        } catch (Exception e) {
            e.printStackTrace();
            OperationFeedbackHelper.showError(
                    "Export Failed",
                    "Failed to export the assignment list."
            );
        }
    }

    private String safe(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String csvSafe(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void showWarning(String title, String message) {
        OperationFeedbackHelper.showWarning(title, message);
    }

    private void showError(String title, String message) {
        OperationFeedbackHelper.showError(title, message);
    }
}
