package com.mycompany.msr.amis;

import javafx.concurrent.Task;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class UiBackgroundLoader {

    private UiBackgroundLoader() {
    }

    static <T> void run(String threadName,
                        Supplier<T> loader,
                        Consumer<T> onSuccess,
                        Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return loader.get();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> {
            if (onFailure != null) {
                onFailure.accept(task.getException());
            }
        });

        Thread thread = new Thread(task, threadName == null || threadName.isBlank() ? "ui-background-loader" : threadName);
        thread.setDaemon(true);
        thread.start();
    }
}
