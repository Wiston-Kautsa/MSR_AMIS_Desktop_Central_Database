package com.mycompany.msr.amis;

import javafx.collections.*;
import javafx.event.ActionEvent;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

public class AddEquipmentController implements Initializable {
    private static final int BULK_HEADER_ROW_INDEX = 0;
    private static final int BULK_DATA_START_ROW_INDEX = BULK_HEADER_ROW_INDEX + 1;
    private static final int BULK_PURCHASE_COST_COLUMN_INDEX = 6;
    private static final int BULK_FORMATTED_DATA_ROWS = 1000;
    private static final String[] BULK_TEMPLATE_HEADERS = {
            "name",
            "category",
            "imei_serial_number",
            "source",
            "condition",
            "entry_date",
            "purchase_cost",
            "location",
            "warranty_expiry",
            "supplier"
    };
    private static final String[] BULK_TEMPLATE_SAMPLE = {
            "Tablet",
            "Tablet",
            "SN-001",
            "World Bank",
            "New",
            LocalDate.now().toString(),
            "MWK 150,000.00",
            "Finance Office",
            "2027-12-31",
            "ABC Suppliers"
    };

    // ================= UI =================
    @FXML private TextField txtName;
    @FXML private ComboBox<String> cmbCategory;
    @FXML private TextField txtSerialNumber;
    @FXML private TextField txtSource;
    @FXML private ComboBox<String> cmbCondition;
    @FXML private DatePicker dateEntry;
    @FXML private TextField txtPurchaseCost;
    @FXML private TextField txtLocation;
    @FXML private DatePicker dateWarrantyExpiry;
    @FXML private TextField txtSupplier;

    @FXML private Label lblSelectedFile;
    @FXML private Label lblBulkUploadProgress;
    @FXML private ProgressBar progressBulkUpload;
    @FXML private Button btnDownloadTemplate;
    @FXML private Button btnChooseExcelFile;
    @FXML private Button btnUploadExcel;

    @FXML private TableView<Equipment> equipmentTable;
    @FXML private TableColumn<Equipment, Void> colNo;
    @FXML private TableColumn<Equipment, String> colName;
    @FXML private TableColumn<Equipment, String> colCategory;
    @FXML private TableColumn<Equipment, String> colSerial;
    @FXML private TableColumn<Equipment, String> colCondition;
    @FXML private TableColumn<Equipment, String> colSource;
    @FXML private TableColumn<Equipment, String> colPurchaseCost;
    @FXML private TableColumn<Equipment, String> colLocation;
    @FXML private TableColumn<Equipment, String> colWarrantyExpiry;
    @FXML private TableColumn<Equipment, String> colSupplier;
    @FXML private TableColumn<Equipment, String> colDate;

    // ================= DATA =================
    private final EquipmentService equipmentService = ServiceRegistry.getEquipmentService();
    private ObservableList<Equipment> equipmentList = FXCollections.observableArrayList();
    private File selectedFile;

    // ================= INIT =================
    @Override
    public void initialize(URL url, ResourceBundle rb) {

        setupTable();

        cmbCategory.setEditable(false);
        cmbCondition.setEditable(false);
        cmbCategory.getItems().addAll("Desktop", "Laptop", "Monitor", "Printer", "Tablet", "Phone", "Network", "Furniture", "Other");
        cmbCondition.getItems().addAll("New", "Good", "Fair", "Poor", "Damaged", "Faulty");

        dateEntry.setValue(LocalDate.now());
        txtPurchaseCost.setPromptText("Example: MWK 150,000.00");
        CurrencyFormatHelper.installCurrencyFormatter(txtPurchaseCost);

        equipmentTable.setItems(equipmentList);
        loadEquipmentAndCategoriesAsync();
        resetBulkUploadProgress();
    }

    // ================= TABLE =================
    private void setupTable() {

        TableNumbering.install(colNo);
        colName.setCellValueFactory(cell -> cell.getValue().nameProperty());
        colCategory.setCellValueFactory(cell -> cell.getValue().categoryProperty());
        colSerial.setCellValueFactory(cell -> cell.getValue().serialNumberProperty());
        colCondition.setCellValueFactory(cell -> cell.getValue().conditionProperty());
        colSource.setCellValueFactory(cell -> cell.getValue().sourceProperty());
        colPurchaseCost.setCellValueFactory(cell -> cell.getValue().purchaseCostProperty());
        CurrencyFormatHelper.installCurrencyCellFactory(colPurchaseCost);
        colLocation.setCellValueFactory(cell -> cell.getValue().locationProperty());
        colWarrantyExpiry.setCellValueFactory(cell -> cell.getValue().warrantyExpiryProperty());
        colSupplier.setCellValueFactory(cell -> cell.getValue().supplierProperty());
        colDate.setCellValueFactory(cell -> cell.getValue().entryDateProperty());

        equipmentTable.setItems(equipmentList);
    }

    // ================= LOAD =================
    private void loadEquipmentFromDatabase() {
        equipmentList.clear();
        equipmentList.addAll(equipmentService.getAllEquipment());
    }

    private void loadEquipmentAndCategoriesAsync() {
        equipmentTable.setDisable(true);
        UiBackgroundLoader.run(
                "add-equipment-loader",
                () -> new EquipmentLoadResult(
                        javafx.collections.FXCollections.observableArrayList(equipmentService.getAllEquipment()),
                        new java.util.LinkedHashSet<>(equipmentService.getEquipmentCategories())
                ),
                result -> {
                    equipmentList.setAll(result.equipment());
                    LinkedHashSet<String> categories = new LinkedHashSet<>(cmbCategory.getItems());
                    categories.addAll(result.categories());
                    cmbCategory.getItems().setAll(categories);
                    equipmentTable.setItems(equipmentList);
                    equipmentTable.setDisable(false);
                },
                error -> {
                    equipmentTable.setDisable(false);
                    showError("Failed to load equipment data: " + safeMessage(error));
                }
        );
    }

    private void loadCategories() {
        LinkedHashSet<String> categories = new LinkedHashSet<>(cmbCategory.getItems());
        categories.addAll(equipmentService.getEquipmentCategories());
        cmbCategory.getItems().setAll(categories);
    }

    private String safeMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? "Unexpected error."
                : throwable.getMessage();
    }

    // ================= SAVE =================
    @FXML
    private void saveEquipment(ActionEvent event) {

        if (txtName.getText().isEmpty()
                || cmbCategory.getValue() == null
                || txtSerialNumber.getText().isEmpty()
                || cmbCondition.getValue() == null
                || dateEntry.getValue() == null) {

            showWarning("Please fill all required fields");
            return;
        }

        Equipment eq = new Equipment(
                txtName.getText(),
                cmbCategory.getValue(),
                txtSerialNumber.getText(),
                txtSource.getText(),
                cmbCondition.getValue(),
                dateEntry.getValue().toString(),
                CurrencyFormatHelper.formatLocalCurrency(txtPurchaseCost.getText()),
                txtLocation.getText(),
                dateWarrantyExpiry.getValue() == null ? "" : dateWarrantyExpiry.getValue().toString(),
                txtSupplier.getText()
        );

        try {
            equipmentService.createEquipment(eq);
            loadEquipmentFromDatabase();
            loadCategories();
            showInfo("Equipment saved successfully");
            clearForm(null);
        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to save equipment: " + e.getMessage());
        }
    }

    // ================= CLEAR =================
    @FXML
    private void clearForm(ActionEvent event) {

        txtName.clear();
        txtSerialNumber.clear();
        txtSource.clear();
        txtPurchaseCost.clear();
        txtLocation.clear();
        txtSupplier.clear();

        cmbCategory.getSelectionModel().clearSelection();
        cmbCondition.getSelectionModel().clearSelection();

        dateEntry.setValue(LocalDate.now());
        dateWarrantyExpiry.setValue(null);
    }

    // ================= FILE =================
    @FXML
    private void chooseExcelFile(ActionEvent event) {

        FileChooser chooser = new FileChooser();
        FileLocationHelper.useDownloadsDirectory(chooser);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        selectedFile = chooser.showOpenDialog(null);

        if (selectedFile != null) {
            lblSelectedFile.setText(selectedFile.getName());
            resetBulkUploadProgress();
            OperationFeedbackHelper.showInfo(
                    "File Selected",
                    "Ready to upload:\n" + selectedFile.getName() +
                            "\n\nClick Upload to import the equipment records."
            );
        } else {
            OperationFeedbackHelper.showWarning(
                    "No File Selected",
                    "No Excel file was selected."
            );
        }
    }

    @FXML
    private void downloadTemplate(ActionEvent event) {

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Bulk Enrolment Template");
        chooser.setInitialFileName("equipment_bulk_template.xlsx");
        FileLocationHelper.useDownloadsDirectory(chooser);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File targetFile = chooser.showSaveDialog(null);

        if (targetFile == null) {
            OperationFeedbackHelper.showWarning(
                    "Download Cancelled",
                    "Template download was cancelled."
            );
            return;
        }

        OperationFeedbackHelper.showInfo(
                "Preparing Template",
                "Creating the bulk equipment template in Downloads."
        );

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Equipment Template");

            Row headerRow = sheet.createRow(BULK_HEADER_ROW_INDEX);

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            for (int i = 0; i < BULK_TEMPLATE_HEADERS.length; i++) {
                Cell headerCell = headerRow.createCell(i);
                headerCell.setCellValue(BULK_TEMPLATE_HEADERS[i]);
                headerCell.setCellStyle(headerStyle);
            }

            Row sampleRow = sheet.createRow(BULK_DATA_START_ROW_INDEX);
            CellStyle purchaseCostStyle = wb.createCellStyle();
            purchaseCostStyle.setDataFormat(wb.createDataFormat().getFormat("\"MWK\" #,##0.00"));

            for (int i = 0; i < BULK_TEMPLATE_SAMPLE.length; i++) {
                Cell sampleCell = sampleRow.createCell(i);
                if (i == BULK_PURCHASE_COST_COLUMN_INDEX) {
                    sampleCell.setCellValue(150000);
                    sampleCell.setCellStyle(purchaseCostStyle);
                } else {
                    sampleCell.setCellValue(BULK_TEMPLATE_SAMPLE[i]);
                }
            }
            sheet.setDefaultColumnStyle(BULK_PURCHASE_COST_COLUMN_INDEX, purchaseCostStyle);
            for (int rowIndex = BULK_DATA_START_ROW_INDEX + 1; rowIndex <= BULK_FORMATTED_DATA_ROWS; rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                row.createCell(BULK_PURCHASE_COST_COLUMN_INDEX).setCellStyle(purchaseCostStyle);
            }

            for (int i = 0; i < BULK_TEMPLATE_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.setColumnWidth(BULK_PURCHASE_COST_COLUMN_INDEX, Math.max(sheet.getColumnWidth(BULK_PURCHASE_COST_COLUMN_INDEX), 16 * 256));

            try (FileOutputStream out = new FileOutputStream(targetFile)) {
                wb.write(out);
            }

            OperationFeedbackHelper.showInfo(
                    "Template Ready",
                    "Template downloaded successfully to:\n" + targetFile.getAbsolutePath()
            );

        } catch (Exception e) {
            e.printStackTrace();
            OperationFeedbackHelper.showError(
                    "Download Failed",
                    "Failed to download the template."
            );
        }
    }

    // ================= UPLOAD =================
    @FXML
    private void uploadExcel() {

        if (selectedFile == null) {
            OperationFeedbackHelper.showWarning(
                    "Upload Blocked",
                    "Select an Excel file first before uploading."
            );
            return;
        }

        try (Workbook wb = new XSSFWorkbook(new FileInputStream(selectedFile))) {

            Sheet sheet = wb.getSheetAt(0);

            if (sheet.getRow(BULK_HEADER_ROW_INDEX) == null || sheet.getRow(BULK_HEADER_ROW_INDEX).getLastCellNum() < BULK_TEMPLATE_HEADERS.length) {
                OperationFeedbackHelper.showError(
                    "Invalid File",
                    "The Excel file must contain these columns: " + String.join(", ", BULK_TEMPLATE_HEADERS)
                );
                return;
            }

            int inserted = 0;
            int skipped = 0;
            Set<String> fileSerials = new LinkedHashSet<>();
            StringBuilder skippedDetails = new StringBuilder();
            List<BulkEquipmentRow> rowsToImport = new ArrayList<>();

            // Row 1 is reserved for column titles; actual bulk data starts on row 2.
            for (int i = BULK_DATA_START_ROW_INDEX; i <= sheet.getLastRowNum(); i++) {

                Row r = sheet.getRow(i);
                if (r == null) continue;

                String name = getCell(r, 0);
                String category = getCell(r, 1);
                String serial = getCell(r, 2);
                String source = getCell(r, 3);
                String condition = getCell(r, 4);
                String entryDate = getCell(r, 5);
                String purchaseCost = getCell(r, 6);
                String location = getCell(r, 7);
                String warrantyExpiry = getCell(r, 8);
                String supplier = getCell(r, 9);

                // skip empty rows
                if (name.isEmpty() || category.isEmpty() || serial.isEmpty()) continue;
                if (isHeaderLikeRow(name, category, serial, source, condition)) continue;
                if (isSampleRow(name, category, serial, source, condition)) continue;
                if (containsIgnoreCase(fileSerials, serial)) {
                    skipped++;
                    appendSkipped(skippedDetails, i + 1, serial, "Duplicate serial/IMEI inside the uploaded file.");
                    continue;
                }
                fileSerials.add(serial);

                Equipment eq = new Equipment(
                        name,
                        category,
                        serial,
                        source,
                        condition,
                        entryDate.isBlank() ? LocalDate.now().toString() : entryDate,
                        CurrencyFormatHelper.formatLocalCurrency(purchaseCost),
                        location,
                        warrantyExpiry,
                        supplier
                );

                rowsToImport.add(new BulkEquipmentRow(i + 1, serial, eq));
            }

            if (rowsToImport.isEmpty()) {
                resetBulkUploadProgress();
                OperationFeedbackHelper.showWarning(
                        "No Equipment Found",
                        "The selected Excel file does not contain any equipment records to import."
                                + (skippedDetails.length() == 0 ? "" : "\n\nSkipped details:\n" + skippedDetails)
                );
                return;
            }

            runBulkUploadTask(rowsToImport, inserted, skipped, skippedDetails);

        } catch (Exception e) {
            e.printStackTrace();
            resetBulkUploadProgress();
            OperationFeedbackHelper.showError(
                "Upload Failed",
                "Error reading the Excel file.\n\n" + e.getMessage()
            );
        }
    }

    private void runBulkUploadTask(List<BulkEquipmentRow> rowsToImport,
                                   int initialInserted,
                                   int initialSkipped,
                                   StringBuilder initialSkippedDetails) {
        Task<BulkUploadResult> task = new Task<>() {
            @Override
            protected BulkUploadResult call() {
                int inserted = initialInserted;
                int skipped = initialSkipped;
                StringBuilder skippedDetails = new StringBuilder(initialSkippedDetails);
                int total = rowsToImport.size();
                updateProgress(0, total);
                updateMessage("Entering equipment 0 of " + total);

                for (int index = 0; index < total; index++) {
                    BulkEquipmentRow row = rowsToImport.get(index);
                    try {
                        equipmentService.createEquipment(row.equipment);
                        inserted++;
                    } catch (Exception e) {
                        skipped++;
                        appendSkipped(skippedDetails, row.rowNumber, row.serial, e.getMessage());
                    }
                    int processed = index + 1;
                    updateProgress(processed, total);
                    updateMessage("Entering equipment " + processed + " of " + total);
                }
                return new BulkUploadResult(inserted, skipped, skippedDetails.toString(), total);
            }
        };

        setBulkUploadRunning(true);
        progressBulkUpload.progressProperty().bind(task.progressProperty());
        lblBulkUploadProgress.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(event -> {
            unbindBulkUploadProgress();
            BulkUploadResult result = task.getValue();
            progressBulkUpload.setProgress(1.0);
            lblBulkUploadProgress.setText("Completed " + result.total + " equipment record(s).");
            setBulkUploadRunning(false);
            loadEquipmentFromDatabase();
            loadCategories();
            OperationFeedbackHelper.showInfo(
                    "Upload Complete",
                    "Equipment upload completed.\n\nImported records: " + result.inserted +
                            "\nSkipped records: " + result.skipped +
                            (result.skippedDetails.isBlank() ? "" : "\n\nSkipped details:\n" + result.skippedDetails)
            );
        });

        task.setOnFailed(event -> {
            unbindBulkUploadProgress();
            progressBulkUpload.setProgress(0);
            lblBulkUploadProgress.setText("Upload failed");
            setBulkUploadRunning(false);
            Throwable error = task.getException();
            OperationFeedbackHelper.showError(
                    "Upload Failed",
                    "Failed to import equipment records.\n\n" + (error == null ? "" : error.getMessage())
            );
        });

        Thread worker = new Thread(task, "equipment-bulk-upload");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBulkUploadRunning(boolean running) {
        if (btnUploadExcel != null) {
            btnUploadExcel.setDisable(running);
        }
        if (btnChooseExcelFile != null) {
            btnChooseExcelFile.setDisable(running);
        }
        if (btnDownloadTemplate != null) {
            btnDownloadTemplate.setDisable(running);
        }
    }

    private void resetBulkUploadProgress() {
        unbindBulkUploadProgress();
        if (progressBulkUpload != null) {
            progressBulkUpload.setProgress(0);
        }
        if (lblBulkUploadProgress != null) {
            lblBulkUploadProgress.setText("No upload running");
        }
        setBulkUploadRunning(false);
    }

    private void unbindBulkUploadProgress() {
        if (progressBulkUpload != null) {
            progressBulkUpload.progressProperty().unbind();
        }
        if (lblBulkUploadProgress != null) {
            lblBulkUploadProgress.textProperty().unbind();
        }
    }

    // ================= UTIL =================
    private String getCell(Row row, int index) {

        Cell cell = row.getCell(index);
        if (cell == null) return "";

        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }

        if (cell.getCellType() == CellType.NUMERIC) {

            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate().toString();
            }

            double v = cell.getNumericCellValue();
            return (v == (long) v)
                    ? String.valueOf((long) v)
                    : String.valueOf(v);
        }

        if (cell.getCellType() == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }

        if (cell.getCellType() == CellType.FORMULA) {
            return cell.getCellFormula();
        }

        return "";
    }

    private boolean isHeaderLikeRow(String name, String category, String serial, String source, String condition) {
        return BULK_TEMPLATE_HEADERS[0].equalsIgnoreCase(name)
                && BULK_TEMPLATE_HEADERS[1].equalsIgnoreCase(category)
                && BULK_TEMPLATE_HEADERS[2].equalsIgnoreCase(serial)
                && BULK_TEMPLATE_HEADERS[3].equalsIgnoreCase(source)
                && BULK_TEMPLATE_HEADERS[4].equalsIgnoreCase(condition);
    }

    private boolean isSampleRow(String name, String category, String serial, String source, String condition) {
        return BULK_TEMPLATE_SAMPLE[0].equalsIgnoreCase(name)
                && BULK_TEMPLATE_SAMPLE[1].equalsIgnoreCase(category)
                && BULK_TEMPLATE_SAMPLE[2].equalsIgnoreCase(serial)
                && BULK_TEMPLATE_SAMPLE[3].equalsIgnoreCase(source)
                && BULK_TEMPLATE_SAMPLE[4].equalsIgnoreCase(condition);
    }

    private boolean containsIgnoreCase(Set<String> values, String target) {
        for (String value : values) {
            if (value != null && target != null && value.trim().equalsIgnoreCase(target.trim())) {
                return true;
            }
        }
        return false;
    }

    private void appendSkipped(StringBuilder details, int rowNumber, String serial, String reason) {
        if (details.length() > 1200) {
            return;
        }
        details.append("Row ")
                .append(rowNumber)
                .append(" (")
                .append(serial == null || serial.isBlank() ? "no serial" : serial)
                .append("): ")
                .append(reason == null || reason.isBlank() ? "Duplicate or invalid record." : reason)
                .append("\n");
    }

    private static final class BulkEquipmentRow {
        private final int rowNumber;
        private final String serial;
        private final Equipment equipment;

        private BulkEquipmentRow(int rowNumber, String serial, Equipment equipment) {
            this.rowNumber = rowNumber;
            this.serial = serial;
            this.equipment = equipment;
        }
    }

    private static final class EquipmentLoadResult {
        private final ObservableList<Equipment> equipment;
        private final Set<String> categories;

        private EquipmentLoadResult(ObservableList<Equipment> equipment, Set<String> categories) {
            this.equipment = equipment == null ? FXCollections.observableArrayList() : equipment;
            this.categories = categories == null ? Set.of() : categories;
        }

        private ObservableList<Equipment> equipment() {
            return equipment;
        }

        private Set<String> categories() {
            return categories;
        }
    }

    private static final class BulkUploadResult {
        private final int inserted;
        private final int skipped;
        private final String skippedDetails;
        private final int total;

        private BulkUploadResult(int inserted, int skipped, String skippedDetails, int total) {
            this.inserted = inserted;
            this.skipped = skipped;
            this.skippedDetails = skippedDetails == null ? "" : skippedDetails;
            this.total = total;
        }
    }

    // ================= ALERTS =================
    private void showInfo(String msg) {
        OperationFeedbackHelper.showInfo("Equipment", msg);
    }

    private void showWarning(String msg) {
        OperationFeedbackHelper.showWarning("Equipment", msg);
    }

    private void showError(String msg) {
        OperationFeedbackHelper.showError("Equipment", msg);
    }
}
