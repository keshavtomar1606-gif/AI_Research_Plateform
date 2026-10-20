package com.aiplatform.ml;

/** Standard regression metrics comparing true values with predictions. */
public final class RegressionMetrics {
    private RegressionMetrics() {}

    /** Mean absolute error: the average size of a prediction mistake, in the target's units. */
    public static double mae(double[] actual, double[] predicted) {
        double sum = 0;
        for (int i = 0; i < actual.length; i++) sum += Math.abs(actual[i] - predicted[i]);
        return sum / actual.length;
    }

    /** Root mean squared error: like MAE but punishes large mistakes more. */
    public static double rmse(double[] actual, double[] predicted) {
        double sum = 0;
        for (int i = 0; i < actual.length; i++) {
            double e = actual[i] - predicted[i];
            sum += e * e;
        }
        return Math.sqrt(sum / actual.length);
    }

    /**
     * R squared: share of the target's variance explained by the model (1 = perfect, 0 = no better than
     * predicting the average, negative = worse). Returns NaN when every actual value is identical.
     */
    public static double r2(double[] actual, double[] predicted) {
        double mean = 0;
        for (double v : actual) mean += v;
        mean /= actual.length;
        double ssRes = 0, ssTot = 0;
        for (int i = 0; i < actual.length; i++) {
            ssRes += Math.pow(actual[i] - predicted[i], 2);
            ssTot += Math.pow(actual[i] - mean, 2);
        }
        return ssTot == 0 ? Double.NaN : 1 - ssRes / ssTot;
    }
}
