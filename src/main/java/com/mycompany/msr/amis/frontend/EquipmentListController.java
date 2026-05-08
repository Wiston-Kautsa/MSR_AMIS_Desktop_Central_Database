package com.mycompany.msr.amis;

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.Optional;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;

import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.GridPane;
import javafx.scene.control.Label;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableRow;

import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.binding.Bindings;

public class EquipmentListController implements Initializable {

    private final EquipmentService equipmentService = ServiceRegistry.getEquipmentService();

    @FXML
    private TableView<Equipment> equipmentTable;

    @FXML
    private TableColumn<Equipment, Void> colNo;

    @FXML
    private TableColumn<Equipment, String> colAssetCode;

    @FXML
    private TableColumn<Equipment, String> colSerial;

    @FXML
    private TableColumn<Equipment, String> colName;

    @FXML
    private TableColumn<Equipment, String> colCategory;

    @FXML
    private TableColumn<Equipment, String> colCondition;

    @FXML
    private TableColumn<Equipment, String> colSource;

    @FXML
    private TableColumn<Equipment, String> colStatus;

    @FXML
    private TableColumn<Equipment, String> colDate;

    @FXML
    private TextField txtSearch;

    private ObservableList<Equipment> equipmentList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        equipmentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableNumbering.install(colNo);
        colAssetCode.setCellValueFactory(new PropertyValueFactory<>("assetCode"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colCondition.setCellValueFactory(new PropertyValueFactory<>("condition"));
        colSource.setCellValueFactory(new PropertyValueFactory<>("source"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("entryDate"));

        loadEquipment();
        enableRowMenu();
    }

    /* =============================
       LOAD EQUIPMENT
    ============================== */

    private void loadEquipment() {
        try {
            equipmentList.setAll(equipmentService.getAllEquipment());
            equipmentTable.setItems(equipmentList);
        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Error", "Failed to load equipment: " + e.getMessage());
        }
    }

    /* =============================
       CONTEXT MENU
    ============================== */

    private void enableRowMenu() {

        equipmentTable.setRowFactory(tv -> {

            TableRow<Equipment> row = new TableRow<>();

            ContextMenu menu = new ContextMenu();

            MenuItem edit = new MenuItem("Edit Equipment");
            MenuItem refresh = new MenuItem("Refresh Equipment List");
            MenuItem retire = new MenuItem("Retire Equipment");
            MenuItem restore = new MenuItem("Restore Equipment");

            edit.setOnAction(e -> editEquipment(row.getItem()));
            retire.setOnAction(e -> retireEquipment(row.getItem()));
            restore.setOnAction(e -> restoreEquipment(row.getItem()));
            refresh.setOnAction(e -> refreshEquipmentList());

            menu.getItems().add(edit);
            menu.getItems().add(retire);
            menu.getItems().add(restore);
            MenuItem delete = new MenuItem("Delete Equipment");
            delete.setOnAction(e -> deleteEquipment(row.getItem()));
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

    /* =============================
       SEARCH
    ============================== */

    @FXML
    private void searchEquipment(ActionEvent event) {

        String keyword = txtSearch.getText().toLowerCase();

        if (keyword.isEmpty()) {
            equipmentTable.setItems(equipmentList);
            return;
        }

        ObservableList<Equipment> filtered = FXCollections.observableArrayList();

        for (Equipment eq : equipmentList) {

            if (eq.getName().toLowerCase().contains(keyword)
                    || eq.getSerialNumber().toLowerCase().contains(keyword)
                    || eq.getAssetCode().toLowerCase().contains(keyword)
                    || eq.getCategory().toLowerCase().contains(keyword)) {

                filtered.add(eq);
            }
        }

        equipmentTable.setItems(filtered);
    }

    @FXML
    private void exportEquipmentList(ActionEvent event) {

        ObservableList<Equipment> itemsToExport = equipmentTable.getItems();

        if (itemsToExport == null || itemsToExport.isEmpty()) {
            showMessage("No Data", "There is no equipment to export.");
            return;
        }

        File file = FileLocationHelper.fileInDownloads("equipment_list.csv");
        OperationFeedbackHelper.showInfo(
                "Export Starting",
                "Preparing equipment list export.\n\nRows to export: " + itemsToExport.size()
        );

        try (FileWriter writer = new FileWriter(file)) {
            writer.append("System Serial No.,IMEI/Serial Number,Equipment Name,Category,Condition,Source,Status,Entry Date\n");

            for (Equipment equipment : itemsToExport) {
                writer.append(csvSafe(equipment.getAssetCode())).append(",")
                        .append(csvSafe(equipment.getSerialNumber())).append(",")
                        .append(csvSafe(equipment.getName())).append(",")
                        .append(csvSafe(equipment.getCategory())).append(",")
                        .append(csvSafe(equipment.getCondition())).append(",")
                        .append(csvSafe(equipment.getSource())).append(",")
                        .append(csvSafe(equipment.getStatus())).append(",")
                        .append(csvSafe(equipment.getEntryDate())).append("\n");
            }

            OperationFeedbackHelper.showInfo(
                    "Export Complete",
                    "Equipment list exported successfully to:\n" + file.getAbsolutePath()
            );

        } catch (Exception e) {
            e.printStackTrace();
            OperationFeedbackHelper.showError(
                    "Export Failed",
                    "Failed to export the equipment list."
            );
        }
    }

    private void refreshEquipmentList() {
        txtSearch.clear();
        loadEquipment();

        OperationFeedbackHelper.showInfo(
                "Equipment Refreshed",
                "The equipment list was reloaded successfully."
        );
    }

    /* =============================
       EDIT EQUIPMENT
    ============================== */

    private void editEquipment(Equipment equipment) {

        if (equipment == null) {
            showMessage("Edit Equipment", "No equipment selected.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Equipment");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField nameField = new TextField(equipment.getName());
        TextField serialField = new TextField(equipment.getSerialNumber());
        TextField categoryField = new TextField(equipment.getCategory());
        TextField conditionField = new TextField(equipment.getCondition());

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);

        grid.add(new Label("IMEI/Serial Number:"), 0, 1);
        grid.add(serialField, 1, 1);

        grid.add(new Label("Category:"), 0, 2);
        grid.add(categoryField, 1, 2);

        grid.add(new Label("Condition:"), 0, 3);
        grid.add(conditionField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                equipmentService.updateEquipment(
                        equipment.getAssetCode(),
                        serialField.getText(),
                        nameField.getText(),
                        categoryField.getText(),
                        conditionField.getText()
                );
                loadEquipment();
                showMessage("Success", "Equipment updated successfully.");
            } catch (Exception e) {
                e.printStackTrace();
                showMessage("Error", e.getMessage());
            }
        }
    }

    /* =============================
       DELETE EQUIPMENT
    ============================== */

    private void deleteEquipment(Equipment equipment) {

        if (equipment == null) {
            showMessage("Delete Equipment", "No equipment selected.");
            return;
        }

        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText(null);
        confirm.setContentText("Delete equipment " + equipment.getAssetCode() + "?");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        try {
            equipmentService.deleteEquipment(equipment.getAssetCode());
            loadEquipment();
            showMessage("Deleted", "Equipment deleted successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Error", e.getMessage());
        }
    }

    private void retireEquipment(Equipment equipment) {
        if (equipment == null) {
            showMessage("Retire Equipment", "No equipment selected.");
            return;
        }

        try {
            equipmentService.updateEquipmentStatus(equipment.getAssetCode(), AccessControl.STATUS_RETIRED);
            loadEquipment();
            showMessage("Retired", "Equipment marked as retired successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Error", e.getMessage());
        }
    }

    private void restoreEquipment(Equipment equipment) {
        if (equipment == null) {
            showMessage("Restore Equipment", "No equipment selected.");
            return;
        }

        try {
            equipmentService.updateEquipmentStatus(equipment.getAssetCode(), AccessControl.STATUS_ACTIVE);
            loadEquipment();
            showMessage("Restored", "Equipment restored successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Error", e.getMessage());
        }
    }

    /* =============================
       MESSAGE
    ============================== */

    private void showMessage(String title, String message) {

        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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
}
