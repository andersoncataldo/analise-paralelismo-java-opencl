package br.com.paralelismo.benchmark;

import java.util.Arrays;
import java.util.List;

/**
 * Utilitário estatístico para consolidação das amostras de benchmark.
 */
public final class StatisticsUtil {

    private StatisticsUtil() {}

    public static class Stats {
        public final int count;
        public final double mean;
        public final double stdDev;
        public final double min;
        public final double max;
        public final double median;

        public Stats(int count, double mean, double stdDev, double min, double max, double median) {
            this.count = count;
            this.mean = mean;
            this.stdDev = stdDev;
            this.min = min;
            this.max = max;
            this.median = median;
        }
    }

    public static Stats compute(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return new Stats(0, 0, 0, 0, 0, 0);
        }
        int n = values.size();
        double[] arr = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            arr[i] = values.get(i);
            sum += arr[i];
        }
        Arrays.sort(arr);
        double mean = sum / n;

        double varianceSum = 0;
        for (double v : arr) {
            double diff = v - mean;
            varianceSum += diff * diff;
        }
        double stdDev = (n > 1) ? Math.sqrt(varianceSum / (n - 1)) : 0.0;
        double median = (n % 2 == 1)
                ? arr[n / 2]
                : (arr[n / 2 - 1] + arr[n / 2]) / 2.0;

        return new Stats(n, mean, stdDev, arr[0], arr[n - 1], median);
    }

    public static double speedup(double serialTimeMs, double parallelTimeMs) {
        if (parallelTimeMs <= 0) {
            return 0.0;
        }
        return serialTimeMs / parallelTimeMs;
    }

    public static double efficiency(double speedup, int workers) {
        if (workers <= 0) {
            return 0.0;
        }
        return (speedup / workers) * 100.0;
    }
}
