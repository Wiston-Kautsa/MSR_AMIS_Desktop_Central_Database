package com.mycompany.msr.amis;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.HashSet;

public class DistributeEquipmentController implements Initializable {
    private static final int BULK_HEADER_ROW_INDEX = 0;
    private static final int BULK_DATA_START_ROW_INDEX = BULK_HEADER_ROW_INDEX + 1;
    private static final String[] BULK_TEMPLATE_HEADERS = {
            "asset_code",
            "assigned_to",
            "phone",
            "nid",
            "distribution_date"
    };
    private static final String[] BULK_TEMPLATE_SAMPLE = {
            "MSR-LTP-001",
            "Jane Banda",
            "0991234567",
            "MW123456",
            LocalDate.now().toString()
    };


    @FXML private ComboBox<String> cmbAssignments;
    @FXML private ComboBox<String> equipmentCombo;
    @FXML private TitledPane manualEntryPane;
    @FXML private TitledPane bulkImportPane;

    @FXML private Label lblProgress;
    @FXML private Label lblSelectedAssignment;
    @FXML private Label lblAssignmentStats;
    @FXML private Label lblSelectedFile;

    @FXML private TextField txtName;
    @FXML private TextField txtPhone;
    @FXML private TextField txtNid;

    @FXML private TableView<Distribution> distributionTable;
    @FXML private TableColumn<Distribution, Void> colDistNo;
    @FXML private TableColumn<Distribution, String> colDistAsset;
    @FXML private TableColumn<Distribution, String> colDistName;
    @FXML private TableColumn<Distribution, String> colDistPhone;
    @FXML private TableColumn<Distribution, String> colDistNid;
    @FXML private TableColumn<Distribution, String> colDistDate;

    @FXML private Button btnSave;
    @FXML private Button btnAdd;
    @FXML private Button btnClear;

    private final ObservableList<Distribution> stagedData = FXCollections.observableArrayList();
    private final ObservableList<Distribution> currentDistributionData = FXCollections.observableArrayList();
    private final Map<String, Assignment> assignmentMap = new HashMap<>();
    private final Set<String> usedAssets = new HashSet<>();
    private final DataFormatter dataFormatter = new DataFormatter();

    private File selectedFile;
    private int requiredQty = 0;
    private int existingDistributedQty = 0;
    private Assignment selectedAssignment;
    private final AssignmentService assignmentService = ServiceRegistry.getAssignmentService();
    private final DistributionService distributionService = ServiceRegistry.getDistributionService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        loadAssignments();
        setAssignmentDependentState(false);

        if (equipmentCombo != null) {
            loadAvailableEquipment();
        }

        loadCurrentDistributions();
        btnSave.setDisable(true);
        cmbAssignments.setOnAction(e -> loadAssignmentDetails());
    }

    private void setupTable() {
        TableNumbering.install(colDistNo);
        colDistAsset.setCellValueFactory(cell -> cell.getValue().assetCodeProperty());
        colDistName.setCellValueFactory(cell -> cell.getValue().assignedToProperty());
        colDistPhone.setCellValueFactory(cell -> cell.getValue().phoneProperty());
        colDistNid.setCellValueFactory(cell -> cell.getValue().nidProperty());
        colDistDate.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getDate()));
        distributionTable.setItems(currentDistributionData);
    }

    private void loadCurrentDistributions() {
        currentDistributionData.setAll(distributionService.getCurrentDistributions());
        distributionTable.setItems(currentDistributionData);
    }

    private void loadAssignments() {
        cmbAssignments.getItems().clear();
        assignmentMap.clear();

        List<Assignment> list = assignmentService.getAssignments();
        for (Assignment assignment : list) {
            if (AccessControl.STATUS_FROZEN.equalsIgnoreCase(assignment.getStatus())
                    || AccessControl.STATUS_RETIRED.equalsIgnoreCase(assignment.getStatus())) {
                continue;
            }

            int distributed = assignment.getDistributedCount();
            if (distributed >= assignment.getQuantity()) {
                continue;
            }

            String label = buildAssignmentLabel(assignment, distributed);
            cmbAssignments.getItems().add(label);
            assignmentMap.put(label, assignment);
        }
    }

    private void loadAvailableEquipment() {
        if (equipmentCombo == null) {
            return;
        }

        equipmentCombo.getItems().clear();
        List<String> equipmentList = distributionService.getAvailableEquipment();
        if (equipmentList != null) {
            equipmentCombo.getItems().addAll(equipmentList);
        }
    }

    private void loadAvailableEquipmentForAssignment() {
        if (equipmentCombo == null) {
            return;
        }

        equipmentCombo.getItems().clear();
        if (selectedAssignment == null || selectedAssignment.getEquipmentType().isBlank()) {
            return;
        }

        List<String> equipmentList = distributionService.getAvailableEquipmentByCategory(
                selectedAssignment.getEquipmentType()
        );
        equipmentCombo.getItems().addAll(equipmentList);
    }

    private void loadAssignmentDetails() {
        selectedAssignment = assignmentMap.get(cmbAssignments.getValue());
        if (selectedAssignment == null) {
            requiredQty = 0;
            existingDistributedQty = 0;
            selectedFile = null;
            stagedData.clear();
            usedAssets.clear();
            clearFields();
            lblSelectedAssignment.setText("Selected: -");
            lblSelectedFile.setText("No file selected");
            distributionTable.setItems(currentDistributionData);
            setAssignmentDependentState(false);
            updateProgress();
            return;
        }

        requiredQty = selectedAssignment.getQuantity();
        existingDistributedQty = assignmentService.getDistributedCountForAssignment(selectedAssignment.getId());
        selectedFile = null;
        stagedData.clear();
        usedAssets.clear();
        clearFields();

        lblSelectedAssignment.setText(
                selectedAssignment.getPerson() + " - " + selectedAssignment.getEquipmentType() +
                        " (" + getAssignmentStatus(existingDistributedQty, requiredQty) + ")"
        );
        lblSelectedFile.setText("No file selected");
        txtName.setText(selectedAssignment.getPerson());
        loadAvailableEquipmentForAssignment();
        distributionTable.setItems(stagedData);
        setAssignmentDependentState(true);
        updateProgress();
    }

    @FXML
    private void addManualAssignment(ActionEvent event) {
        if (!isAssignmentSelected()) {
            return;
        }

        if (existingDistributedQty + stagedData.size() >= requiredQty) {
            showWarning("Limit reached");
            return;
        }

        if (equipmentCombo.getValue() == null) {
            showWarning("Select equipment");
            return;
        }

        if (txtName.getText().isEmpty() || txtPhone.getText().isEmpty() || txtNid.getText().isEmpty()) {
            showWarning("Fill all fields");
            return;
        }

        String assetCode = equipmentCombo.getValue();
        if (usedAssets.contains(assetCode)) {
            showWarning("Equipment already used");
            return;
        }

        stagedData.add(new Distribution(
                0,
                assetCode,
                "",
                txtName.getText(),
                txtPhone.getText(),
                txtNid.getText(),
                LocalDate.now()
        ));

        usedAssets.add(assetCode);
        distributionTable.setItems(stagedData);
        clearFields();
        txtName.setText(selectedAssignment.getPerson());
        updateProgress();
    }

    private void clearFields() {
        txtName.clear();
        txtPhone.clear();
        txtNid.clear();
        equipmentCombo.setValue(null);
    }

    @FXML
    private void saveEquipment(ActionEvent event) {
        if (!isAssignmentSelected()) {
            return;
        }

        if (existingDistributedQty + stagedData.size() != requiredQty) {
            showError("Incomplete entries");
            return;
        }

        try {
            distributionService.distributeEquipmentBatch(selectedAssignment.getId(), new ArrayList<>(stagedData));

            showInfo("Saved successfully");

            stagedData.clear();
            usedAssets.clear();
            clearFields();
            loadAssignments();
            resetAfterDistributionSave();
            loadCurrentDistributions();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void updateProgress() {
        int staged = stagedData.size();
        int entered = existingDistributedQty + staged;
        int remaining = Math.max(0, requiredQty - entered);

        lblProgress.setText("Distributed: " + entered + " / " + requiredQty);
        lblAssignmentStats.setText(
                "Required: " + requiredQty +
                " | Already Distributed: " + existingDistributedQty +
                " | This Session: " + staged +
                " | Remaining: " + remaining
        );

        btnAdd.setDisable(requiredQty == 0 || entered >= requiredQty);
        btnSave.setDisable(requiredQty == 0 || staged == 0 || entered != requiredQty);
        btnClear.setDisable(requiredQty == 0);
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
    }

    private void showWarning(String msg) {
        new Alert(Alert.AlertType.WARNING, msg).showAndWait();
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }

    @FXML
    private void clearForm() {
        if (!isAssignmentSelected()) {
            return;
        }

        clearFields();
        stagedData.clear();
        usedAssets.clear();
        selectedFile = null;
        lblSelectedFile.setText("No file selected");
        txtName.setText(selectedAssignment.getPerson());
        loadAvailableEquipmentForAssignment();
        distributionTable.setItems(stagedData);
        updateProgress();
    }

    @FXML
    private void chooseExcelFile() {
        if (!isAssignmentSelected()) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Excel File");
        FileLocationHelper.useDownloadsDirectory(fileChooser);
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );

        selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            lblSelectedFile.setText(selectedFile.getName());
            OperationFeedbackHelper.showInfo(
                    "File Selected",
                    "Ready to upload distribution data from:\n" + selectedFile.getName()
            );
        } else {
            OperationFeedbackHelper.showWarning(
                    "No File Selected",
                    "No Excel file was selected for distribution upload."
            );
        }
    }

    @FXML
    private void downloadTemplate() {
        if (!isAssignmentSelected()) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Distribution Template");
        chooser.setInitialFileName("distribution_bulk_template.xlsx");
        FileLocationHelper.useDownloadsDirectory(chooser);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File targetFile = chooser.showSaveDialog(null);
        if (targetFile == null) {
            OperationFeedbackHelper.showWarning(
                    "Download Cancelled",
                    "Distribution template download was cancelled."
            );
            return;
        }

        OperationFeedbackHelper.showInfo(
                "Preparing Template",
                "Creating the distribution bulk template in Downloads."
        );

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Distribution Template");

            Row headerRow = sheet.createRow(BULK_HEADER_ROW_INDEX);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            for (int i = 0; i < BULK_TEMPLATE_HEADERS.length; i++) {
                Cell headerCell = headerRow.createCell(i);
                headerCell.setCellValue(BULK_TEMPLATE_HEADERS[i]);
                headerCell.setCellStyle(headerStyle);
            }

            Row sampleRow = sheet.createRow(BULK_DATA_START_ROW_INDEX);
            for (int i = 0; i < BULK_TEMPLATE_SAMPLE.length; i++) {
                sampleRow.createCell(i).setCellValue(BULK_TEMPLATE_SAMPLE[i]);
            }

            for (int i = 0; i < BULK_TEMPLATE_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream out = new FileOutputStream(targetFile)) {
                workbook.write(out);
            }

            OperationFeedbackHelper.showInfo(
                    "Template Ready",
                    "Distribution template downloaded successfully to:\n" + targetFile.getAbsolutePath()
            );
        } catch (Exception e) {
            e.printStackTrace();
            OperationFeedbackHelper.showError(
                    "Download Failed",
                    "Failed to download the distribution template."
            );
        }
    }

    @FXML
    private void uploadExcel() {
        if (!isAssignmentSelected()) {
            return;
        }

        if (selectedFile == null) {
            OperationFeedbackHelper.showWarning(
                    "Upload Blocked",
                    "Select an Excel file first before uploading distribution data."
            );
            return;
        }

        OperationFeedbackHelper.showInfo(
                "Upload Starting",
                "Reading distribution data from:\n" + selectedFile.getName()
        );

        try (FileInputStream inputStream = new FileInputStream(selectedFile);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);

            Row headerRow = sheet.getRow(BULK_HEADER_ROW_INDEX);
            if (headerRow == null) {
                OperationFeedbackHelper.showError(
                    "Invalid File",
                    "The Excel file must contain the required header row."
                );
                return;
            }

            Map<String, Integer> columnIndex = readHeaderMap(headerRow);
            if (!hasRequiredHeaders(columnIndex)) {
                OperationFeedbackHelper.showError(
                        "Invalid File",
                        "The Excel file must contain these columns: " + String.join(", ", BULK_TEMPLATE_HEADERS) + "."
                );
                return;
            }

            List<Distribution> importedRows = new ArrayList<>();
            List<String> countedRowDetails = new ArrayList<>();

            // Row 1 is always reserved for template headers; imported data starts on row 2.
            for (int rowIndex = BULK_DATA_START_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String assetCode = getCellValue(row, columnIndex.get("asset_code"));
                String assignedTo = getCellValue(row, columnIndex.get("assigned_to"));
                String phone = getCellValue(row, columnIndex.get("phone"));
                String nid = getCellValue(row, columnIndex.get("nid"));
                String distributionDateText = getCellValue(row, columnIndex.get("distribution_date"));

                if (assetCode.isEmpty() && assignedTo.isEmpty() && phone.isEmpty() && nid.isEmpty()
                        && distributionDateText.isEmpty()) {
                    continue;
                }
                if (isHeaderLikeDataRow(assetCode, assignedTo, phone, nid, distributionDateText)) {
                    continue;
                }
                if (isSampleRow(assetCode, assignedTo, phone, nid, distributionDateText)) {
                    continue;
                }

                if (assetCode.isEmpty() || assignedTo.isEmpty() || phone.isEmpty() || nid.isEmpty()
                        || distributionDateText.isEmpty()) {
                    OperationFeedbackHelper.showError(
                            "Invalid Row",
                            "Each uploaded distribution row must include asset code, name, phone, NID, and distribution date."
                    );
                    return;
                }

                LocalDate distributionDate;
                try {
                    distributionDate = LocalDate.parse(distributionDateText);
                } catch (Exception exception) {
                    OperationFeedbackHelper.showError(
                            "Invalid Row",
                            "Distribution date must use yyyy-MM-dd format. Check row " + (rowIndex + 1) + "."
                    );
                    return;
                }

                importedRows.add(new Distribution(
                        0,
                        assetCode,
                        "",
                        assignedTo,
                        phone,
                        nid,
                        distributionDate
                ));
                countedRowDetails.add("Row " + (rowIndex + 1) + ": " + assetCode + " -> " + assignedTo);
            }

            if (importedRows.isEmpty()) {
                OperationFeedbackHelper.showWarning(
                        "No Data Found",
                        "The selected Excel file does not contain any distribution rows to import."
                );
                return;
            }

            int remainingQty = Math.max(0, requiredQty - existingDistributedQty);
            if (requiredQty > 0 && importedRows.size() != remainingQty) {
                OperationFeedbackHelper.showError(
                        "Quantity Mismatch",
                        "This assignment needs exactly " + remainingQty +
                                " distribution entries, but the file contains " + importedRows.size() + ".\n\n" +
                                "Selected file: " + selectedFile.getName() + "\n" +
                                "Counted rows:\n" + String.join("\n", countedRowDetails)
                );
                return;
            }

            distributionService.distributeEquipmentBatch(selectedAssignment.getId(), importedRows);

            stagedData.clear();
            usedAssets.clear();
            selectedFile = null;
            loadAssignments();
            resetAfterDistributionSave();
            loadCurrentDistributions();

            OperationFeedbackHelper.showInfo(
                    "Upload Complete",
                    "Distribution upload completed successfully.\n\nImported records: " + importedRows.size()
            );
        } catch (Exception e) {
            e.printStackTrace();
            OperationFeedbackHelper.showError(
                    "Upload Failed",
                    "Failed to import the distribution list.\n\n" + e.getMessage()
            );
        }
    }

    private String getCellValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }

        return dataFormatter.formatCellValue(cell).trim();
    }

    private Map<String, Integer> readHeaderMap(Row headerRow) {
        Map<String, Integer> headerMap = new HashMap<>();
        short lastCellNum = headerRow.getLastCellNum();
        for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
            String header = getCellValue(headerRow, cellIndex).toLowerCase();
            if (!header.isBlank()) {
                headerMap.put(header, cellIndex);
            }
        }
        return headerMap;
    }

    private boolean hasRequiredHeaders(Map<String, Integer> headerMap) {
        for (String header : BULK_TEMPLATE_HEADERS) {
            if (!headerMap.containsKey(header)) {
                return false;
            }
        }
        return true;
    }

    private boolean isHeaderLikeDataRow(String assetCode, String assignedTo, String phone, String nid, String distributionDate) {
        return BULK_TEMPLATE_HEADERS[0].equalsIgnoreCase(assetCode)
                && BULK_TEMPLATE_HEADERS[1].equalsIgnoreCase(assignedTo)
                && BULK_TEMPLATE_HEADERS[2].equalsIgnoreCase(phone)
                && BULK_TEMPLATE_HEADERS[3].equalsIgnoreCase(nid)
                && BULK_TEMPLATE_HEADERS[4].equalsIgnoreCase(distributionDate);
    }

    private boolean isSampleRow(String assetCode, String assignedTo, String phone, String nid, String distributionDate) {
        return BULK_TEMPLATE_SAMPLE[0].equalsIgnoreCase(assetCode)
                && BULK_TEMPLATE_SAMPLE[1].equalsIgnoreCase(assignedTo)
                && BULK_TEMPLATE_SAMPLE[2].equalsIgnoreCase(phone)
                && BULK_TEMPLATE_SAMPLE[3].equalsIgnoreCase(nid)
                && BULK_TEMPLATE_SAMPLE[4].equalsIgnoreCase(distributionDate);
    }

    private String buildAssignmentLabel(Assignment assignment, int distributed) {
        return assignment.getPerson() + " (" + assignment.getEquipmentType() + ") - "
                + getAssignmentStatus(distributed, assignment.getQuantity())
                + " (" + distributed + "/" + assignment.getQuantity() + ")";
    }

    private String getAssignmentStatus(int distributed, int required) {
        if (distributed <= 0) {
            return "PENDING";
        }
        if (distributed >= required) {
            return "COMPLETE";
        }
        return "PARTIAL";
    }

    private void setAssignmentDependentState(boolean enabled) {
        if (manualEntryPane != null) {
            manualEntryPane.setDisable(!enabled);
        }
        if (bulkImportPane != null) {
            bulkImportPane.setDisable(!enabled);
        }
    }

    private boolean isAssignmentSelected() {
        return selectedAssignment != null;
    }

    private void resetAfterDistributionSave() {
        Assignment refreshedAssignment = assignmentMap.get(cmbAssignments.getValue());
        if (refreshedAssignment == null) {
            cmbAssignments.getSelectionModel().clearSelection();
            selectedAssignment = null;
            requiredQty = 0;
            existingDistributedQty = 0;
            lblSelectedAssignment.setText("Selected: -");
            lblSelectedFile.setText("No file selected");
            distributionTable.setItems(currentDistributionData);
            setAssignmentDependentState(false);
            updateProgress();
            return;
        }

        selectedAssignment = refreshedAssignment;
        existingDistributedQty = assignmentService.getDistributedCountForAssignment(selectedAssignment.getId());
        selectedFile = null;
        txtName.setText(selectedAssignment.getPerson());
        lblSelectedFile.setText("No file selected");
        loadAvailableEquipmentForAssignment();
        updateProgress();
    }
}
