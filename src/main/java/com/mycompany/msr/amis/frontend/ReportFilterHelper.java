package com.mycompany.msr.amis;

import java.time.LocalDate;
import javafx.scene.control.DatePicker;

public final class ReportFilterHelper {

    private ReportFilterHelper() {
    }

    public static boolean matchesDateRange(String rawDate, DatePicker fromPicker, DatePicker toPicker) {
        LocalDate date = parseDate(rawDate);
        if (date == null) {
            return fromPicker == null || fromPicker.getValue() == null;
        }
        LocalDate from = fromPicker == null ? null : fromPicker.getValue();
        LocalDate to = toPicker == null ? null : toPicker.getValue();
        if (from != null && date.isBefore(from)) {
            return false;
        }
        return to == null || !date.isAfter(to);
    }

    public static boolean isOverdue(String rawDate) {
        LocalDate date = parseDate(rawDate);
        return date != null && date.isBefore(LocalDate.now().minusDays(30));
    }

    private static LocalDate parseDate(String rawDate) {
        try {
            return rawDate == null || rawDate.isBlank() ? null : LocalDate.parse(rawDate.trim());
        } catch (Exception exception) {
            return null;
        }
    }
}
