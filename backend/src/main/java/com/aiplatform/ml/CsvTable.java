package com.aiplatform.ml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A small, strict CSV reader (comma separated, first line = header, "quoted, values" supported).
 * Any problem is reported as an IllegalArgumentException with a message a student can act on.
 */
public final class CsvTable {
    public static final int MAX_COLUMNS = 50;
    public static final int MAX_ROWS = 50_000;
    private static final Set<String> MISSING = Set.of("", "na", "n/a", "nan", "null", "?");

    private final List<String> columns;
    private final List<String[]> rows;

    private CsvTable(List<String> columns, List<String[]> rows) {
        this.columns = columns;
        this.rows = rows;
    }

    public List<String> columns() { return columns; }
    public List<String[]> rows() { return rows; }
    public int rowCount() { return rows.size(); }

    public int indexOf(String column) {
        int i = columns.indexOf(column);
        if (i < 0) throw new IllegalArgumentException("Column '" + column + "' does not exist in this dataset.");
        return i;
    }

    public static boolean isMissing(String value) {
        return value == null || MISSING.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    /** Parses a number, or returns null if the text is not a finite number. */
    public static Double parseNumber(String value) {
        try {
            double d = Double.parseDouble(value.trim());
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isNumeric(int col) {
        boolean sawValue = false;
        for (String[] row : rows) {
            if (isMissing(row[col])) continue;
            if (parseNumber(row[col]) == null) return false;
            sawValue = true;
        }
        return sawValue;
    }

    public int missingCount(int col) {
        int count = 0;
        for (String[] row : rows) if (isMissing(row[col])) count++;
        return count;
    }

    public static CsvTable parse(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("The CSV file is empty.");
        if (text.charAt(0) == '﻿') text = text.substring(1);

        List<List<String>> records = readRecords(text);
        if (records.isEmpty()) throw new IllegalArgumentException("The CSV file is empty.");

        List<String> header = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : records.get(0)) {
            String name = raw.trim().replace('\t', ' ');
            if (name.isEmpty()) throw new IllegalArgumentException("The header row contains an empty column name.");
            if (!seen.add(name)) throw new IllegalArgumentException("Column name '" + name + "' appears more than once.");
            header.add(name);
        }
        if (header.size() < 2) {
            throw new IllegalArgumentException("The CSV needs at least two columns (features and a target). Is it comma separated?");
        }
        if (header.size() > MAX_COLUMNS) throw new IllegalArgumentException("Too many columns (max " + MAX_COLUMNS + ").");

        List<String[]> rows = new ArrayList<>();
        for (int r = 1; r < records.size(); r++) {
            List<String> record = records.get(r);
            if (record.size() == 1 && record.get(0).isBlank()) continue;
            if (record.size() != header.size()) {
                throw new IllegalArgumentException("Data row " + r + " has " + record.size() + " values but the header has "
                        + header.size() + " columns.");
            }
            rows.add(record.toArray(String[]::new));
            if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("Too many rows (max " + MAX_ROWS + ").");
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("The CSV has a header but no data rows.");
        return new CsvTable(List.copyOf(header), rows);
    }

    private static List<List<String>> readRecords(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else inQuotes = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                current.add(field.toString()); field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                current.add(field.toString()); field.setLength(0);
                records.add(current); current = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (inQuotes) throw new IllegalArgumentException("The CSV has an unclosed quote (\").");
        if (field.length() > 0 || !current.isEmpty()) { current.add(field.toString()); records.add(current); }
        return records;
    }
}
