package com.mycompany.msr.amis;

import java.nio.file.Path;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class BackupSyncRecord {

    private final StringProperty fileName;
    private final StringProperty actor;
    private final StringProperty timestamp;
    private final StringProperty location;
    private final StringProperty status;
    private final Path filePath;

    public BackupSyncRecord(String fileName, String actor, String timestamp, String location, String status, Path filePath) {
        this.fileName = new SimpleStringProperty(safe(fileName));
        this.actor = new SimpleStringProperty(safe(actor));
        this.timestamp = new SimpleStringProperty(safe(timestamp));
        this.location = new SimpleStringProperty(safe(location));
        this.status = new SimpleStringProperty(safe(status));
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName.get();
    }

    public String getActor() {
        return actor.get();
    }

    public String getTimestamp() {
        return timestamp.get();
    }

    public String getLocation() {
        return location.get();
    }

    public String getStatus() {
        return status.get();
    }

    public Path getFilePath() {
        return filePath;
    }

    public StringProperty fileNameProperty() {
        return fileName;
    }

    public StringProperty actorProperty() {
        return actor;
    }

    public StringProperty timestampProperty() {
        return timestamp;
    }

    public StringProperty locationProperty() {
        return location;
    }

    public StringProperty statusProperty() {
        return status;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
