package com.mycompany.msr.amis;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.HashSet;

public class ReturnEquipmentController implements Initializable {
    private static final Set<String> ALLOWED_RETURN_CONDITIONS = new HashSet<>(
            List.of("Returned", "Good", "Fair", "Damaged", "Faulty", "Lost")
    );
    private static final int BULK_HEADER_ROW_INDEX = 0;
    private static final int BULK_DATA_START_ROW_INDEX = BULK_HEADER_ROW_INDEX + 1;
    private static final String[] BULK_TEMPLATE_HEADERS = {
            "asset_code_or_imei",
            "returned_by",
            "phone",
            "nid",
            "condition",
            "remarks"
    };
    private static final String[] BULK_TEMPLATE_SAMPLE = {
            "MSR-LTP-001",
            "Jane Banda",
            "0991234567",
            "MW123456",
            "Good",
            "Returned to store"
    };

    @FXML private ComboBox<String> cmbAssignments;
    @FXML private TextField txtReturnedBy;
    @FXML private TextField txtPhone;
    @FXML private TextField txtNid;
    @FXML private ComboBox<String> cmbCondition;
    @FXML private TextField txtRemarks;
    @FXML private Label lblAssignOfficer;
    @FXML private Label lblAssignType;
    @FXML private Label lblAssignQty;
    @FXML private Label lblReturnProgress;
    @FXML private TextField txtAssetCode;
    @FXML private TextField txtOfficer;
    @FXML private TextField txtEquipmentType;
    @FXML private TableView<Return> tableReturns;
    @FXML private TableColumn<Return, String> colAsset;
    @FXML private TableColumn<Return, String> colReturnedBy;
    @FXML private TableColumn<Return, String> colPhone;
    @FXML private TableColumn<Return, String> colCondition;
    @FXML private TableColumn<Return, String> colDate;
    @FXML private Label lblFileName;
    @FXML private Button btnSaveReturns;

    private final ObservableList<Return> returnHistoryList = FXCollections.observableArrayList();
    private final Map<String, Assignment> assignmentMap = new LinkedHashMap<>();
    private final List<String> outstandingAssetCodes = new ArrayList<>();
    private final Set<String> stagedResolvedAssetCodes = new LinkedHashSet<>();
    private final List<StagedReturnItem> stagedReturnItems = new ArrayList<>();
    private final DataFormatter dataFormatter = new DataFormatter();

    private Assignment selectedAssignment;
    private int requiredReturnQty = 0;
    private File selectedFile;
    private String pendingOutstandingRemark;
    private final AssignmentService assignmentService = ServiceRegistry.getAssignmentService();
    private final DistributionService distributionService = ServiceRegistry.getDistributionService();
    private final ReturnService returnService = ServiceRegistry.getReturnService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cmbCondition.getItems().addAll("Returned", "Good", "Fair", "Damaged", "Faulty", "Lost");

        setupTable();
        loadAssignmentsPendingReturn();
        loadReturnHistory();
        clearAssignmentDetails();
        updateSaveState();

        txtReturnedBy.setEditable(true);
        cmbAssignments.setOnAction(e -> populateAssignmentDetails());
        txtAssetCode.textProperty().addListener((obs, oldValue, newValue) -> populateReturnedByForAsset(newValue));
    }

    private void setupTable() {
        if (colAsset != null) {
            colAsset.setCellValueFactory(new PropertyValueFactory<>("assetCode"));
            colReturnedBy.setCellValueFactory(new PropertyValueFactory<>("returnedBy"));
            colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
            colCondition.setCellValueFactory(new PropertyValueFactory<>("condition"));
            colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
            tableReturns.setItems(returnHistoryList);
        }
    }

    private void loadAssignmentsPendingReturn() {
        if (cmbAssignments == null) {
            return;
        }

        cmbAssignments.getItems().clear();
        assignmentMap.clear();

        List<Assignment> assignments = assignmentService.getAssignmentsPendingReturn();
        for (Assignment assignment : assignments) {
            if (AccessControl.STATUS_FROZEN.equalsIgnoreCase(assignment.getStatus())
                    || AccessControl.STATUS_RETIRED.equalsIgnoreCase(assignment.getStatus())) {
                continue;
            }
            int outstandingCount = assignment.getDistributedCount();
            if (outstandingCount <= 0) {
                continue;
            }

            String label = assignment.getPerson() + " (" + assignment.getEquipmentType() + ")";
            cmbAssignments.getItems().add(label);
            assignmentMap.put(label, assignment);
        }
    }

    private void populateAssignmentDetails() {
        selectedAssignment = assignmentMap.get(cmbAssignments.getValue());
        stagedResolvedAssetCodes.clear();
        stagedReturnItems.clear();
        outstandingAssetCodes.clear();

        if (selectedAssignment == null) {
            requiredReturnQty = 0;
            selectedFile = null;
            if (lblFileName != null) {
                lblFileName.setText("No file selected");
            }
            clearAssignmentDetails();
            clearEntryFields();
            updateSaveState();
            return;
        }

        requiredReturnQty = assignmentService.getDistributedCountForAssignment(selectedAssignment.getId());
        outstandingAssetCodes.addAll(returnService.getOutstandingAssetCodesForAssignment(selectedAssignment.getId()));
        selectedFile = null;

        lblAssignOfficer.setText(selectedAssignment.getPerson());
        lblAssignType.setText(selectedAssignment.getEquipmentType());
        lblAssignQty.setText(String.valueOf(requiredReturnQty));
        if (lblFileName != null) {
            lblFileName.setText("No file selected");
        }

        if (txtOfficer != null) {
            txtOfficer.setText(selectedAssignment.getPerson());
        }
        if (txtEquipmentType != null) {
            txtEquipmentType.setText(selectedAssignment.getEquipmentType());
        }

        clearEntryFields();
        updateSaveState();
    }

    private void loadReturnHistory() {
        returnHistoryList.clear();
        returnHistoryList.setAll(returnService.getReturns());
    }

    @FXML
    private void handleReturn(ActionEvent event) {
        if (selectedAssignment == null) {
            showWarning("Select an assignment first.");
            return;
        }

        try {
            StagedReturnItem item = buildDraft(
                    txtAssetCode.getText().trim(),
                    txtReturnedBy.getText().trim(),
                    txtPhone.getText().trim(),
                    txtNid.getText().trim(),
                    cmbCondition.getValue(),
                    txtRemarks.getText().trim()
            );

            stagedReturnItems.add(item);
            stagedResolvedAssetCodes.add(item.originalAssetCode);

            clearEntryFields();
            updateSaveState();
            if (btnSaveReturns != null) {
                btnSaveReturns.setDisable(false);
            }

        } catch (Exception e) {
            showWarning(e.getMessage());
        }
    }

    @FXML
    private void saveReturns(ActionEvent event) {
        saveReturns(true);
    }

    private void saveReturns(boolean requireOutstandingConfirmation) {
        if (selectedAssignment == null) {
            showWarning("Select an assignment first.");
            return;
        }
        if (stagedReturnItems.isEmpty()) {
            showWarning("Add at least one returned equipment entry before saving.");
            return;
        }

        if (requireOutstandingConfirmation && !confirmSaveWithRemainingItems()) {
            return;
        }

        try {
            ReturnSaveResult result = returnService.saveReturns(
                    selectedAssignment.getId(),
                    selectedAssignment.getEquipmentType(),
                    toReturnDrafts(stagedReturnItems),
                    pendingOutstandingRemark
            );

            resetAfterSave();
            loadAssignmentsPendingReturn();
            loadReturnHistory();

            if (result.getReplacementAssetCodes().isEmpty()) {
                showInfo("Equipment returns saved successfully.");
            } else {
                showInfo(
                        "Equipment returns saved successfully.\n\nNew replacement asset codes:\n" +
                                String.join("\n", result.getReplacementAssetCodes())
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
            showError("Return failed: " + e.getMessage());
        }
    }

    @FXML
    private void clearForm(ActionEvent event) {
        clearEntryFields();
        stagedReturnItems.clear();
        stagedResolvedAssetCodes.clear();
        selectedFile = null;

        if (lblFileName != null) {
            lblFileName.setText("No file selected");
        }

        if (selectedAssignment == null) {
            if (cmbAssignments != null) {
                cmbAssignments.setValue(null);
            }
            clearAssignmentDetails();
        } else {
            if (txtOfficer != null) {
                txtOfficer.setText(selectedAssignment.getPerson());
            }
            if (txtEquipmentType != null) {
                txtEquipmentType.setText(selectedAssignment.getEquipmentType());
            }
        }

        updateSaveState();
    }

    @FXML
    private void handleBulkUpload(ActionEvent event) {
        if (selectedAssignment == null) {
            showWarning("Select an assignment first.");
            return;
        }
        if (selectedFile == null) {
            showWarning("Choose an Excel file first.");
            return;
        }

        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(selectedFile))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<StagedReturnItem> uploadedItems = new ArrayList<>();
            Set<String> uploadedResolvedAssets = new LinkedHashSet<>();

            // Row 1 is reserved for the bulk file headers; return entries start on row 2.
            for (int rowIndex = BULK_DATA_START_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String identifier = getCellValue(row, 0);
                String returnedBy = getCellValue(row, 1);
                String phone = getCellValue(row, 2);
                String nid = getCellValue(row, 3);
                String condition = getCellValue(row, 4);
                String remarks = getCellValue(row, 5);

                if (identifier.isBlank() && returnedBy.isBlank() && phone.isBlank() && nid.isBlank()
                        && condition.isBlank() && remarks.isBlank()) {
                    continue;
                }
                if (isHeaderLikeRow(identifier, returnedBy, phone, nid, condition, remarks)) {
                    continue;
                }
                if (isSampleRow(identifier, returnedBy, phone, nid, condition, remarks)) {
                    continue;
                }

                StagedReturnItem item = buildDraft(
                        identifier,
                        returnedBy,
                        phone,
                        nid,
                        condition,
                        remarks,
                        uploadedResolvedAssets
                );
                uploadedItems.add(item);
                uploadedResolvedAssets.add(item.originalAssetCode);
            }

            if (uploadedItems.isEmpty()) {
                showWarning("The selected file does not contain any return rows.");
                return;
            }

            stagedReturnItems.clear();
            stagedReturnItems.addAll(uploadedItems);
            stagedResolvedAssetCodes.clear();
            stagedResolvedAssetCodes.addAll(uploadedResolvedAssets);
            updateSaveState();

            if (!confirmSaveWithRemainingItems()) {
                return;
            }

            saveReturns(false);

        } catch (Exception e) {
            e.printStackTrace();
            showError("Bulk upload failed: " + e.getMessage());
        }
    }

    @FXML
    private void downloadTemplate(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Return Template");
        chooser.setInitialFileName("return_bulk_template.xlsx");
        FileLocationHelper.useDownloadsDirectory(chooser);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File targetFile = chooser.showSaveDialog(null);
        if (targetFile == null) {
            OperationFeedbackHelper.showWarning(
                    "Download Cancelled",
                    "Return template download was cancelled."
            );
            return;
        }

        OperationFeedbackHelper.showInfo(
                "Preparing Template",
                "Creating the return bulk template in Downloads."
        );

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Return Template");

            Row headerRow = sheet.createRow(BULK_HEADER_ROW_INDEX);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            for (int i = 0; i < BULK_TEMPLATE_HEADERS.length; i++) {
                Cell headerCell = headerRow.createCell(i);
                headerCell.setCellValue(BULK_TEMPLATE_HEADERS[i]);
                headerCell.setCellStyle(headerStyle);
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream out = new FileOutputStream(targetFile)) {
                workbook.write(out);
            }

            OperationFeedbackHelper.showInfo(
                    "Template Ready",
                    "Return template downloaded successfully to:\n" + targetFile.getAbsolutePath()
            );
        } catch (Exception e) {
            e.printStackTrace();
            OperationFeedbackHelper.showError(
                    "Download Failed",
                    "Failed to download the return template."
            );
        }
    }

    @FXML
    private void chooseFile(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        FileLocationHelper.useDownloadsDirectory(chooser);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );
        selectedFile = chooser.showOpenDialog(null);
        if (selectedFile != null && lblFileName != null) {
            lblFileName.setText(selectedFile.getName());
            OperationFeedbackHelper.showInfo(
                    "File Selected",
                    "Ready to upload return data from:\n" + selectedFile.getName()
            );
        } else {
            OperationFeedbackHelper.showWarning(
                    "No File Selected",
                    "No Excel file was selected for return upload."
            );
        }
    }

    private StagedReturnItem buildDraft(String identifier, String returnedBy, String phone, String nid,
                                        String condition, String remarks)
            throws Exception {
        return buildDraft(identifier, returnedBy, phone, nid, condition, remarks, stagedResolvedAssetCodes);
    }

    private StagedReturnItem buildDraft(
            String identifier,
            String returnedBy,
            String phone,
            String nid,
            String condition,
            String remarks,
            Set<String> reservedAssets
    ) throws Exception {
        if (identifier == null || identifier.isBlank()) {
            throw new Exception("Asset Code / New IMEI is required.");
        }
        if (returnedBy == null || returnedBy.isBlank()) {
            throw new Exception("Returned By is required.");
        }
        if (phone == null || phone.isBlank()) {
            throw new Exception("Phone is required.");
        }
        if (nid == null || nid.isBlank()) {
            throw new Exception("NID is required.");
        }
        if (condition == null || condition.isBlank()) {
            throw new Exception("Please select a condition.");
        }
        if (!ALLOWED_RETURN_CONDITIONS.contains(condition.trim())) {
            throw new Exception(
                    "Invalid return condition: " + condition +
                            ". Use one of: " + String.join(", ", ALLOWED_RETURN_CONDITIONS) + "."
            );
        }

        String trimmedIdentifier = identifier.trim();
        boolean directOutstandingReturn = outstandingAssetCodes.contains(trimmedIdentifier);
        String originalAssetCode;

        if (directOutstandingReturn) {
            originalAssetCode = trimmedIdentifier;
        } else {
            originalAssetCode = getNextOutstandingAssetCode(reservedAssets);
            if (originalAssetCode == null) {
                throw new Exception("There are no remaining outstanding assets available for replacement.");
            }
        }

        if (reservedAssets.contains(originalAssetCode)) {
            throw new Exception("This asset has already been entered in the current save batch.");
        }

        String normalizedReturnedBy = returnedBy.trim();

        return new StagedReturnItem(
                originalAssetCode,
                trimmedIdentifier,
                normalizedReturnedBy,
                phone,
                nid,
                condition,
                remarks,
                !directOutstandingReturn
        );
    }

    private String getNextOutstandingAssetCode(Set<String> reservedAssets) {
        for (String assetCode : outstandingAssetCodes) {
            if (!reservedAssets.contains(assetCode)) {
                return assetCode;
            }
        }
        return null;
    }

    private boolean confirmSaveWithRemainingItems() {
        List<String> remainingAssets = getRemainingAssets();

        long replacementCount = stagedReturnItems.stream().filter(item -> item.replacement).count();
        if (remainingAssets.isEmpty() && replacementCount == 0) {
            pendingOutstandingRemark = "";
            return true;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Complete Return Save");
        dialog.setHeaderText("Capture the reason for equipment that is still outstanding.");

        ButtonType saveButtonType = new ButtonType("Save Returns", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().getStyleClass().add("reset-dialog");

        VBox content = new VBox(14);
        content.getStyleClass().add("reset-dialog-content");

        Label title = new Label("Outstanding Items");
        title.getStyleClass().add("reset-dialog-section-title");

        Label helper = new Label(
                remainingAssets.isEmpty()
                        ? "All selected items will be saved now."
                        : "The assets below will remain outstanding after this save. Enter a remark explaining why they were not returned."
        );
        helper.setWrapText(true);
        helper.getStyleClass().add("reset-dialog-helper");

        TextArea outstandingList = new TextArea(String.join("\n", remainingAssets));
        outstandingList.setEditable(false);
        outstandingList.setWrapText(true);
        outstandingList.setPrefRowCount(Math.min(Math.max(remainingAssets.size(), 2), 6));
        outstandingList.getStyleClass().add("feedback-content");
        outstandingList.setManaged(!remainingAssets.isEmpty());
        outstandingList.setVisible(!remainingAssets.isEmpty());

        Label remarkLabel = new Label("Reason For Outstanding Equipment");
        remarkLabel.getStyleClass().add("reset-dialog-field-label");
        remarkLabel.setManaged(!remainingAssets.isEmpty());
        remarkLabel.setVisible(!remainingAssets.isEmpty());

        TextArea remarkArea = new TextArea();
        remarkArea.setPromptText("Example: 4 tablets are still with field team and will be returned after supervision closes on Friday.");
        remarkArea.setWrapText(true);
        remarkArea.setPrefRowCount(4);
        remarkArea.getStyleClass().add("reset-dialog-input");
        remarkArea.setManaged(!remainingAssets.isEmpty());
        remarkArea.setVisible(!remainingAssets.isEmpty());

        Label replacementLabel = new Label(
                replacementCount > 0
                        ? replacementCount + " replacement item(s) will be added as new equipment and given new asset codes."
                        : ""
        );
        replacementLabel.setWrapText(true);
        replacementLabel.getStyleClass().add("reset-dialog-helper");
        replacementLabel.setManaged(replacementCount > 0);
        replacementLabel.setVisible(replacementCount > 0);

        content.getChildren().addAll(title, helper, outstandingList, remarkLabel, remarkArea, replacementLabel);
        dialog.getDialogPane().setContent(content);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        if (saveButton != null) {
            saveButton.setDisable(!remainingAssets.isEmpty());
            remarkArea.textProperty().addListener((obs, oldValue, newValue) -> {
                if (!remainingAssets.isEmpty()) {
                    saveButton.setDisable(newValue == null || newValue.trim().isEmpty());
                }
            });
        }

        dialog.setResultConverter(button -> {
            if (button == saveButtonType) {
                return remarkArea.getText() == null ? "" : remarkArea.getText().trim();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return false;
        }

        pendingOutstandingRemark = remainingAssets.isEmpty() ? "" : result.get();
        return true;
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

    private void clearAssignmentDetails() {
        lblAssignOfficer.setText("");
        lblAssignType.setText("");
        lblAssignQty.setText("0");
        if (txtOfficer != null) {
            txtOfficer.clear();
        }
        if (txtEquipmentType != null) {
            txtEquipmentType.clear();
        }
    }

    private void clearEntryFields() {
        txtAssetCode.clear();
        txtReturnedBy.clear();
        txtPhone.clear();
        txtNid.clear();
        txtRemarks.clear();
        cmbCondition.setValue(null);
    }

    private void updateSaveState() {
        if (btnSaveReturns != null) {
            btnSaveReturns.setDisable(selectedAssignment == null || stagedReturnItems.isEmpty());
        }
        if (lblReturnProgress != null) {
            lblReturnProgress.setText("Entered Returns: " + stagedReturnItems.size() + " / " + requiredReturnQty);
        }
    }

    private void resetAfterSave() {
        stagedReturnItems.clear();
        stagedResolvedAssetCodes.clear();
        outstandingAssetCodes.clear();
        selectedAssignment = null;
        requiredReturnQty = 0;
        selectedFile = null;
        if (lblFileName != null) {
            lblFileName.setText("No file selected");
        }
        if (cmbAssignments != null) {
            cmbAssignments.getSelectionModel().clearSelection();
        }
        clearAssignmentDetails();
        clearEntryFields();
        pendingOutstandingRemark = "";
        updateSaveState();
    }

    private void populateReturnedByForAsset(String assetCode) {
        if (txtReturnedBy == null) {
            return;
        }
        if (selectedAssignment == null || assetCode == null || assetCode.isBlank()) {
            txtReturnedBy.clear();
            return;
        }

        String trimmedAssetCode = assetCode.trim();
        String lookupAssetCode = outstandingAssetCodes.contains(trimmedAssetCode)
                ? trimmedAssetCode
                : getNextOutstandingAssetCode(stagedResolvedAssetCodes);

        if (lookupAssetCode == null) {
            txtReturnedBy.clear();
            return;
        }

        Distribution distribution = distributionService.getCurrentDistributionForAsset(lookupAssetCode);
        txtReturnedBy.setText(distribution == null ? "" : distribution.getAssignedTo());
    }

    private String getCellValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return dataFormatter.formatCellValue(cell).trim();
        }
        return dataFormatter.formatCellValue(cell).trim();
    }

    private List<ReturnDraft> toReturnDrafts(List<StagedReturnItem> items) {
        List<ReturnDraft> drafts = new ArrayList<>();
        for (StagedReturnItem item : items) {
            drafts.add(new ReturnDraft(
                    item.originalAssetCode,
                    item.enteredIdentifier,
                    item.returnedBy,
                    item.phone,
                    item.nid,
                    item.condition,
                    item.remarks,
                    item.replacement
            ));
        }
        return drafts;
    }

    private List<String> getRemainingAssets() {
        List<String> remainingAssets = new ArrayList<>();
        for (String assetCode : outstandingAssetCodes) {
            if (!stagedResolvedAssetCodes.contains(assetCode)) {
                remainingAssets.add(assetCode);
            }
        }
        return remainingAssets;
    }

    private boolean isHeaderLikeRow(String identifier, String returnedBy, String phone, String nid,
                                    String condition, String remarks) {
        return BULK_TEMPLATE_HEADERS[0].equalsIgnoreCase(identifier)
                && BULK_TEMPLATE_HEADERS[1].equalsIgnoreCase(returnedBy)
                && BULK_TEMPLATE_HEADERS[2].equalsIgnoreCase(phone)
                && BULK_TEMPLATE_HEADERS[3].equalsIgnoreCase(nid)
                && BULK_TEMPLATE_HEADERS[4].equalsIgnoreCase(condition)
                && BULK_TEMPLATE_HEADERS[5].equalsIgnoreCase(remarks);
    }

    private boolean isSampleRow(String identifier, String returnedBy, String phone, String nid,
                                String condition, String remarks) {
        return BULK_TEMPLATE_SAMPLE[0].equalsIgnoreCase(identifier)
                && BULK_TEMPLATE_SAMPLE[1].equalsIgnoreCase(returnedBy)
                && BULK_TEMPLATE_SAMPLE[2].equalsIgnoreCase(phone)
                && BULK_TEMPLATE_SAMPLE[3].equalsIgnoreCase(nid)
                && BULK_TEMPLATE_SAMPLE[4].equalsIgnoreCase(condition)
                && BULK_TEMPLATE_SAMPLE[5].equalsIgnoreCase(remarks);
    }

    private static final class StagedReturnItem {
        private final String originalAssetCode;
        private final String enteredIdentifier;
        private final String returnedBy;
        private final String phone;
        private final String nid;
        private final String condition;
        private final String remarks;
        private final boolean replacement;

        private StagedReturnItem(
                String originalAssetCode,
                String enteredIdentifier,
                String returnedBy,
                String phone,
                String nid,
                String condition,
                String remarks,
                boolean replacement
        ) {
            this.originalAssetCode = originalAssetCode;
            this.enteredIdentifier = enteredIdentifier;
            this.returnedBy = returnedBy;
            this.phone = phone;
            this.nid = nid;
            this.condition = condition;
            this.remarks = remarks;
            this.replacement = replacement;
        }
    }
}
