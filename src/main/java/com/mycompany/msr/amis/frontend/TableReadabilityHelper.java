package com.mycompany.msr.amis;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

final class TableReadabilityHelper {

    private TableReadabilityHelper() {
    }

    static void applyTo(Parent root) {
        if (root == null) {
            return;
        }
        applyRecursively(root);
        Platform.runLater(() -> applyRecursively(root));
    }

    private static void applyRecursively(Parent parent) {
        if (parent instanceof TableView<?>) {
            configureTable((TableView<?>) parent);
        }

        for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Parent) {
                applyRecursively((Parent) child);
            }
        }
    }

    private static void configureTable(TableView<?> table) {
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        for (TableColumn<?, ?> column : table.getColumns()) {
            configureColumn(column);
        }
    }

    private static void configureColumn(TableColumn<?, ?> column) {
        for (TableColumn<?, ?> child : column.getColumns()) {
            configureColumn(child);
        }

        double preferredWidth = preferredWidthFor(column);
        column.setMinWidth(preferredWidth);
        column.setPrefWidth(Math.max(column.getPrefWidth(), preferredWidth));
        column.setResizable(true);
    }

    private static double preferredWidthFor(TableColumn<?, ?> column) {
        String text = safeLower(column.getText());
        String id = safeLower(column.getId());
        String key = text + " " + id;

        if (key.contains("no.")) {
            return 58;
        }
        if (key.matches(".*\\bid\\b.*")) {
            return 90;
        }
        if (key.contains("qty") || key.contains("quantity") || key.contains("count") || key.contains("cost")) {
            return 140;
        }
        if (key.contains("date") || key.contains("time") || key.contains("timestamp") || key.contains("created")
                || key.contains("captured") || key.contains("submitted") || key.contains("published")) {
            return 190;
        }
        if (key.contains("phone") || key.contains("nid") || key.contains("status") || key.contains("condition")
                || key.contains("source") || key.contains("role") || key.contains("module")) {
            return 175;
        }
        if (key.contains("equipment")) {
            return 280;
        }
        if (key.contains("department")) {
            return 280;
        }
        if (key.contains("asset") || key.contains("serial") || key.contains("imei") || key.contains("category")) {
            return 220;
        }
        if (key.contains("person") || key.contains("officer") || key.contains("assigned") || key.contains("returned")
                || key.contains("performed") || key.contains("user") || key.contains("actor")
                || key.contains("name")) {
            return 280;
        }
        if (key.contains("email") || key.contains("file") || key.contains("location") || key.contains("supplier")
                || key.contains("action taken") || key.contains("issue")) {
            return 300;
        }
        if (key.contains("reason") || key.contains("remarks") || key.contains("details") || key.contains("message")
                || key.contains("error")) {
            return 440;
        }

        return Math.max(160, text.length() * 14.0 + 44);
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
