package com.mycompany.msr.amis;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

public final class TableNumbering {
    private TableNumbering() {
    }

    public static <T> void install(TableColumn<T, Void> column) {
        if (column == null) {
            return;
        }
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
            }
        });
    }
}
