package com.mycompany.msr.amis;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class AuditLog {

    private final SimpleIntegerProperty id;
    private final SimpleStringProperty username;
    private final SimpleStringProperty action;
    private final SimpleStringProperty moduleName;
    private final SimpleStringProperty details;
    private final SimpleStringProperty actionTime;

    public AuditLog(int id, String username, String action, String moduleName, String details, String actionTime) {
        this.id = new SimpleIntegerProperty(id);
        this.username = new SimpleStringProperty(safe(username));
        this.action = new SimpleStringProperty(safe(action));
        this.moduleName = new SimpleStringProperty(safe(moduleName));
        this.details = new SimpleStringProperty(safe(details));
        this.actionTime = new SimpleStringProperty(safe(actionTime));
    }

    public int getId() {
        return id.get();
    }

    public String getUsername() {
        return username.get();
    }

    public String getAction() {
        return action.get();
    }

    public String getModuleName() {
        return moduleName.get();
    }

    public String getDetails() {
        return details.get();
    }

    public String getActionTime() {
        return actionTime.get();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
