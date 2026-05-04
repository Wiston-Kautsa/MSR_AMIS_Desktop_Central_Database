package com.mycompany.msr.amis;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class User {

    private final IntegerProperty id;
    private final StringProperty fullName;
    private final StringProperty username;
    private final StringProperty password;
    private final StringProperty role;
    private final StringProperty department;
    private final StringProperty phone;
    private final StringProperty email;
    private final StringProperty status;

    public User(int id, String fullName, String username,
                String password, String role, String department,
                String phone, String email, String status) {

        this.id = new SimpleIntegerProperty(id);
        this.fullName = new SimpleStringProperty(fullName);
        this.username = new SimpleStringProperty(username);
        this.password = new SimpleStringProperty(password);
        this.role = new SimpleStringProperty(role);
        this.department = new SimpleStringProperty(department);
        this.phone = new SimpleStringProperty(phone);
        this.email = new SimpleStringProperty(email);
        this.status = new SimpleStringProperty(status);
    }

    public int getId() {
        return id.get();
    }

    public String getFullName() {
        return fullName.get();
    }

    public String getUsername() {
        return username.get();
    }

    public String getPassword() {
        return password.get();
    }

    public String getRole() {
        return role.get();
    }

    public String getDepartment() {
        return department.get();
    }

    public String getPhone() {
        return phone.get();
    }

    public String getEmail() {
        return email.get();
    }

    public String getStatus() {
        return status.get();
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public StringProperty fullNameProperty() {
        return fullName;
    }

    public StringProperty usernameProperty() {
        return username;
    }

    public StringProperty passwordProperty() {
        return password;
    }

    public StringProperty roleProperty() {
        return role;
    }

    public StringProperty departmentProperty() {
        return department;
    }

    public StringProperty phoneProperty() {
        return phone;
    }

    public StringProperty emailProperty() {
        return email;
    }

    public StringProperty statusProperty() {
        return status;
    }

    public void setFullName(String value) {
        fullName.set(value);
    }

    public void setUsername(String value) {
        username.set(value);
    }

    public void setPassword(String value) {
        password.set(value);
    }

    public void setRole(String value) {
        role.set(value);
    }

    public void setDepartment(String value) {
        department.set(value);
    }

    public void setPhone(String value) {
        phone.set(value);
    }

    public void setEmail(String value) {
        email.set(value);
    }

    public void setStatus(String value) {
        status.set(value);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + getId() +
                ", fullName='" + getFullName() + '\'' +
                ", username='" + getUsername() + '\'' +
                ", role='" + getRole() + '\'' +
                ", department='" + getDepartment() + '\'' +
                ", email='" + getEmail() + '\'' +
                ", status='" + getStatus() + '\'' +
                '}';
    }
}
