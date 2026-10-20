package com.aiplatform.ml;

import com.aiplatform.exception.TrainingDataException;
import java.util.List;

/**
 * Multiple linear regression fitted with ordinary least squares:
 *   y = intercept + c1*x1 + c2*x2 + ... + cp*xp
 * The coefficients minimise the sum of squared errors on the training rows. They are found by
 * solving the normal equations (Xc^T Xc) c = Xc^T yc on mean-centred data with Gaussian elimination.
 */
public final class LinearRegression {
    private final double intercept;
    private final double[] coefficients;

    private LinearRegression(double intercept, double[] coefficients) {
        this.intercept = intercept;
        this.coefficients = coefficients;
    }

    public double intercept() { return intercept; }
    public double[] coefficients() { return coefficients.clone(); }

    public static LinearRegression fit(double[][] x, double[] y, List<String> featureNames) {
        int n = x.length, p = featureNames.size();
        if (n != y.length) throw new IllegalArgumentException("x and y must have the same number of rows.");
        if (n < p + 2) {
            throw new TrainingDataException("Not enough training rows (" + n + ") for " + p
                    + " feature(s). Use more data or fewer features.");
        }

        double[] xMean = new double[p];
        double yMean = 0;
        for (int i = 0; i < n; i++) {
            yMean += y[i];
            for (int j = 0; j < p; j++) xMean[j] += x[i][j];
        }
        yMean /= n;
        for (int j = 0; j < p; j++) xMean[j] /= n;

        double[][] a = new double[p][p];
        double[] b = new double[p];
        for (int i = 0; i < n; i++) {
            double yc = y[i] - yMean;
            for (int j = 0; j < p; j++) {
                double xj = x[i][j] - xMean[j];
                b[j] += xj * yc;
                for (int k = j; k < p; k++) a[j][k] += xj * (x[i][k] - xMean[k]);
            }
        }
        for (int j = 0; j < p; j++) {
            for (int k = 0; k < j; k++) a[j][k] = a[k][j];
            if (a[j][j] <= 1e-12) {
                throw new TrainingDataException("Feature '" + featureNames.get(j)
                        + "' has the same value in every training row, so it cannot be used.");
            }
        }

        double[] c = solve(a, b, featureNames);
        double intercept = yMean;
        for (int j = 0; j < p; j++) intercept -= c[j] * xMean[j];
        return new LinearRegression(intercept, c);
    }

    public double predict(double[] row) {
        double sum = intercept;
        for (int j = 0; j < coefficients.length; j++) sum += coefficients[j] * row[j];
        return sum;
    }

    /** Gaussian elimination with partial pivoting. */
    private static double[] solve(double[][] a, double[] b, List<String> names) {
        int p = b.length;
        double scale = 0;
        for (int j = 0; j < p; j++) scale = Math.max(scale, Math.abs(a[j][j]));
        for (int col = 0; col < p; col++) {
            int pivot = col;
            for (int r = col + 1; r < p; r++) if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r;
            if (Math.abs(a[pivot][col]) < 1e-10 * scale) {
                throw new TrainingDataException("Some features are perfectly correlated (for example '"
                        + names.get(col) + "'), so a unique linear model does not exist. Remove a duplicate feature.");
            }
            double[] tmpRow = a[col]; a[col] = a[pivot]; a[pivot] = tmpRow;
            double tmp = b[col]; b[col] = b[pivot]; b[pivot] = tmp;
            for (int r = col + 1; r < p; r++) {
                double f = a[r][col] / a[col][col];
                b[r] -= f * b[col];
                for (int k = col; k < p; k++) a[r][k] -= f * a[col][k];
            }
        }
        double[] x = new double[p];
        for (int r = p - 1; r >= 0; r--) {
            double sum = b[r];
            for (int k = r + 1; k < p; k++) sum -= a[r][k] * x[k];
            x[r] = sum / a[r][r];
        }
        return x;
    }
}
