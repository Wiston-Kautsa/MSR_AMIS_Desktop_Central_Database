package com.mycompany.msr.amis;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;

final class CurrencyFormatHelper {

    private static final DecimalFormat MWK_FORMAT = new DecimalFormat("#,##0.00");
    private static final String CURRENCY_PREFIX = "MWK ";

    private CurrencyFormatHelper() {
    }

    static String formatLocalCurrency(String value) {
        String normalized = normalizeNumericValue(value);
        if (normalized.isBlank()) {
            return "";
        }

        try {
            return CURRENCY_PREFIX + MWK_FORMAT.format(new BigDecimal(normalized));
        } catch (NumberFormatException exception) {
            return value == null ? "" : value.trim();
        }
    }

    static void installCurrencyFormatter(TextField textField) {
        if (textField == null) {
            return;
        }

        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                textField.setText(formatLocalCurrency(textField.getText()));
            }
        });
    }

    static <T> void installCurrencyCellFactory(TableColumn<T, String> column) {
        if (column == null) {
            return;
        }

        column.setCellFactory(tableColumn -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : formatLocalCurrency(item));
            }
        });
    }

    private static String normalizeNumericValue(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value.trim()
                .replace("MWK", "")
                .replace("mwk", "")
                .replace("MK", "")
                .replace("mk", "")
                .replace(",", "")
                .replace(" ", "");
        StringBuilder numeric = new StringBuilder();
        boolean decimalSeen = false;

        for (int i = 0; i < cleaned.length(); i++) {
            char character = cleaned.charAt(i);
            if (Character.isDigit(character)) {
                numeric.append(character);
            } else if (character == '.' && !decimalSeen) {
                numeric.append(character);
                decimalSeen = true;
            }
        }

        return numeric.toString();
    }
}
