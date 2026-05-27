package com.mycompany.msr.amis;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public final class DepartmentsController implements Initializable {

    private static final String DEFAULT_DEPARTMENT = "MSR";

    private final DepartmentService departmentService = ServiceRegistry.getDepartmentService();

    @FXML private TextField txtDepartmentName;
    @FXML private Button btnSaveDepartment;
    @FXML private Button btnClearDepartment;
    @FXML private Button btnRefreshDepartments;
    @FXML private TableView<String> tableDepartments;
    @FXML private TableColumn<String, String> colDepartmentName;
    @FXML private Label lblDepartmentStatus;

    private String selectedDepartment;
    private final ObservableList<String> departments = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);
        configureTable();
        refreshDepartments();
    }

    @FXML
    private void handleSaveDepartment() {
        String name = normalizedInput();
        if (name.isBlank()) {
            showStatus("Department name is required.");
            return;
        }

        try {
            if (selectedDepartment == null || selectedDepartment.isBlank()) {
                departmentService.createDepartment(name);
                showStatus("Department created: " + name);
            } else {
                if (DEFAULT_DEPARTMENT.equalsIgnoreCase(selectedDepartment.trim())) {
                    showError("The default MSR department cannot be renamed.");
                    return;
                }
                departmentService.updateDepartment(selectedDepartment, name);
                showStatus("Department updated: " + name);
            }
            clearSelection();
            refreshDepartments();
        } catch (Exception exception) {
            showError(resolveMessage(exception));
        }
    }

    @FXML
    private void handleClearDepartment() {
        clearSelection();
    }

    @FXML
    private void handleRefreshDepartments() {
        refreshDepartments();
    }

    private void configureTable() {
        colDepartmentName.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue()));
        tableDepartments.setItems(departments);
        tableDepartments.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            selectedDepartment = newValue;
            if (newValue != null) {
                txtDepartmentName.setText(newValue);
                btnSaveDepartment.setText("Update Department");
            }
        });

        tableDepartments.setRowFactory(table -> {
            TableRow<String> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem edit = new MenuItem("Edit Department");
            edit.setOnAction(event -> selectDepartment(row.getItem()));
            MenuItem delete = new MenuItem("Delete Department");
            delete.setOnAction(event -> deleteDepartment(row.getItem()));
            menu.getItems().addAll(edit, delete);
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(menu)
            );
            return row;
        });
    }

    private void refreshDepartments() {
        showStatus("Loading departments...");
        tableDepartments.setDisable(true);
        UiBackgroundLoader.run(
                "departments-loader",
                departmentService::getDepartments,
                loaded -> {
            departments.setAll(loaded == null ? java.util.List.of() : loaded);
            if (!departments.contains(DEFAULT_DEPARTMENT)) {
                departments.add(DEFAULT_DEPARTMENT);
                FXCollections.sort(departments);
            }
            tableDepartments.setDisable(false);
            showStatus("Departments refreshed.");
                },
                error -> {
            tableDepartments.setDisable(false);
            showError(resolveMessage(error));
                }
        );
    }

    private void selectDepartment(String department) {
        if (department == null || department.isBlank()) {
            return;
        }
        selectedDepartment = department;
        txtDepartmentName.setText(department);
        btnSaveDepartment.setText("Update Department");
        tableDepartments.getSelectionModel().select(department);
    }

    private void deleteDepartment(String department) {
        if (department == null || department.isBlank()) {
            showStatus("Select a department first.");
            return;
        }
        if (DEFAULT_DEPARTMENT.equalsIgnoreCase(department.trim())) {
            showError("The default MSR department cannot be deleted.");
            return;
        }

        try {
            departmentService.deleteDepartment(department);
            clearSelection();
            refreshDepartments();
            showStatus("Department deleted: " + department);
        } catch (Exception exception) {
            showError(resolveMessage(exception));
        }
    }

    private void clearSelection() {
        selectedDepartment = null;
        txtDepartmentName.clear();
        tableDepartments.getSelectionModel().clearSelection();
        btnSaveDepartment.setText("Add Department");
        showStatus("Ready.");
    }

    private String normalizedInput() {
        return txtDepartmentName == null || txtDepartmentName.getText() == null
                ? ""
                : txtDepartmentName.getText().trim();
    }

    private void showStatus(String message) {
        if (lblDepartmentStatus != null) {
            lblDepartmentStatus.setText(message == null ? "" : message);
        }
    }

    private void showError(String message) {
        showStatus(message);
        OperationFeedbackHelper.showError("Department Error", message);
    }

    private String resolveMessage(Throwable exception) {
        return exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Department operation failed."
                : exception.getMessage();
    }
}
