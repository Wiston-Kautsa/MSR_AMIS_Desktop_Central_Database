package com.mycompany.msr.amis;

import java.io.File;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.stage.FileChooser;

public final class FileLocationHelper {

    private static final String APP_FOLDER_NAME = "MSR AMIS";

    private FileLocationHelper() {
    }

    public static File getDownloadsDirectory() {
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        if (!downloads.exists()) {
            downloads.mkdirs();
        }
        return downloads;
    }

    public static void useDownloadsDirectory(FileChooser chooser) {
        File downloads = getDownloadsDirectory();
        if (downloads.exists() && downloads.isDirectory()) {
            chooser.setInitialDirectory(downloads);
        }
    }

    public static File fileInDownloads(String fileName) {
        return new File(getDownloadsDirectory(), fileName);
    }

    public static Path getApplicationDataDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path baseDirectory;

        if (localAppData != null && !localAppData.isBlank()) {
            baseDirectory = Paths.get(localAppData);
        } else {
            baseDirectory = Paths.get(System.getProperty("user.home"), ".msr-amis");
        }

        Path appDirectory = baseDirectory.resolve(APP_FOLDER_NAME);

        try {
            Files.createDirectories(appDirectory);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create application data directory: " + appDirectory, e);
        }

        return appDirectory;
    }

    public static Path resolveAppDataFile(String fileName) {
        return getApplicationDataDirectory().resolve(fileName);
    }

    public static Path resolveWorkingDirectoryFile(String fileName) {
        return Paths.get(System.getProperty("user.dir")).resolve(fileName).toAbsolutePath().normalize();
    }

    public static Path resolveCanonicalDataFile(String fileName) {
        Path appDataFile = resolveAppDataFile(fileName);
        Path workingDirectoryFile = resolveWorkingDirectoryFile(fileName);

        if (Files.exists(appDataFile)) {
            return appDataFile;
        }

        if (Files.exists(workingDirectoryFile)) {
            try {
                Files.copy(workingDirectoryFile, appDataFile, StandardCopyOption.REPLACE_EXISTING);
                return appDataFile;
            } catch (IOException e) {
                return workingDirectoryFile;
            }
        }

        return appDataFile;
    }
}
