package com.mycompany.msr.amis;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.geometry.Rectangle2D;

import java.io.IOException;

public class App extends Application {
    private static final String FRONTEND_RESOURCE_ROOT = "/com/mycompany/msr/amis/frontend/";

    private static final double LOGIN_WIDTH = 760;
    private static final double LOGIN_HEIGHT = 500;
    private static final double DASHBOARD_WIDTH = 1100;
    private static final double DASHBOARD_HEIGHT = 700;
    private static final double SETUP_WIDTH = 980;
    private static final double SETUP_HEIGHT = 700;

    private static Scene scene;
    private static Stage primaryStage;
    private static boolean singleInstanceAcquired;

    @Override
    public void start(Stage stage) throws Exception {
        if (ServiceRegistry.getConfiguration().usesLocalDatabase()) {
            DatabaseHandler.initializeDatabase();
        }
        primaryStage = stage;

        scene = new Scene(loadFXML("Login"), LOGIN_WIDTH, LOGIN_HEIGHT);

        stage.setTitle("MSR AMIS");
        loadApplicationIcon(stage);
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setWidth(LOGIN_WIDTH);
        stage.setHeight(LOGIN_HEIGHT);
        stage.centerOnScreen();
        stage.show();
    }

    @Override
    public void stop() {
        if (singleInstanceAcquired) {
            SingleInstanceGuard.release();
            singleInstanceAcquired = false;
        }
    }

    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    static Scene getScene() {
        return scene;
    }

    static void showLoginPage() throws IOException {
        primaryStage.setFullScreen(false);
        primaryStage.setMaximized(false);
        scene.setRoot(loadFXML("Login"));
        primaryStage.setWidth(LOGIN_WIDTH);
        primaryStage.setHeight(LOGIN_HEIGHT);
        primaryStage.centerOnScreen();
    }

    static void showDashboardPage() throws IOException {
        FXMLLoader loader = createLoader("Dashboards");
        Parent dashboardRoot = loader.load();
        scene.setRoot(dashboardRoot);
        DashboardsController controller = loader.getController();
        if (controller != null) {
            controller.showDashboardHome();
            Platform.runLater(controller::showDashboardHome);
        }
        openDashboardFullScreen();
        Platform.runLater(App::openDashboardFullScreen);
    }

    private static void openDashboardFullScreen() {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        primaryStage.setFullScreen(false);
        primaryStage.setMaximized(false);
        primaryStage.setMinWidth(Math.min(DASHBOARD_WIDTH, bounds.getWidth()));
        primaryStage.setMinHeight(Math.min(DASHBOARD_HEIGHT, bounds.getHeight()));
        primaryStage.setX(bounds.getMinX());
        primaryStage.setY(bounds.getMinY());
        primaryStage.setWidth(bounds.getWidth());
        primaryStage.setHeight(bounds.getHeight());
        primaryStage.setMaximized(true);
    }

    static void showSetupUsersPage() throws IOException {
        primaryStage.setFullScreen(false);
        primaryStage.setMaximized(false);
        scene.setRoot(loadFXML("Users"));
        primaryStage.setWidth(SETUP_WIDTH);
        primaryStage.setHeight(SETUP_HEIGHT);
        primaryStage.centerOnScreen();
    }

    private static Parent loadFXML(String fxml) throws IOException {
        return createLoader(fxml).load();
    }

    private static FXMLLoader createLoader(String fxml) {
        return new FXMLLoader(App.class.getResource(FRONTEND_RESOURCE_ROOT + fxml + ".fxml"));
    }

    private static void loadApplicationIcon(Stage stage) {
        try {
            stage.getIcons().add(new Image(App.class.getResourceAsStream(FRONTEND_RESOURCE_ROOT + "msr-amis-icon.png")));
        } catch (Exception ignored) {
            // The packaged executable icon is still supplied by jpackage.
        }
    }

    public static void main(String[] args) {
        singleInstanceAcquired = SingleInstanceGuard.acquire();
        if (!singleInstanceAcquired) {
            System.err.println("MSR AMIS is already running.");
            return;
        }
        launch();
    }
}
