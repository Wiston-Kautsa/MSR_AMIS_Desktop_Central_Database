package com.mycompany.msr.amis;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ReturnRecord {

    private final StringProperty assetCode;
    private final StringProperty serialNumber;
    private final StringProperty equipmentName;
    private final StringProperty category;
    private final StringProperty source;
    private final StringProperty dateTaken;
    private final StringProperty responsibleOfficer;
    private final StringProperty assignmentEquipmentType;
    private final StringProperty assignmentReason;
    private final StringProperty returnedBy;
    private final StringProperty phone;
    private final StringProperty nid;
    private final StringProperty returnCondition;
    private final StringProperty remarks;
    private final StringProperty returnDate;

    public ReturnRecord(
            String assetCode,
            String serialNumber,
            String equipmentName,
            String category,
            String source,
            String dateTaken,
            String responsibleOfficer,
            String assignmentEquipmentType,
            String assignmentReason,
            String returnedBy,
            String phone,
            String nid,
            String returnCondition,
            String remarks,
            String returnDate
    ) {
        this.assetCode = new SimpleStringProperty(safe(assetCode));
        this.serialNumber = new SimpleStringProperty(safe(serialNumber));
        this.equipmentName = new SimpleStringProperty(safe(equipmentName));
        this.category = new SimpleStringProperty(safe(category));
        this.source = new SimpleStringProperty(safe(source));
        this.dateTaken = new SimpleStringProperty(safe(dateTaken));
        this.responsibleOfficer = new SimpleStringProperty(safe(responsibleOfficer));
        this.assignmentEquipmentType = new SimpleStringProperty(safe(assignmentEquipmentType));
        this.assignmentReason = new SimpleStringProperty(safe(assignmentReason));
        this.returnedBy = new SimpleStringProperty(safe(returnedBy));
        this.phone = new SimpleStringProperty(safe(phone));
        this.nid = new SimpleStringProperty(safe(nid));
        this.returnCondition = new SimpleStringProperty(safe(returnCondition));
        this.remarks = new SimpleStringProperty(safe(remarks));
        this.returnDate = new SimpleStringProperty(safe(returnDate));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public String getAssetCode() { return assetCode.get(); }
    public String getSerialNumber() { return serialNumber.get(); }
    public String getEquipmentName() { return equipmentName.get(); }
    public String getCategory() { return category.get(); }
    public String getSource() { return source.get(); }
    public String getDateTaken() { return dateTaken.get(); }
    public String getResponsibleOfficer() { return responsibleOfficer.get(); }
    public String getAssignmentEquipmentType() { return assignmentEquipmentType.get(); }
    public String getAssignmentReason() { return assignmentReason.get(); }
    public String getReturnedBy() { return returnedBy.get(); }
    public String getPhone() { return phone.get(); }
    public String getNid() { return nid.get(); }
    public String getReturnCondition() { return returnCondition.get(); }
    public String getRemarks() { return remarks.get(); }
    public String getReturnDate() { return returnDate.get(); }

    public StringProperty assetCodeProperty() { return assetCode; }
    public StringProperty serialNumberProperty() { return serialNumber; }
    public StringProperty equipmentNameProperty() { return equipmentName; }
    public StringProperty categoryProperty() { return category; }
    public StringProperty sourceProperty() { return source; }
    public StringProperty dateTakenProperty() { return dateTaken; }
    public StringProperty responsibleOfficerProperty() { return responsibleOfficer; }
    public StringProperty assignmentEquipmentTypeProperty() { return assignmentEquipmentType; }
    public StringProperty assignmentReasonProperty() { return assignmentReason; }
    public StringProperty returnedByProperty() { return returnedBy; }
    public StringProperty phoneProperty() { return phone; }
    public StringProperty nidProperty() { return nid; }
    public StringProperty returnConditionProperty() { return returnCondition; }
    public StringProperty remarksProperty() { return remarks; }
    public StringProperty returnDateProperty() { return returnDate; }
}
