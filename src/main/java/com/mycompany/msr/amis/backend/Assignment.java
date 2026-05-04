package com.mycompany.msr.amis;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Assignment {

    // ================= PROPERTIES =================
    private final IntegerProperty id;
    private final StringProperty person;
    private final StringProperty department;
    private final StringProperty equipmentType;
    private final StringProperty reason;
    private final IntegerProperty quantity;
    private final StringProperty date;

    // ================= REPORT-ONLY EXTRA FIELDS =================
    // These are only populated when using the report constructor below
    private final StringProperty officerName;
    private final StringProperty phone;
    private final StringProperty nid;
    private final StringProperty category;
    private final StringProperty status;
    private final IntegerProperty distributedCount;

    // ================= MAIN CONSTRUCTOR (DB USE) =================
    public Assignment(int id, String person, String department,
                      String equipmentType, String reason, int quantity, String date) {

        this.id = new SimpleIntegerProperty(id);
        this.person = new SimpleStringProperty(safe(person));
        this.department = new SimpleStringProperty(safe(department));
        this.equipmentType = new SimpleStringProperty(safe(equipmentType));
        this.reason = new SimpleStringProperty(safe(reason));
        this.quantity = new SimpleIntegerProperty(quantity);
        this.date = new SimpleStringProperty(safe(date));

        // Report fields default to empty for normal assignments
        this.officerName = new SimpleStringProperty("");
        this.phone = new SimpleStringProperty("");
        this.nid = new SimpleStringProperty("");
        this.category = new SimpleStringProperty("");
        this.status = new SimpleStringProperty("PENDING");
        this.distributedCount = new SimpleIntegerProperty(0);
    }

    // ================= REPORT CONSTRUCTOR =================
    // Used by AssignmentReportController to hold joined/report data
    public Assignment(String assignmentName, String officerName,
                      String phone, String nid,
                      String equipmentName, String category,
                      String status, String date) {

        this.id = new SimpleIntegerProperty(0);
        this.person = new SimpleStringProperty(safe(assignmentName));
        this.department = new SimpleStringProperty("");
        this.equipmentType = new SimpleStringProperty(safe(equipmentName));
        this.reason = new SimpleStringProperty("");
        this.quantity = new SimpleIntegerProperty(1);
        this.date = new SimpleStringProperty(safe(date));

        this.officerName = new SimpleStringProperty(safe(officerName));
        this.phone = new SimpleStringProperty(safe(phone));
        this.nid = new SimpleStringProperty(safe(nid));
        this.category = new SimpleStringProperty(safe(category));
        this.status = new SimpleStringProperty(safe(status));
        this.distributedCount = new SimpleIntegerProperty(0);
    }

    // ================= NULL SAFETY =================
    private String safe(String v) { return v == null ? "" : v; }

    // ================= STANDARD GETTERS =================
    public int getId()             { return id.get(); }
    public String getPerson()      { return person.get(); }
    public String getDepartment()  { return department.get(); }
    public String getEquipmentType() { return equipmentType.get(); }
    public String getReason()      { return reason.get(); }
    public int getQuantity()       { return quantity.get(); }
    public String getDate()        { return date.get(); }

    // ================= REPORT GETTERS =================
    public String getAssignmentName() { return person.get(); }
    public String getOfficerName()    { return officerName.get(); }
    public String getPhone()          { return phone.get(); }
    public String getNid()            { return nid.get(); }
    public String getEquipmentName()  { return equipmentType.get(); }
    public String getCategory()       { return category.get(); }
    public String getStatus()         { return status.get(); }
    public int getDistributedCount()  { return distributedCount.get(); }

    // ================= SETTERS =================
    public void setId(int id)                    { this.id.set(id); }
    public void setPerson(String v)              { this.person.set(safe(v)); }
    public void setDepartment(String v)          { this.department.set(safe(v)); }
    public void setEquipmentType(String v)       { this.equipmentType.set(safe(v)); }
    public void setReason(String v)              { this.reason.set(safe(v)); }
    public void setQuantity(int v)               { this.quantity.set(v); }
    public void setDate(String v)                { this.date.set(safe(v)); }
    public void setStatus(String value)          { this.status.set(safe(value)); }
    public void setDistributedCount(int value)   { this.distributedCount.set(value); }

    // ================= PROPERTY METHODS =================
    public IntegerProperty idProperty()          { return id; }
    public StringProperty personProperty()       { return person; }
    public StringProperty departmentProperty()   { return department; }
    public StringProperty equipmentTypeProperty(){ return equipmentType; }
    public StringProperty reasonProperty()       { return reason; }
    public IntegerProperty quantityProperty()    { return quantity; }
    public StringProperty dateProperty()         { return date; }
    public StringProperty statusProperty()       { return status; }
    public IntegerProperty distributedCountProperty() { return distributedCount; }

    // ================= DEBUG =================
    @Override
    public String toString() {
        return "Assignment{" +
                "id=" + getId() +
                ", person='" + getPerson() + '\'' +
                ", department='" + getDepartment() + '\'' +
                ", equipmentType='" + getEquipmentType() + '\'' +
                ", reason='" + getReason() + '\'' +
                ", quantity=" + getQuantity() +
                ", date='" + getDate() + '\'' +
                '}';
    }
}
