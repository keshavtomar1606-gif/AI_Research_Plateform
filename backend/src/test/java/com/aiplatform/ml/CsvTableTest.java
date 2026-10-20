package com.aiplatform.ml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTableTest {

    @Test
    void parsesQuotesBomAndWindowsLineEndings() {
        CsvTable t = CsvTable.parse("﻿name,score\r\n\"Smith, Jo\",5\r\n\"Say \"\"hi\"\"\",NA\r\n\r\n");
        assertEquals(List.of("name", "score"), t.columns());
        assertEquals(2, t.rowCount());
        assertArrayEquals(new String[] {"Smith, Jo", "5"}, t.rows().get(0));
        assertArrayEquals(new String[] {"Say \"hi\"", "NA"}, t.rows().get(1));
        assertEquals(1, t.missingCount(1));
        assertTrue(t.isNumeric(1));
        assertFalse(t.isNumeric(0));
    }

    @Test
    void rejectsRowsWithWrongNumberOfValues() {
        var e = assertThrows(IllegalArgumentException.class, () -> CsvTable.parse("a,b\n1,2\n3\n"));
        assertTrue(e.getMessage().contains("row 2"));
    }

    @Test
    void rejectsDuplicateAndEmptyHeaders() {
        assertThrows(IllegalArgumentException.class, () -> CsvTable.parse("a,a\n1,2\n"));
        assertThrows(IllegalArgumentException.class, () -> CsvTable.parse("a,\n1,2\n"));
    }

    @Test
    void rejectsEmptyAndHeaderOnlyFiles() {
        assertThrows(IllegalArgumentException.class, () -> CsvTable.parse(""));
        assertThrows(IllegalArgumentException.class, () -> CsvTable.parse("a,b\n"));
        assertThrows(IllegalArgumentException.class, () -> CsvTable.parse("just one column\n1\n"));
    }

    @Test
    void rejectsUnclosedQuote() {
        assertThrows(IllegalArgumentException.class, () -> CsvTable.parse("a,b\n\"1,2\n"));
    }
}
