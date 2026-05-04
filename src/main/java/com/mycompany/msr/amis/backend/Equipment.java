package com.mycompany.msr.amis;

import javafx.beans.property.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Equipment {

    // ================= ENUMS =================
    public enum Status {
        AVAILABLE, ASSIGNED, MAINTENANCE, RETIRED
    }

    public enum Condition {
        NEW, GOOD, FAIR, POOR
    }

    // ================= PROPERTIES =================
    private final IntegerProperty id;
    private final StringProperty assetCode;
    private final StringProperty serialNumber;
    private final StringProperty name;
    private final StringProperty category;
    private final StringProperty condition;
    private final StringProperty source;
    private final StringProperty entryDate;
    private final StringProperty status;

    // ================= DATE FORMAT =================
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ================= DEFAULT =================
    public Equipment() {
        this(0, "", "", "", "", "", "", today(), "AVAILABLE");
    }

    // ================= FULL CONSTRUCTOR (DB USE) =================
    public Equipment(int id, String assetCode, String serialNumber,
                     String name, String category,
                     String condition, String source,
                     String entryDate, String status) {

        this.id = new SimpleIntegerProperty(id);
        this.assetCode = new SimpleStringProperty(safe(assetCode));
        this.serialNumber = new SimpleStringProperty(safe(serialNumber));
        this.name = new SimpleStringProperty(safe(name));
        this.category = new SimpleStringProperty(safe(category));
        this.condition = new SimpleStringProperty(safe(condition));
        this.source = new SimpleStringProperty(safe(source));
        this.entryDate = new SimpleStringProperty(safe(entryDate));
        this.status = new SimpleStringProperty(safe(status));
    }

    // ================= SIMPLIFIED CONSTRUCTOR (UI USE) =================
    public Equipment(String name, String category,
                     String serialNumber, String source,
                     String condition, String entryDate) {

        this(0,
             generateAssetCode(),
             serialNumber,
             name,
             category,
             condition,
             source,
             entryDate,
             "AVAILABLE");
    }

    // ================= NULL SAFETY =================
    private String safe(String value) {
        return value == null ? "" : value;
    }

    // ================= AUTO ASSET CODE =================
    private static String generateAssetCode() {
        return "AST-" + System.currentTimeMillis();
    }

    // ================= GETTERS =================
    public int getId() { return id.get(); }

    public String getAssetCode() { return assetCode.get(); }

    public String getSerialNumber() { return serialNumber.get(); }

    public String getName() { return name.get(); }

    public String getCategory() { return category.get(); }

    public String getCondition() { return condition.get(); }

    public String getSource() { return source.get(); }

    public String getEntryDate() { return entryDate.get(); }

    public String getStatus() { return status.get(); }

    // ================= SETTERS =================
    public void setId(int value) { id.set(value); }

    public void setAssetCode(String value) { assetCode.set(safe(value)); }

    public void setSerialNumber(String value) { serialNumber.set(safe(value)); }

    public void setName(String value) { name.set(safe(value)); }

    public void setCategory(String value) { category.set(safe(value)); }

    public void setCondition(String value) { condition.set(safe(value)); }

    public void setSource(String value) { source.set(safe(value)); }

    public void setEntryDate(String value) { entryDate.set(safe(value)); }

    public void setStatus(String value) { status.set(safe(value)); }

    // ================= PROPERTIES =================
    public IntegerProperty idProperty() { return id; }

    public StringProperty assetCodeProperty() { return assetCode; }

    public StringProperty serialNumberProperty() { return serialNumber; }

    public StringProperty nameProperty() { return name; }

    public StringProperty categoryProperty() { return category; }

    public StringProperty conditionProperty() { return condition; }

    public StringProperty sourceProperty() { return source; }

    public StringProperty entryDateProperty() { return entryDate; }

    public StringProperty statusProperty() { return status; }

    // ================= DATE HELPER =================
    public static String today() {
        return LocalDate.now().format(FORMATTER);
    }

    // ================= DEBUG =================
    @Override
    public String toString() {
        return "Equipment{" +
                "id=" + getId() +
                ", assetCode='" + getAssetCode() + '\'' +
                ", serialNumber='" + getSerialNumber() + '\'' +
                ", name='" + getName() + '\'' +
                ", category='" + getCategory() + '\'' +
                ", condition='" + getCondition() + '\'' +
                ", source='" + getSource() + '\'' +
                ", entryDate='" + getEntryDate() + '\'' +
                ", status='" + getStatus() + '\'' +
                '}';
    }
}