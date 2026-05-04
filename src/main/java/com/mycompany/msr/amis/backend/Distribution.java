package com.mycompany.msr.amis;

import javafx.beans.property.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Distribution {

    // ================= PROPERTIES =================
    private final IntegerProperty id;
    private final StringProperty assetCode;
    private final StringProperty serialNumber;
    private final StringProperty assignedTo;
    private final StringProperty responsiblePerson;
    private final StringProperty phone;
    private final StringProperty nid;
    private final ObjectProperty<LocalDate> distributionDate;
    private final StringProperty outstandingRemarks;

    // ✅ FIXED: changed from StringProperty → IntegerProperty
    private final IntegerProperty assignmentId;

    private final StringProperty status;

    // ================= DATE FORMAT =================
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ================= DEFAULT =================
    public Distribution() {
        this(0, "", "", "", "", "", LocalDate.now());
    }

    // ================= MAIN (FULL) =================
    public Distribution(
            int id,
            String assetCode,
            String serialNumber,
            String assignedTo,
            String phone,
            String nid,
            LocalDate distributionDate
    ) {
        this.id = new SimpleIntegerProperty(id);
        this.assetCode = new SimpleStringProperty(safe(assetCode));
        this.serialNumber = new SimpleStringProperty(safe(serialNumber));
        this.assignedTo = new SimpleStringProperty(safe(assignedTo));
        this.responsiblePerson = new SimpleStringProperty("");
        this.phone = new SimpleStringProperty(safe(phone));
        this.nid = new SimpleStringProperty(safe(nid));
        this.distributionDate = new SimpleObjectProperty<>(
                distributionDate != null ? distributionDate : LocalDate.now()
        );
        this.outstandingRemarks = new SimpleStringProperty("");

        // ✅ FIXED: correct type initialization
        this.assignmentId = new SimpleIntegerProperty(0);
        this.status = new SimpleStringProperty("ACTIVE");
    }

    // ================= SIMPLIFIED CONSTRUCTOR =================
    public Distribution(
            String assetCode,
            String serialNumber,
            String assignedTo,
            String phone,
            String nid
    ) {
        this(0, assetCode, serialNumber, assignedTo, phone, nid, LocalDate.now());
    }

    // ================= NULL SAFETY =================
    private String safe(String value) {
        return value == null ? "" : value;
    }

    // ================= GETTERS =================
    public int getId() { return id.get(); }

    public String getAssetCode() { return assetCode.get(); }

    public String getSerialNumber() { return serialNumber.get(); }

    public String getAssignedTo() { return assignedTo.get(); }

    public String getResponsiblePerson() { return responsiblePerson.get(); }

    public String getPhone() { return phone.get(); }

    public String getNid() { return nid.get(); }

    public LocalDate getDistributionDate() { return distributionDate.get(); }

    // ✅ FIXED: now returns int
    public int getAssignmentId() { return assignmentId.get(); }

    public String getStatus() { return status.get(); }
    public String getOutstandingRemarks() { return outstandingRemarks.get(); }

    // controller expects this name
    public String getDate() { return getFormattedDate(); }

    // ================= FORMATTED DATE =================
    public String getFormattedDate() {
        return getDistributionDate().format(FORMATTER);
    }

    // ================= SETTERS =================
    public void setId(int value) { id.set(value); }

    public void setAssetCode(String value) { assetCode.set(safe(value)); }

    public void setSerialNumber(String value) { serialNumber.set(safe(value)); }

    public void setAssignedTo(String value) { assignedTo.set(safe(value)); }

    public void setResponsiblePerson(String value) { responsiblePerson.set(safe(value)); }

    public void setPhone(String value) { phone.set(safe(value)); }

    public void setNid(String value) { nid.set(safe(value)); }

    public void setDistributionDate(LocalDate value) {
        distributionDate.set(value != null ? value : LocalDate.now());
    }

    // ✅ FIXED: now accepts int
    public void setAssignmentId(int value) { assignmentId.set(value); }

    public void setStatus(String value) { status.set(safe(value)); }
    public void setOutstandingRemarks(String value) { outstandingRemarks.set(safe(value)); }

    // ================= PROPERTIES =================
    public IntegerProperty idProperty() { return id; }

    public StringProperty assetCodeProperty() { return assetCode; }

    public StringProperty serialNumberProperty() { return serialNumber; }

    public StringProperty assignedToProperty() { return assignedTo; }

    public StringProperty responsiblePersonProperty() { return responsiblePerson; }

    public StringProperty phoneProperty() { return phone; }

    public StringProperty nidProperty() { return nid; }

    public ObjectProperty<LocalDate> distributionDateProperty() { return distributionDate; }

    // ✅ FIXED: now IntegerProperty
    public IntegerProperty assignmentIdProperty() { return assignmentId; }

    public StringProperty statusProperty() { return status; }
    public StringProperty outstandingRemarksProperty() { return outstandingRemarks; }

    // ================= DEBUG =================
    @Override
    public String toString() {
        return "Distribution{" +
                "id=" + getId() +
                ", assetCode='" + getAssetCode() + '\'' +
                ", serialNumber='" + getSerialNumber() + '\'' +
                ", assignedTo='" + getAssignedTo() + '\'' +
                ", responsiblePerson='" + getResponsiblePerson() + '\'' +
                ", phone='" + getPhone() + '\'' +
                ", nid='" + getNid() + '\'' +
                ", assignmentId=" + getAssignmentId() +
                ", status='" + getStatus() + '\'' +
                ", outstandingRemarks='" + getOutstandingRemarks() + '\'' +
                ", date=" + getFormattedDate() +
                '}';
    }
}
