package com.mycompany.msr.amis;

import javafx.beans.property.SimpleStringProperty;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
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
import java.util.ResourceBundle;
import java.util.Set;
import java.util.HashSet;

public class ReturnEquipmentController implements Initializable {
    private static final List<String> RETURN_CONDITION_ORDER = List.of(
            "Returned", "Good", "Fair", "Damaged", "Faulty", "Lost"
    );
    private static final Map<String, String> RETURN_CONDITION_LABELS = Map.of(
            "Returned", "Returned to owner - borrowed external equipment handed back",
            "Good", "Good - working and complete",
            "Fair", "Fair - usable with minor wear",
            "Damaged", "Damaged - physical damage",
            "Faulty", "Faulty - not working correctly",
            "Lost", "Lost - not physically returned"
    );
    private static final Set<String> ALLOWED_RETURN_CONDITIONS = new HashSet<>(RETURN_CONDITION_LABELS.keySet());
    private static final int BULK_HEADER_ROW_INDEX = 0;
    private static final int BULK_DATA_START_ROW_INDEX = BULK_HEADER_ROW_INDEX + 1;
    private static final String[] BULK_TEMPLATE_HEADERS = {
            "asset_code_or_imei",
            "returned_by",
            "phone",
            "nid",
            "condition (choose: Returned to owner, Good, Fair, Damaged, Faulty, Lost)",
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
    @FXML private TableColumn<Return, Void> colReturnNo;
    @FXML private TableColumn<Return, String> colAsset;
    @FXML private TableColumn<Return, String> colReturnedBy;
    @FXML private TableColumn<Return, String> colPhone;
    @FXML private TableColumn<Return, String> colCondition;
    @FXML private TableColumn<Return, String> colDate;
    @FXML private VBox outstandingReasonPane;
    @FXML private TableView<OutstandingAssetRow> tableOutstandingAssets;
    @FXML private TableColumn<OutstandingAssetRow, Void> colOutstandingNo;
    @FXML private TableColumn<OutstandingAssetRow, String> colOutstandingAsset;
    @FXML private TableColumn<OutstandingAssetRow, String> colOutstandingUser;
    @FXML private TableColumn<OutstandingAssetRow, String> colOutstandingPhone;
    @FXML private TableColumn<OutstandingAssetRow, String> colOutstandingNid;
    @FXML private TextArea txtOutstandingReason;
    @FXML private Label lblFileName;
    @FXML private Button btnSaveReturns;
    @FXML private Button btnAddReturn;
    @FXML private Button btnClear;
    @FXML private Button btnDownloadTemplate;
    @FXML private Button btnChooseFile;
    @FXML private Button btnUpload;
    @FXML private Button btnApplyOutstandingReason;
    @FXML private VBox manualEntryPane;
    @FXML private VBox bulkImportPane;

    private final ObservableList<Return> returnHistoryList = FXCollections.observableArrayList();
    private final ObservableList<OutstandingAssetRow> outstandingAssetRows = FXCollections.observableArrayList();
    private final Map<String, Assignment> assignmentMap = new LinkedHashMap<>();
    private final List<String> outstandingAssetCodes = new ArrayList<>();
    private final Set<String> stagedResolvedAssetCodes = new LinkedHashSet<>();
    private final List<StagedReturnItem> stagedReturnItems = new ArrayList<>();
    private final DataFormatter dataFormatter = new DataFormatter();

    private Assignment selectedAssignment;
    private int requiredReturnQty = 0;
    private File selectedFile;
    private String pendingReturnStatisticsMessage = "";
    private final AssignmentService assignmentService = ServiceRegistry.getAssignmentService();
    private final DistributionService distributionService = ServiceRegistry.getDistributionService();
    private final ReturnService returnService = ServiceRegistry.getReturnService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cmbCondition.setPromptText("Select actual return condition");
        cmbCondition.getItems().addAll(getReturnConditionDisplayLabels());

        setupTable();
        setupOutstandingAssetsTable();
        loadAssignmentsPendingReturn();
        loadReturnHistory();
        clearAssignmentDetails();
        setAssignmentDependentState(false);
        updateSaveState();

        txtReturnedBy.setEditable(true);
        cmbAssignments.setOnAction(e -> populateAssignmentDetails());
        txtAssetCode.textProperty().addListener((obs, oldValue, newValue) -> populateReturnedByForAsset(newValue));
    }

    private void setupTable() {
        if (colAsset != null) {
            TableNumbering.install(colReturnNo);
            colAsset.setCellValueFactory(new PropertyValueFactory<>("assetCode"));
            colReturnedBy.setCellValueFactory(new PropertyValueFactory<>("returnedBy"));
            colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
            colCondition.setCellValueFactory(new PropertyValueFactory<>("condition"));
            colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
            tableReturns.setItems(returnHistoryList);
        }
    }

    private void setupOutstandingAssetsTable() {
        if (tableOutstandingAssets == null) {
            return;
        }
        TableNumbering.install(colOutstandingNo);
        colOutstandingAsset.setCellValueFactory(cell -> cell.getValue().assetCodeProperty());
        colOutstandingUser.setCellValueFactory(cell -> cell.getValue().assignedToProperty());
        colOutstandingPhone.setCellValueFactory(cell -> cell.getValue().phoneProperty());
        colOutstandingNid.setCellValueFactory(cell -> cell.getValue().nidProperty());
        tableOutstandingAssets.setItems(outstandingAssetRows);
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
            refreshOutstandingReasonRows();
            setAssignmentDependentState(false);
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
        refreshOutstandingReasonRows();
        setAssignmentDependentState(true);
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
                    txtRemarks.getText().trim(),
                    stagedResolvedAssetCodes,
                    getStagedEnteredIdentifiers()
            );

            stagedReturnItems.add(item);
            stagedResolvedAssetCodes.add(item.originalAssetCode);

            clearEntryFields();
            refreshOutstandingReasonRows();
            updateSaveState();

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

        Map<String, String> outstandingReasons = collectOutstandingReasons();
        if (outstandingReasons == null) {
            return;
        }
        int outstandingReasonCount = outstandingReasons.size();

        try {
            ReturnSaveResult result = returnService.saveReturns(
                    selectedAssignment.getId(),
                    selectedAssignment.getEquipmentType(),
                    toReturnDrafts(stagedReturnItems),
                    outstandingReasons
            );

            resetAfterSave();
            loadAssignmentsPendingReturn();
            loadReturnHistory();

            showInfo(buildReturnSuccessMessage(outstandingReasonCount, result.getReplacementAssetCodes()));
            pendingReturnStatisticsMessage = "";

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
        if (txtOutstandingReason != null) {
            txtOutstandingReason.clear();
        }

        if (selectedAssignment == null) {
            if (cmbAssignments != null) {
                cmbAssignments.setValue(null);
            }
            clearAssignmentDetails();
            setAssignmentDependentState(false);
        } else {
            if (txtOfficer != null) {
                txtOfficer.setText(selectedAssignment.getPerson());
            }
            if (txtEquipmentType != null) {
                txtEquipmentType.setText(selectedAssignment.getEquipmentType());
            }
        }

        refreshOutstandingReasonRows();
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
            Set<String> uploadedEnteredIdentifiers = new LinkedHashSet<>();
            List<String> acceptedAssetCodes = new ArrayList<>();
            List<String> rejectedAlreadyReturned = new ArrayList<>();
            List<String> rejectedOther = new ArrayList<>();

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

                try {
                    StagedReturnItem item = buildDraft(
                            identifier,
                            returnedBy,
                            phone,
                            nid,
                            condition,
                            remarks,
                            uploadedResolvedAssets,
                            uploadedEnteredIdentifiers
                    );
                    uploadedItems.add(item);
                    uploadedResolvedAssets.add(item.originalAssetCode);
                    uploadedEnteredIdentifiers.add(item.enteredIdentifier);
                    acceptedAssetCodes.add(item.originalAssetCode);
                } catch (ReturnEntryRejectedException e) {
                    if (e.reason == RejectionReason.ALREADY_RETURNED_UNDER_ASSIGNMENT) {
                        rejectedAlreadyReturned.add(identifier.trim());
                    } else {
                        rejectedOther.add(identifier.trim() + " - " + e.getMessage());
                    }
                } catch (Exception e) {
                    rejectedOther.add(identifier.trim() + " - " + e.getMessage());
                }
            }

            if (uploadedItems.isEmpty()) {
                showWarning(buildReturnStatisticsMessage(acceptedAssetCodes, rejectedAlreadyReturned, rejectedOther));
                return;
            }

            stagedReturnItems.clear();
            stagedReturnItems.addAll(uploadedItems);
            stagedResolvedAssetCodes.clear();
            stagedResolvedAssetCodes.addAll(uploadedResolvedAssets);
            pendingReturnStatisticsMessage = buildReturnStatisticsMessage(
                    acceptedAssetCodes,
                    rejectedAlreadyReturned,
                    rejectedOther
            );
            refreshOutstandingReasonRows();
            updateSaveState();

            if (!getRemainingAssets().isEmpty()) {
                focusOutstandingReasons();
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
        if (selectedAssignment == null) {
            showWarning("Select an assignment first.");
            return;
        }

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
            Sheet conditionSheet = workbook.createSheet("Return Conditions");

            Row headerRow = sheet.createRow(BULK_HEADER_ROW_INDEX);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setWrapText(true);

            for (int i = 0; i < BULK_TEMPLATE_HEADERS.length; i++) {
                Cell headerCell = headerRow.createCell(i);
                headerCell.setCellValue(BULK_TEMPLATE_HEADERS[i]);
                headerCell.setCellStyle(headerStyle);
                sheet.autoSizeColumn(i);
            }
            sheet.setColumnWidth(4, 12000);

            addConditionDropdown(sheet);
            writeConditionGuide(conditionSheet, headerStyle);

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
        if (selectedAssignment == null) {
            showWarning("Select an assignment first.");
            return;
        }

        FileChooser chooser = new FileChooser();
        FileLocationHelper.useDownloadsDirectory(chooser);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );
        selectedFile = chooser.showOpenDialog(null);
        if (selectedFile != null && lblFileName != null) {
            lblFileName.setText(selectedFile.getName());
            updateSaveState();
            OperationFeedbackHelper.showInfo(
                    "File Selected",
                    "Ready to upload return data from:\n" + selectedFile.getName()
            );
        } else {
            selectedFile = null;
            if (lblFileName != null) {
                lblFileName.setText("No file selected");
            }
            OperationFeedbackHelper.showWarning(
                    "No File Selected",
                    "No Excel file was selected for return upload."
            );
            updateSaveState();
        }
    }

    private String buildReturnSuccessMessage(int outstandingReasonCount, List<String> replacementAssetCodes) {
        StringBuilder message = new StringBuilder("Equipment returns saved successfully.");
        if (pendingReturnStatisticsMessage != null && !pendingReturnStatisticsMessage.isBlank()) {
            message.append("\n\n").append(pendingReturnStatisticsMessage);
        }
        if (outstandingReasonCount > 0) {
            message.append("\n\nOutstanding reason recorded for ")
                    .append(outstandingReasonCount)
                    .append(" equipment item(s).");
        }
        if (replacementAssetCodes != null && !replacementAssetCodes.isEmpty()) {
            message.append("\n\nNew replacement asset codes:\n")
                    .append(String.join("\n", replacementAssetCodes));
        }
        return message.toString();
    }

    private String buildReturnStatisticsMessage(List<String> acceptedAssetCodes,
                                                List<String> rejectedAlreadyReturned,
                                                List<String> rejectedOther) {
        StringBuilder message = new StringBuilder("Return upload statistics");
        message.append("\n\nAccepted as returned equipment: ")
                .append(acceptedAssetCodes == null ? 0 : acceptedAssetCodes.size());
        if (acceptedAssetCodes != null && !acceptedAssetCodes.isEmpty()) {
            message.append("\n").append(String.join("\n", acceptedAssetCodes));
        }

        message.append("\n\nRejected because already returned under this assignment: ")
                .append(rejectedAlreadyReturned == null ? 0 : rejectedAlreadyReturned.size());
        if (rejectedAlreadyReturned != null && !rejectedAlreadyReturned.isEmpty()) {
            message.append("\n").append(String.join("\n", rejectedAlreadyReturned));
        }

        if (rejectedOther != null && !rejectedOther.isEmpty()) {
            message.append("\n\nRejected for other reasons: ")
                    .append(rejectedOther.size())
                    .append("\n")
                    .append(String.join("\n", rejectedOther));
        }
        return message.toString();
    }

    private void focusOutstandingReasons() {
        updateOutstandingReasonPanel();
        if (txtOutstandingReason == null) {
            return;
        }
        txtOutstandingReason.requestFocus();
    }

    @FXML
    private void enterOutstandingReason(ActionEvent event) {
        Map<String, String> outstandingReasons = collectOutstandingReasons();
        if (outstandingReasons == null) {
            return;
        }
        if (stagedReturnItems.isEmpty()) {
            focusOutstandingReasons();
            return;
        }
        saveReturns(false);
    }

    private void addConditionDropdown(Sheet sheet) {
        DataValidationHelper validationHelper = sheet.getDataValidationHelper();
        DataValidationConstraint conditionConstraint = validationHelper.createExplicitListConstraint(
                getReturnConditionDisplayLabels().toArray(new String[0])
        );
        CellRangeAddressList conditionCells = new CellRangeAddressList(
                BULK_DATA_START_ROW_INDEX,
                1000,
                4,
                4
        );
        DataValidation validation = validationHelper.createValidation(conditionConstraint, conditionCells);
        validation.setShowErrorBox(true);
        validation.createErrorBox(
                "Invalid Condition",
                "Choose one of the listed return condition categories."
        );
        sheet.addValidationData(validation);
    }

    private void writeConditionGuide(Sheet conditionSheet, CellStyle headerStyle) {
        Row titleRow = conditionSheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Return condition categories");
        titleCell.setCellStyle(headerStyle);

        int rowIndex = 1;
        for (String label : getReturnConditionDisplayLabels()) {
            Row row = conditionSheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(label);
        }

        conditionSheet.autoSizeColumn(0);
    }

    private StagedReturnItem buildDraft(String identifier, String returnedBy, String phone, String nid,
                                        String condition, String remarks)
            throws Exception {
        return buildDraft(identifier, returnedBy, phone, nid, condition, remarks, stagedResolvedAssetCodes, getStagedEnteredIdentifiers());
    }

    private StagedReturnItem buildDraft(
            String identifier,
            String returnedBy,
            String phone,
            String nid,
            String condition,
            String remarks,
            Set<String> reservedAssets,
            Set<String> reservedIdentifiers
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
        String normalizedCondition = normalizeReturnCondition(condition);
        if (normalizedCondition.isBlank()) {
            throw new Exception("Please select a condition.");
        }
        if (!ALLOWED_RETURN_CONDITIONS.contains(normalizedCondition)) {
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
            if (selectedAssignment != null
                    && DatabaseHandler.wasAssetReturnedForAssignment(selectedAssignment.getId(), trimmedIdentifier)) {
                throw new ReturnEntryRejectedException(
                        RejectionReason.ALREADY_RETURNED_UNDER_ASSIGNMENT,
                        "Rejected because this asset code was already returned under this assignment: " + trimmedIdentifier
                );
            }
            originalAssetCode = getNextOutstandingAssetCode(reservedAssets);
            if (originalAssetCode == null) {
                throw new Exception("There are no remaining outstanding assets available for replacement.");
            }
        }

        if (reservedAssets.contains(originalAssetCode)) {
            throw new Exception("This asset has already been entered in the current save batch.");
        }
        if (!directOutstandingReturn) {
            Distribution currentDistribution = distributionService.getCurrentDistributionForAsset(trimmedIdentifier);
            if (currentDistribution != null) {
                throw new Exception(
                        "This asset is currently assigned under another record and cannot be used as a replacement: " +
                                trimmedIdentifier
                );
            }
            if (containsIgnoreCase(reservedIdentifiers, trimmedIdentifier)) {
                throw new Exception("This IMEI/serial has already been entered in the current save batch: " + trimmedIdentifier);
            }
            if (DatabaseHandler.equipmentIdentifierExists(trimmedIdentifier)) {
                throw new Exception(
                        "This asset code or IMEI/serial already exists or was already returned. " +
                                "Only assets listed for this assignment can be returned: " + trimmedIdentifier
                );
            }
        }

        String normalizedReturnedBy = returnedBy.trim();

        return new StagedReturnItem(
                originalAssetCode,
                trimmedIdentifier,
                normalizedReturnedBy,
                phone,
                nid,
                normalizedCondition,
                remarks,
                !directOutstandingReturn
        );
    }

    private String normalizeReturnCondition(String condition) {
        if (condition == null || condition.isBlank()) {
            return "";
        }

        String trimmedCondition = condition.trim();
        String normalizedText = trimmedCondition.toLowerCase();
        if (normalizedText.equals("return to the owner")
                || normalizedText.equals("returned to the owner")
                || normalizedText.equals("return to owner")
                || normalizedText.equals("returned to owner")) {
            return "Returned";
        }

        for (Map.Entry<String, String> entry : RETURN_CONDITION_LABELS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(trimmedCondition)
                    || entry.getValue().equalsIgnoreCase(trimmedCondition)) {
                return entry.getKey();
            }
        }
        return trimmedCondition;
    }

    private List<String> getReturnConditionDisplayLabels() {
        List<String> labels = new ArrayList<>();
        for (String condition : RETURN_CONDITION_ORDER) {
            labels.add(RETURN_CONDITION_LABELS.get(condition));
        }
        return labels;
    }

    private String getNextOutstandingAssetCode(Set<String> reservedAssets) {
        for (String assetCode : outstandingAssetCodes) {
            if (!reservedAssets.contains(assetCode)) {
                return assetCode;
            }
        }
        return null;
    }

    private Set<String> getStagedEnteredIdentifiers() {
        Set<String> identifiers = new LinkedHashSet<>();
        for (StagedReturnItem item : stagedReturnItems) {
            identifiers.add(item.enteredIdentifier);
        }
        return identifiers;
    }

    private boolean containsIgnoreCase(Set<String> values, String target) {
        if (values == null || target == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.trim().equalsIgnoreCase(target.trim())) {
                return true;
            }
        }
        return false;
    }

    private void refreshOutstandingReasonRows() {
        updateOutstandingReasonPanel();
        updateSaveState();
    }

    private void updateOutstandingReasonPanel() {
        List<String> remainingAssets = getRemainingAssets();
        boolean hasRemainingAssets = !remainingAssets.isEmpty() && !stagedReturnItems.isEmpty();

        if (outstandingReasonPane != null) {
            outstandingReasonPane.setVisible(hasRemainingAssets);
            outstandingReasonPane.setManaged(hasRemainingAssets);
        }
        outstandingAssetRows.setAll(buildOutstandingAssetRows(remainingAssets));
        if (!hasRemainingAssets && txtOutstandingReason != null) {
            txtOutstandingReason.clear();
        }
    }

    private List<OutstandingAssetRow> buildOutstandingAssetRows(List<String> assetCodes) {
        List<OutstandingAssetRow> rows = new ArrayList<>();
        for (String assetCode : assetCodes) {
            Distribution distribution = distributionService.getCurrentDistributionForAsset(assetCode);
            if (distribution == null) {
                rows.add(new OutstandingAssetRow(assetCode, "", "", ""));
                continue;
            }
            rows.add(new OutstandingAssetRow(
                    assetCode,
                    distribution.getAssignedTo(),
                    distribution.getPhone(),
                    distribution.getNid()
            ));
        }
        return rows;
    }

    private Map<String, String> collectOutstandingReasons() {
        Map<String, String> reasons = new LinkedHashMap<>();
        List<String> remainingAssets = getRemainingAssets();
        if (remainingAssets.isEmpty()) {
            return reasons;
        }

        String reason = txtOutstandingReason == null || txtOutstandingReason.getText() == null
                ? ""
                : txtOutstandingReason.getText().trim();
        if (reason.isEmpty()) {
            showWarning("Enter the outstanding equipment reason before saving.");
            focusOutstandingReasons();
            return null;
        }

        for (String assetCode : remainingAssets) {
            reasons.put(assetCode, reason);
        }
        return reasons;
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
        boolean assignmentSelected = selectedAssignment != null;
        if (btnSaveReturns != null) {
            btnSaveReturns.setDisable(!assignmentSelected || stagedReturnItems.isEmpty());
        }
        if (btnAddReturn != null) {
            btnAddReturn.setDisable(!assignmentSelected || requiredReturnQty == 0 || stagedReturnItems.size() >= requiredReturnQty);
        }
        if (btnClear != null) {
            btnClear.setDisable(!assignmentSelected && stagedReturnItems.isEmpty());
        }
        if (btnDownloadTemplate != null) {
            btnDownloadTemplate.setDisable(!assignmentSelected);
        }
        if (btnChooseFile != null) {
            btnChooseFile.setDisable(!assignmentSelected);
        }
        if (btnUpload != null) {
            btnUpload.setDisable(!assignmentSelected || selectedFile == null);
        }
        if (lblReturnProgress != null) {
            lblReturnProgress.setText("Entered Returns: " + stagedReturnItems.size() + " / " + requiredReturnQty);
        }
    }

    private void setAssignmentDependentState(boolean enabled) {
        if (manualEntryPane != null) {
            manualEntryPane.setDisable(!enabled);
        }
        if (bulkImportPane != null) {
            bulkImportPane.setDisable(!enabled);
        }
        if (!enabled) {
            selectedFile = null;
            if (lblFileName != null) {
                lblFileName.setText("No file selected");
            }
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
        setAssignmentDependentState(false);
        clearEntryFields();
        if (txtOutstandingReason != null) {
            txtOutstandingReason.clear();
        }
        refreshOutstandingReasonRows();
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
                && (BULK_TEMPLATE_HEADERS[4].equalsIgnoreCase(condition) || "condition".equalsIgnoreCase(condition))
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

    private enum RejectionReason {
        ALREADY_RETURNED_UNDER_ASSIGNMENT,
        OTHER
    }

    private static final class ReturnEntryRejectedException extends Exception {
        private final RejectionReason reason;

        private ReturnEntryRejectedException(RejectionReason reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    public static final class OutstandingAssetRow {
        private final SimpleStringProperty assetCode;
        private final SimpleStringProperty assignedTo;
        private final SimpleStringProperty phone;
        private final SimpleStringProperty nid;

        private OutstandingAssetRow(String assetCode, String assignedTo, String phone, String nid) {
            this.assetCode = new SimpleStringProperty(assetCode == null ? "" : assetCode);
            this.assignedTo = new SimpleStringProperty(assignedTo == null ? "" : assignedTo);
            this.phone = new SimpleStringProperty(phone == null ? "" : phone);
            this.nid = new SimpleStringProperty(nid == null ? "" : nid);
        }

        public SimpleStringProperty assetCodeProperty() {
            return assetCode;
        }

        public SimpleStringProperty assignedToProperty() {
            return assignedTo;
        }

        public SimpleStringProperty phoneProperty() {
            return phone;
        }

        public SimpleStringProperty nidProperty() {
            return nid;
        }
    }

}
