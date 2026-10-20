package com.aiplatform.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LinearRegressionTest {

    @Test
    void recoversExactCoefficientsFromNoiseFreeData() {
        Random rng = new Random(1);
        double[][] x = new double[50][3];
        double[] y = new double[50];
        for (int i = 0; i < 50; i++) {
            x[i][0] = rng.nextDouble() * 10;
            x[i][1] = rng.nextDouble() * 100;
            x[i][2] = rng.nextDouble() * 5 - 2;
            y[i] = 7.5 + 2.0 * x[i][0] - 0.3 * x[i][1] + 4.25 * x[i][2];
        }
        LinearRegression model = LinearRegression.fit(x, y, List.of("a", "b", "c"));
        assertEquals(7.5, model.intercept(), 1e-9);
        assertEquals(2.0, model.coefficients()[0], 1e-9);
        assertEquals(-0.3, model.coefficients()[1], 1e-9);
        assertEquals(4.25, model.coefficients()[2], 1e-9);
        assertEquals(7.5 + 2.0 - 3.0 + 4.25, model.predict(new double[] {1, 10, 1}), 1e-9);
    }

    @Test
    void simpleLineMatchesHandCalculation() {
        // Points (1,2) (2,4) (3,5) (4,4) (5,5): slope = 0.6, intercept = 2.2 (worked by hand with OLS formulas)
        double[][] x = {{1}, {2}, {3}, {4}, {5}};
        double[] y = {2, 4, 5, 4, 5};
        LinearRegression model = LinearRegression.fit(x, y, List.of("x"));
        assertEquals(0.6, model.coefficients()[0], 1e-12);
        assertEquals(2.2, model.intercept(), 1e-12);
    }

    @Test
    void rejectsConstantFeature() {
        double[][] x = {{1, 5}, {2, 5}, {3, 5}, {4, 5}, {5, 5}};
        double[] y = {1, 2, 3, 4, 5};
        var e = assertThrows(IllegalArgumentException.class, () -> LinearRegression.fit(x, y, List.of("a", "constant")));
        assertTrue(e.getMessage().contains("constant"));
    }

    @Test
    void rejectsPerfectlyCorrelatedFeatures() {
        double[][] x = {{1, 2}, {2, 4}, {3, 6}, {4, 8}, {5, 10}, {6, 12}};
        double[] y = {1, 3, 2, 5, 4, 6};
        var e = assertThrows(IllegalArgumentException.class, () -> LinearRegression.fit(x, y, List.of("a", "twice_a")));
        assertTrue(e.getMessage().contains("correlated"));
    }

    @Test
    void rejectsTooFewRows() {
        double[][] x = {{1, 2}, {2, 1}, {3, 3}};
        double[] y = {1, 2, 3};
        assertThrows(IllegalArgumentException.class, () -> LinearRegression.fit(x, y, List.of("a", "b")));
    }
}
