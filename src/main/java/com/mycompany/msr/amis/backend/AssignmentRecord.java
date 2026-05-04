package com.mycompany.msr.amis;

import javafx.beans.property.*;

public class AssignmentRecord {

    private final IntegerProperty id;
    private final StringProperty person;
    private final StringProperty department;
    private final StringProperty equipmentType;
    private final IntegerProperty quantity;
    private final StringProperty date;
    private final StringProperty status;

    // ================= DEFAULT =================
    public AssignmentRecord() {
        this(0, "", "", "", 0, "", "PENDING");
    }

    // ================= MAIN =================
    public AssignmentRecord(int id,
                            String person,
                            String department,
                            String equipmentType,
                            int quantity,
                            String date) {

        this(id, person, department, equipmentType, quantity, date, "PENDING");
    }

    // ================= FULL =================
    public AssignmentRecord(int id,
                            String person,
                            String department,
                            String equipmentType,
                            int quantity,
                            String date,
                            String status) {

        this.id = new SimpleIntegerProperty(id);
        this.person = new SimpleStringProperty(person != null ? person : "");
        this.department = new SimpleStringProperty(department != null ? department : "");
        this.equipmentType = new SimpleStringProperty(equipmentType != null ? equipmentType : "");
        this.quantity = new SimpleIntegerProperty(quantity);
        this.date = new SimpleStringProperty(date != null ? date : "");
        this.status = new SimpleStringProperty(status != null ? status : "PENDING");
    }

    // ================= GETTERS =================
    public int getId() { return id.get(); }
    public String getPerson() { return person.get(); }
    public String getDepartment() { return department.get(); }
    public String getEquipmentType() { return equipmentType.get(); }
    public int getQuantity() { return quantity.get(); }
    public String getDate() { return date.get(); }
    public String getStatus() { return status.get(); }

    // ================= PROPERTIES =================
    public IntegerProperty idProperty() { return id; }
    public StringProperty personProperty() { return person; }
    public StringProperty departmentProperty() { return department; }
    public StringProperty equipmentTypeProperty() { return equipmentType; }
    public IntegerProperty quantityProperty() { return quantity; }
    public StringProperty dateProperty() { return date; }
    public StringProperty statusProperty() { return status; }

    // ================= SETTERS =================
    public void setPerson(String value) { person.set(value); }
    public void setDepartment(String value) { department.set(value); }
    public void setEquipmentType(String value) { equipmentType.set(value); }
    public void setQuantity(int value) { quantity.set(value); }
    public void setDate(String value) { date.set(value); }
    public void setStatus(String value) { status.set(value); }

    // ================= DEBUG =================
    @Override
    public String toString() {
        return "AssignmentRecord{" +
                "id=" + getId() +
                ", person='" + getPerson() + '\'' +
                ", department='" + getDepartment() + '\'' +
                ", equipmentType='" + getEquipmentType() + '\'' +
                ", quantity=" + getQuantity() +
                ", status='" + getStatus() + '\'' +
                '}';
    }
}