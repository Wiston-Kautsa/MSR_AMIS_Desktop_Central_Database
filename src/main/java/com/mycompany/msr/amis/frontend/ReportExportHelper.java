package com.mycompany.msr.amis;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

public final class ReportExportHelper {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private ReportExportHelper() {
    }

    public static <T> void exportCsv(String baseName, String title, List<T> rows, List<Column<T>> columns) {
        if (rows == null || rows.isEmpty()) {
            OperationFeedbackHelper.showInfo("No Data", "There is no data to export.");
            return;
        }

        File file = FileLocationHelper.fileInDownloads(baseName + "_" + STAMP.format(LocalDateTime.now()) + ".csv");
        try (FileWriter writer = new FileWriter(file)) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    writer.append(",");
                }
                writer.append(csvSafe(columns.get(i).header()));
            }
            writer.append("\n");

            for (T row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) {
                        writer.append(",");
                    }
                    writer.append(csvSafe(columns.get(i).value(row)));
                }
                writer.append("\n");
            }
            OperationFeedbackHelper.showInfo("Export Complete", title + " exported to:\n" + file.getAbsolutePath());
        } catch (Exception exception) {
            OperationFeedbackHelper.showError("Export Failed", title + " export failed:\n" + exception.getMessage());
        }
    }

    public static <T> void exportPdf(String baseName, String title, List<T> rows, List<Column<T>> columns) {
        if (rows == null || rows.isEmpty()) {
            OperationFeedbackHelper.showInfo("No Data", "There is no data to export.");
            return;
        }

        File file = FileLocationHelper.fileInDownloads(baseName + "_" + STAMP.format(LocalDateTime.now()) + ".pdf");
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            PdfWriter.getInstance(document, outputStream);
            document.open();
            Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD);
            Font metaFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
            Font headerFont = new Font(Font.HELVETICA, 8, Font.BOLD);
            Font bodyFont = new Font(Font.HELVETICA, 8, Font.NORMAL);

            document.add(new Paragraph(title, titleFont));
            document.add(new Paragraph("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) +
                    " | Rows: " + rows.size(), metaFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(columns.size());
            table.setWidthPercentage(100);
            for (Column<T> column : columns) {
                PdfPCell cell = new PdfPCell(new Phrase(column.header(), headerFont));
                cell.setPadding(4);
                table.addCell(cell);
            }
            for (T row : rows) {
                for (Column<T> column : columns) {
                    PdfPCell cell = new PdfPCell(new Phrase(column.value(row), bodyFont));
                    cell.setPadding(3);
                    table.addCell(cell);
                }
            }
            document.add(table);
            OperationFeedbackHelper.showInfo("Export Complete", title + " PDF exported to:\n" + file.getAbsolutePath());
        } catch (Exception exception) {
            OperationFeedbackHelper.showError("Export Failed", title + " PDF export failed:\n" + exception.getMessage());
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    public static final class Column<T> {
        private final String header;
        private final Function<T, String> extractor;

        public Column(String header, Function<T, String> extractor) {
            this.header = header;
            this.extractor = extractor;
        }

        String header() {
            return header;
        }

        String value(T row) {
            String value = extractor.apply(row);
            return value == null ? "" : value;
        }
    }

    private static String csvSafe(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
