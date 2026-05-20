package com.mycompany.msr.amis;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public final class OperationFeedbackHelper {
    private static final String OVERLAY_ID = "operation-feedback-overlay";

    private OperationFeedbackHelper() {
    }

    public static void showInfo(String title, String message) {
        show("info", title, message);
    }

    public static void showWarning(String title, String message) {
        show("warning", title, message);
    }

    public static void showError(String title, String message) {
        show("error", title, message);
    }

    public static void showConfirmation(String title,
                                        String message,
                                        String confirmText,
                                        String cancelText,
                                        Runnable onConfirm) {
        Runnable action = () -> showConfirmationInScene(title, message, confirmText, cancelText, onConfirm);
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private static void show(String tone, String title, String message) {
        Runnable action = () -> showInScene(tone, title, message);
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private static void showInScene(String tone, String title, String message) {
        Scene scene = App.getScene();
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        ensureStylesheet(scene);

        StackPane root = ensureStackRoot(scene);
        VBox overlay = findOrCreateOverlay(root);

        VBox card = new VBox(8);
        card.getStyleClass().addAll("operation-feedback-card", "operation-feedback-" + normalizeTone(tone));
        card.setMaxWidth(460);
        card.setPadding(new Insets(14, 16, 14, 16));

        Label titleLabel = new Label(normalizeText(title, "Message"));
        titleLabel.getStyleClass().add("operation-feedback-title");
        titleLabel.setWrapText(true);

        Label messageLabel = new Label(normalizeText(message, ""));
        messageLabel.getStyleClass().add("operation-feedback-message");
        messageLabel.setWrapText(true);

        Button closeButton = new Button("Dismiss");
        closeButton.getStyleClass().add("operation-feedback-dismiss");
        closeButton.setOnAction(event -> overlay.getChildren().remove(card));

        card.getChildren().addAll(titleLabel, messageLabel, closeButton);
        overlay.getChildren().add(0, card);

        PauseTransition delay = new PauseTransition(Duration.seconds("error".equals(tone) ? 8 : 5));
        delay.setOnFinished(event -> overlay.getChildren().remove(card));
        delay.play();
    }

    private static void showConfirmationInScene(String title,
                                                String message,
                                                String confirmText,
                                                String cancelText,
                                                Runnable onConfirm) {
        Scene scene = App.getScene();
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        ensureStylesheet(scene);

        StackPane root = ensureStackRoot(scene);
        VBox overlay = findOrCreateOverlay(root);

        VBox card = new VBox(10);
        card.getStyleClass().addAll("operation-feedback-card", "operation-feedback-warning");
        card.setMaxWidth(480);
        card.setPadding(new Insets(14, 16, 14, 16));

        Label titleLabel = new Label(normalizeText(title, "Confirm Action"));
        titleLabel.getStyleClass().add("operation-feedback-title");
        titleLabel.setWrapText(true);

        Label messageLabel = new Label(normalizeText(message, ""));
        messageLabel.getStyleClass().add("operation-feedback-message");
        messageLabel.setWrapText(true);

        javafx.scene.layout.HBox buttons = new javafx.scene.layout.HBox(8);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button cancelButton = new Button(normalizeText(cancelText, "Cancel"));
        cancelButton.getStyleClass().add("operation-feedback-dismiss");
        cancelButton.setOnAction(event -> overlay.getChildren().remove(card));

        Button confirmButton = new Button(normalizeText(confirmText, "Continue"));
        confirmButton.getStyleClass().add("operation-feedback-confirm");
        confirmButton.setOnAction(event -> {
            overlay.getChildren().remove(card);
            if (onConfirm != null) {
                onConfirm.run();
            }
        });

        buttons.getChildren().addAll(cancelButton, confirmButton);
        card.getChildren().addAll(titleLabel, messageLabel, buttons);
        overlay.getChildren().add(0, card);
    }

    private static StackPane ensureStackRoot(Scene scene) {
        Parent currentRoot = scene.getRoot();
        if (currentRoot instanceof StackPane) {
            return (StackPane) currentRoot;
        }

        StackPane wrappedRoot = new StackPane();
        wrappedRoot.getChildren().add(currentRoot);
        scene.setRoot(wrappedRoot);
        return wrappedRoot;
    }

    private static VBox findOrCreateOverlay(StackPane root) {
        for (Node child : root.getChildren()) {
            if (OVERLAY_ID.equals(child.getId()) && child instanceof VBox) {
                return (VBox) child;
            }
        }

        VBox overlay = new VBox(10);
        overlay.setId(OVERLAY_ID);
        overlay.setAlignment(Pos.TOP_RIGHT);
        overlay.setPickOnBounds(false);
        overlay.setMouseTransparent(false);
        overlay.setPadding(new Insets(18));
        StackPane.setAlignment(overlay, Pos.TOP_RIGHT);
        root.getChildren().add(overlay);
        return overlay;
    }

    private static String normalizeTone(String tone) {
        if ("warning".equals(tone) || "error".equals(tone)) {
            return tone;
        }
        return "info";
    }

    private static String normalizeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static void ensureStylesheet(Scene scene) {
        String stylesheet = OperationFeedbackHelper.class
                .getResource("/com/mycompany/msr/amis/frontend/theme.css")
                .toExternalForm();
        if (!scene.getStylesheets().contains(stylesheet)) {
            scene.getStylesheets().add(stylesheet);
        }
    }
}
