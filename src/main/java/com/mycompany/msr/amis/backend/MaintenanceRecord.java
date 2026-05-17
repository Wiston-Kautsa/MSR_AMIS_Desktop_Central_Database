package com.mycompany.msr.amis;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class MaintenanceRecord {
    private final IntegerProperty id;
    private final StringProperty assetCode;
    private final StringProperty issue;
    private final StringProperty actionTaken;
    private final StringProperty performedBy;
    private final StringProperty maintenanceDate;
    private final StringProperty cost;
    private final StringProperty status;

    public MaintenanceRecord(int id, String assetCode, String issue, String actionTaken,
                             String performedBy, String maintenanceDate, String cost, String status) {
        this.id = new SimpleIntegerProperty(id);
        this.assetCode = new SimpleStringProperty(safe(assetCode));
        this.issue = new SimpleStringProperty(safe(issue));
        this.actionTaken = new SimpleStringProperty(safe(actionTaken));
        this.performedBy = new SimpleStringProperty(safe(performedBy));
        this.maintenanceDate = new SimpleStringProperty(safe(maintenanceDate));
        this.cost = new SimpleStringProperty(safe(cost));
        this.status = new SimpleStringProperty(safe(status));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public int getId() { return id.get(); }
    public String getAssetCode() { return assetCode.get(); }
    public String getIssue() { return issue.get(); }
    public String getActionTaken() { return actionTaken.get(); }
    public String getPerformedBy() { return performedBy.get(); }
    public String getMaintenanceDate() { return maintenanceDate.get(); }
    public String getCost() { return cost.get(); }
    public String getStatus() { return status.get(); }

    public IntegerProperty idProperty() { return id; }
    public StringProperty assetCodeProperty() { return assetCode; }
    public StringProperty issueProperty() { return issue; }
    public StringProperty actionTakenProperty() { return actionTaken; }
    public StringProperty performedByProperty() { return performedBy; }
    public StringProperty maintenanceDateProperty() { return maintenanceDate; }
    public StringProperty costProperty() { return cost; }
    public StringProperty statusProperty() { return status; }
}
