package com.mycompany.msr.amis;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;

public class MaintenanceController implements Initializable {

    @FXML private ComboBox<String> cmbAssetCode;
    @FXML private TextArea txtIssue;
    @FXML private TextArea txtActionTaken;
    @FXML private TextField txtPerformedBy;
    @FXML private DatePicker dateMaintenance;
    @FXML private TextField txtCost;
    @FXML private CheckBox chkCompleted;
    @FXML private TableView<MaintenanceRecord> tableMaintenance;
    @FXML private TableColumn<MaintenanceRecord, Void> colNo;
    @FXML private TableColumn<MaintenanceRecord, String> colAssetCode;
    @FXML private TableColumn<MaintenanceRecord, String> colIssue;
    @FXML private TableColumn<MaintenanceRecord, String> colActionTaken;
    @FXML private TableColumn<MaintenanceRecord, String> colPerformedBy;
    @FXML private TableColumn<MaintenanceRecord, String> colDate;
    @FXML private TableColumn<MaintenanceRecord, String> colCost;
    @FXML private TableColumn<MaintenanceRecord, String> colStatus;

    private final ObservableList<MaintenanceRecord> maintenanceRecords = FXCollections.observableArrayList();
    private final MaintenanceService maintenanceService = ServiceRegistry.getMaintenanceService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        TableNumbering.install(colNo);
        colAssetCode.setCellValueFactory(cell -> cell.getValue().assetCodeProperty());
        colIssue.setCellValueFactory(cell -> cell.getValue().issueProperty());
        colActionTaken.setCellValueFactory(cell -> cell.getValue().actionTakenProperty());
        colPerformedBy.setCellValueFactory(cell -> cell.getValue().performedByProperty());
        colDate.setCellValueFactory(cell -> cell.getValue().maintenanceDateProperty());
        colCost.setCellValueFactory(cell -> cell.getValue().costProperty());
        CurrencyFormatHelper.installCurrencyCellFactory(colCost);
        colStatus.setCellValueFactory(cell -> cell.getValue().statusProperty());
        tableMaintenance.setItems(maintenanceRecords);
        dateMaintenance.setValue(LocalDate.now());
        txtCost.setPromptText("Example: MWK 25,000.00");
        CurrencyFormatHelper.installCurrencyFormatter(txtCost);
        refresh();
    }

    @FXML
    private void saveMaintenance() {
        if (cmbAssetCode.getValue() == null || cmbAssetCode.getValue().isBlank() || txtIssue.getText().isBlank()) {
            OperationFeedbackHelper.showWarning("Missing Details", "Select equipment and enter the maintenance issue.");
            return;
        }

        try {
            maintenanceService.createMaintenanceRecord(
                    cmbAssetCode.getValue(),
                    txtIssue.getText(),
                    txtActionTaken.getText(),
                    txtPerformedBy.getText(),
                    dateMaintenance.getValue() == null ? "" : dateMaintenance.getValue().toString(),
                    CurrencyFormatHelper.formatLocalCurrency(txtCost.getText()),
                    chkCompleted.isSelected()
            );
            clearForm();
            refresh();
        } catch (Exception exception) {
            OperationFeedbackHelper.showError("Save Failed", exception.getMessage());
        }
    }

    @FXML
    private void clearForm() {
        cmbAssetCode.getSelectionModel().clearSelection();
        txtIssue.clear();
        txtActionTaken.clear();
        txtPerformedBy.clear();
        txtCost.clear();
        chkCompleted.setSelected(false);
        dateMaintenance.setValue(LocalDate.now());
    }

    @FXML
    private void refresh() {
        cmbAssetCode.getItems().clear();
        for (Equipment equipment : ServiceRegistry.getEquipmentService().getAllEquipment()) {
            cmbAssetCode.getItems().add(equipment.getAssetCode());
        }
        maintenanceRecords.setAll(maintenanceService.getMaintenanceRecords());
    }
}
