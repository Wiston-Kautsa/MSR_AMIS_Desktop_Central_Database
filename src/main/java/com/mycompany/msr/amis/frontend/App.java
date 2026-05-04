package com.mycompany.msr.amis;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

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

    @Override
    public void start(Stage stage) throws Exception {
        if (ServiceRegistry.getConfiguration().usesLocalDatabase()) {
            DatabaseHandler.initializeDatabase();
        }
        primaryStage = stage;

        scene = new Scene(loadFXML("Login"), LOGIN_WIDTH, LOGIN_HEIGHT);

        stage.setTitle("MSR AMIS");
        stage.setScene(scene);
        stage.setWidth(LOGIN_WIDTH);
        stage.setHeight(LOGIN_HEIGHT);
        stage.centerOnScreen();
        stage.show();
    }

    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    static void showLoginPage() throws IOException {
        scene.setRoot(loadFXML("Login"));
        primaryStage.setWidth(LOGIN_WIDTH);
        primaryStage.setHeight(LOGIN_HEIGHT);
        primaryStage.centerOnScreen();
    }

    static void showDashboardPage() throws IOException {
        scene.setRoot(loadFXML("Dashboards"));
        primaryStage.setWidth(DASHBOARD_WIDTH);
        primaryStage.setHeight(DASHBOARD_HEIGHT);
        primaryStage.centerOnScreen();
    }

    static void showSetupUsersPage() throws IOException {
        scene.setRoot(loadFXML("Users"));
        primaryStage.setWidth(SETUP_WIDTH);
        primaryStage.setHeight(SETUP_HEIGHT);
        primaryStage.centerOnScreen();
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(FRONTEND_RESOURCE_ROOT + fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }
}
