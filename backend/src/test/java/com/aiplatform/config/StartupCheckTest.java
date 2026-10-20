package com.aiplatform.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StartupCheckTest {

    @Test
    void removesCopyPasteMistakesAroundValues() {
        assertEquals("secret", StartupCheck.clean(" secret "));
        assertEquals("jdbc:mysql://h:1/db", StartupCheck.clean("`jdbc:mysql://h:1/db`"));
        assertEquals("gsk_abc", StartupCheck.clean("\"gsk_abc\""));
        assertEquals("x", StartupCheck.clean(" ' `x` ' "));
        assertEquals("pa ss", StartupCheck.clean("pa ss"));      // spaces inside a value are kept
        assertEquals("keshu@1606", StartupCheck.clean("keshu@1606"));
    }
}
