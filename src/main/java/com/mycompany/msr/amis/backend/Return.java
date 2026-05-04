package com.mycompany.msr.amis;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Return {

    // ================= PROPERTIES =================
    private final StringProperty assetCode;
    private final StringProperty returnedBy;
    private final StringProperty phone;
    private final StringProperty nid;
    private final StringProperty condition;
    private final StringProperty returnDate;

    // ================= CONSTRUCTOR =================
    public Return(String assetCode,
                  String returnedBy,
                  String phone,
                  String nid,
                  String condition,
                  String returnDate) {

        this.assetCode = new SimpleStringProperty(assetCode);
        this.returnedBy = new SimpleStringProperty(returnedBy);
        this.phone = new SimpleStringProperty(phone);
        this.nid = new SimpleStringProperty(nid);
        this.condition = new SimpleStringProperty(condition);
        this.returnDate = new SimpleStringProperty(returnDate);
    }

    // ================= GETTERS =================
    public String getAssetCode() {
        return assetCode.get();
    }

    public String getReturnedBy() {
        return returnedBy.get();
    }

    public String getPhone() {
        return phone.get();
    }

    public String getNid() {
        return nid.get();
    }

    public String getCondition() {
        return condition.get();
    }

    // IMPORTANT: controller/export expects getDate()
    public String getDate() {
        return returnDate.get();
    }

    // ================= PROPERTY METHODS =================
    public StringProperty assetCodeProperty() {
        return assetCode;
    }

    public StringProperty returnedByProperty() {
        return returnedBy;
    }

    public StringProperty phoneProperty() {
        return phone;
    }

    public StringProperty nidProperty() {
        return nid;
    }

    public StringProperty conditionProperty() {
        return condition;
    }

    public StringProperty returnDateProperty() {
        return returnDate;
    }

    // ================= OPTIONAL SETTERS =================
    public void setAssetCode(String value) {
        assetCode.set(value);
    }

    public void setReturnedBy(String value) {
        returnedBy.set(value);
    }

    public void setPhone(String value) {
        phone.set(value);
    }

    public void setNid(String value) {
        nid.set(value);
    }

    public void setCondition(String value) {
        condition.set(value);
    }

    public void setReturnDate(String value) {
        returnDate.set(value);
    }

    // ================= DEBUG =================
    @Override
    public String toString() {
        return "Return{" +
                "assetCode='" + getAssetCode() + '\'' +
                ", returnedBy='" + getReturnedBy() + '\'' +
                ", phone='" + getPhone() + '\'' +
                ", nid='" + getNid() + '\'' +
                ", condition='" + getCondition() + '\'' +
                ", date='" + getDate() + '\'' +
                '}';
    }
}