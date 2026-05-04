package com.mycompany.msr.amis;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class AssetHistoryRecord {

    private final StringProperty activityDate;
    private final StringProperty eventType;
    private final StringProperty actor;
    private final StringProperty affectedPerson;
    private final StringProperty details;
    private final StringProperty status;

    public AssetHistoryRecord(
            String activityDate,
            String eventType,
            String actor,
            String affectedPerson,
            String details,
            String status
    ) {
        this.activityDate = new SimpleStringProperty(safe(activityDate));
        this.eventType = new SimpleStringProperty(safe(eventType));
        this.actor = new SimpleStringProperty(safe(actor));
        this.affectedPerson = new SimpleStringProperty(safe(affectedPerson));
        this.details = new SimpleStringProperty(safe(details));
        this.status = new SimpleStringProperty(safe(status));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public String getActivityDate() { return activityDate.get(); }
    public String getEventType() { return eventType.get(); }
    public String getActor() { return actor.get(); }
    public String getAffectedPerson() { return affectedPerson.get(); }
    public String getDetails() { return details.get(); }
    public String getStatus() { return status.get(); }

    public StringProperty activityDateProperty() { return activityDate; }
    public StringProperty eventTypeProperty() { return eventType; }
    public StringProperty actorProperty() { return actor; }
    public StringProperty affectedPersonProperty() { return affectedPerson; }
    public StringProperty detailsProperty() { return details; }
    public StringProperty statusProperty() { return status; }
}
