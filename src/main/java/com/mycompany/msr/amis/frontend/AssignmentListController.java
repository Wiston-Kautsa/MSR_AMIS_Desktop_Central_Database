package com.mycompany.msr.amis;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.binding.Bindings;
import javafx.scene.layout.GridPane;

public class AssignmentListController implements Initializable {

    @FXML private TextField txtSearch;
    @FXML private TableView<Assignment> tableAssignments;
    @FXML private TableColumn<Assignment, Void> colNo;
    @FXML private TableColumn<Assignment, Integer> colId;
    @FXML private TableColumn<Assignment, String> colPerson;
    @FXML private TableColumn<Assignment, String> colDepartment;
    @FXML private TableColumn<Assignment, String> colEquipment;
    @FXML private TableColumn<Assignment, String> colReason;
    @FXML private TableColumn<Assignment, Integer> colQty;
    @FXML private TableColumn<Assignment, Integer> colAssigned;
    @FXML private TableColumn<Assignment, String> colStatus;
    @FXML private TableColumn<Assignment, String> colDate;

    private ObservableList<Assignment> assignmentList = FXCollections.observableArrayList();
    private final AssignmentService assignmentService = ServiceRegistry.getAssignmentService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        TableNumbering.install(colNo);
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colPerson.setCellValueFactory(new PropertyValueFactory<>("person"));
        colDepartment.setCellValueFactory(new PropertyValueFactory<>("department"));
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentType"));
        colReason.setCellValueFactory(new PropertyValueFactory<>("reason"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        // Show actual distributed count per assignment
        colAssigned.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getDistributedCount()).asObject());

        // Derive status from distributed vs required quantity
        colStatus.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus()));

        tableAssignments.setItems(assignmentList);

        loadAssignments();
        enableRowMenu();
    }

    private void loadAssignments() {
        try {
            assignmentList.clear();
            assignmentList.addAll(assignmentService.getAssignments());
        } catch (Exception e) {
            showError("Load Error", e.getMessage());
        }
    }

    @FXML
    private void searchAssignment(ActionEvent event) {

        String keyword = txtSearch.getText().toLowerCase();

        if (keyword.isEmpty()) {
            tableAssignments.setItems(assignmentList);
            return;
        }

        ObservableList<Assignment> filtered = FXCollections.observableArrayList();

        for (Assignment a : assignmentList) {
            if (a.getPerson().toLowerCase().contains(keyword)
                    || a.getDepartment().toLowerCase().contains(keyword)
                    || a.getEquipmentType().toLowerCase().contains(keyword)
                    || a.getReason().toLowerCase().contains(keyword)) {
                filtered.add(a);
            }
        }

        tableAssignments.setItems(filtered);
    }

    private void refreshTable() {
        txtSearch.clear();
        loadAssignments();
        tableAssignments.setItems(assignmentList);
    }

    private void enableRowMenu() {
        tableAssignments.setRowFactory(tv -> {
            TableRow<Assignment> row = new TableRow<>();

            ContextMenu menu = new ContextMenu();
            MenuItem edit = new MenuItem("Edit Assignment");
            MenuItem freeze = new MenuItem("Freeze Assignment");
            MenuItem unfreeze = new MenuItem("Unfreeze Assignment");
            MenuItem retire = new MenuItem("Retire Assignment");
            MenuItem restore = new MenuItem("Restore Assignment");
            MenuItem refresh = new MenuItem("Refresh Assignment List");

            edit.setOnAction(event -> editAssignment(row.getItem()));
            freeze.setOnAction(event -> updateAssignmentStatus(row.getItem(), AccessControl.STATUS_FROZEN));
            unfreeze.setOnAction(event -> updateAssignmentStatus(row.getItem(), AccessControl.STATUS_ACTIVE));
            retire.setOnAction(event -> updateAssignmentStatus(row.getItem(), AccessControl.STATUS_RETIRED));
            restore.setOnAction(event -> updateAssignmentStatus(row.getItem(), AccessControl.STATUS_ACTIVE));
            refresh.setOnAction(event -> refreshTable());

            menu.getItems().add(edit);
            menu.getItems().addAll(freeze, unfreeze, retire, restore);
            MenuItem delete = new MenuItem("Delete Assignment");
            delete.setOnAction(event -> deleteAssignment(row.getItem()));
            menu.getItems().add(delete);
            menu.getItems().add(refresh);

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
            showError("Status Error", "Select an assignment first.");
            return;
        }
        try {
            assignmentService.updateAssignmentStatus(assignment.getId(), status);
            refreshTable();
            showInfo("Assignment status updated successfully.");
        } catch (Exception e) {
            showError("Status Error", e.getMessage());
        }
    }

    private void editAssignment(Assignment a) {
        if (a == null) {
            showError("Edit Error", "Select an assignment first.");
            return;
        }

        int distributed = a.getDistributedCount();
        if (distributed > 0) {
            showError("Edit Error", "This assignment already has distributed equipment and can no longer be edited.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Assignment");

        TextField personField = new TextField(a.getPerson());
        TextField departmentField = new TextField(a.getDepartment());
        ComboBox<String> equipmentTypeField = new ComboBox<>();
        equipmentTypeField.getItems().addAll(assignmentService.getAvailableStockByCategory().keySet());
        if (!equipmentTypeField.getItems().contains(a.getEquipmentType())) {
            equipmentTypeField.getItems().add(a.getEquipmentType());
        }
        equipmentTypeField.setEditable(true);
        equipmentTypeField.getEditor().setText(a.getEquipmentType());
        TextField reasonField = new TextField(a.getReason());
        TextField quantityField = new TextField(String.valueOf(a.getQuantity()));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Responsible Person:"), 0, 0);
        grid.add(personField, 1, 0);
        grid.add(new Label("Department:"), 0, 1);
        grid.add(departmentField, 1, 1);
        grid.add(new Label("Equipment Type:"), 0, 2);
        grid.add(equipmentTypeField, 1, 2);
        grid.add(new Label("Reason:"), 0, 3);
        grid.add(reasonField, 1, 3);
        grid.add(new Label("Quantity:"), 0, 4);
        grid.add(quantityField, 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(quantityField.getText().trim());
        } catch (Exception e) {
            showError("Edit Error", "Quantity must be a valid number.");
            return;
        }

        try {
            assignmentService.updateAssignment(
                    a.getId(),
                    personField.getText(),
                    departmentField.getText(),
                    equipmentTypeField.getEditor().getText(),
                    reasonField.getText(),
                    quantity
            );
            refreshTable();
            showInfo("Assignment updated successfully.");
        } catch (Exception e) {
            showError("Edit Error", e.getMessage());
        }
    }

    private void deleteAssignment(Assignment a) {
        if (a == null) return;

        OperationFeedbackHelper.showConfirmation(
                "Confirm Delete",
                "Delete assignment for " + a.getPerson() + "?",
                "Delete",
                "Cancel",
                () -> {
                try {
                    assignmentService.deleteAssignment(a.getId());
                    loadAssignments();
                    showInfo("Deleted successfully.");
                } catch (Exception e) {
                    showError("Delete Error", e.getMessage());
                }
                }
        );
    }

    private void showInfo(String msg) {
        OperationFeedbackHelper.showInfo("Assignments", msg);
    }

    private void showError(String title, String msg) {
        OperationFeedbackHelper.showError(title, msg);
    }
}
