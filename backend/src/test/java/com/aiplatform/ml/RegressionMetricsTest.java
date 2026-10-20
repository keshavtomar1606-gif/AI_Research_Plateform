package com.aiplatform.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegressionMetricsTest {
    // Same example as the scikit-learn documentation for mean_absolute_error / r2_score.
    private final double[] actual = {3, -0.5, 2, 7};
    private final double[] predicted = {2.5, 0.0, 2, 8};

    @Test
    void mae() {
        assertEquals(0.5, RegressionMetrics.mae(actual, predicted), 1e-12);
    }

    @Test
    void rmse() {
        assertEquals(Math.sqrt(0.375), RegressionMetrics.rmse(actual, predicted), 1e-12);
    }

    @Test
    void r2() {
        assertEquals(0.9486081370449679, RegressionMetrics.r2(actual, predicted), 1e-12);
    }

    @Test
    void r2IsUndefinedWhenTargetIsConstant() {
        assertTrue(Double.isNaN(RegressionMetrics.r2(new double[] {1, 1, 1}, new double[] {1, 2, 3})));
    }
}
