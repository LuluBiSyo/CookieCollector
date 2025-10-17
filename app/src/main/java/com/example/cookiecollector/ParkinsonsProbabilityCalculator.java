package com.example.cookiecollector;

/**
 * A utility class to calculate a hypothetical probability score for developing
 * Parkinson's Disease (PD) based on performance metrics from pointing tasks.
 *
 * The model used here is for demonstration purposes and is not a real medical diagnosis.
 * It assumes that longer average task durations might correlate with a higher risk score.
 */
public class ParkinsonsProbabilityCalculator {

    // These are hypothetical baseline values. In a real-world scenario, these would
    // be determined from clinical data and research.
    private static final float NON_PD_a = 694.57F;
    private static final float NON_PD_b = 61.99F;
    private static final float NON_PD_w = 5.997F;
    private static final float NON_PD_c = 58170.11F;
    private static final float NON_PD_d = 2921.22F;
//    private static final float PD_a = 555.23F;
    private static final float PD_a = 955.23F;
    private static final float PD_b = 165.69F;
    private static final float PD_w = 5.38F;
    private static final float PD_c = 106104.84F;
    private static final float PD_d = 11103.88F;

    private static final boolean DEBUG = true;

    public static float calculateParkinsonProbability(float distance, float width, float movementTime) {
        if (movementTime <= 0 || distance <= 0 || width <= 0) {
            if (DEBUG){
                android.util.Log.d("PD_PROB_CALC", "--- New Calculation ---");
                android.util.Log.d("PD_PROB_CALC", "Input Distance: " + distance);
                android.util.Log.d("PD_PROB_CALC", "Input Width: " + width);
                android.util.Log.d("PD_PROB_CALC", "Input MovementTime: " + movementTime);
                android.util.Log.d("PD_PROB_CALC", "Final PD Probability: " + 0);
            }
            return 0.0f; // Return 0 if the time is not positive.
        }

        // calculate the probability of being PD movement
        float mean_PD = calculateMeanMT(PD_a, PD_b, calculateIndexOfDifficulty(PD_w, distance, width));
        float variance_PD = calculateMTVariance(PD_c, PD_d, calculateIndexOfDifficulty(PD_w, distance, width));
        float probability_PD = calculateGaussianProbability(movementTime, mean_PD, variance_PD);

        // calculate the probability of being non-PD movement
        float mean_NON_PD = calculateMeanMT(NON_PD_a, NON_PD_b, calculateIndexOfDifficulty(NON_PD_w, distance, width));
        float variance_NON_PD = calculateMTVariance(NON_PD_c, NON_PD_d, calculateIndexOfDifficulty(NON_PD_w, distance, width));
        float probability_NON_PD = calculateGaussianProbability(movementTime, mean_NON_PD, variance_NON_PD);

        // calculate the probability of being PD
        float probability = probability_PD / (probability_PD + probability_NON_PD);

        if (DEBUG) {
            android.util.Log.d("PD_PROB_CALC", "--- New Calculation ---");
            android.util.Log.d("PD_PROB_CALC", "Input Distance: " + distance);
            android.util.Log.d("PD_PROB_CALC", "Input Width: " + width);
            android.util.Log.d("PD_PROB_CALC", "Input MovementTime: " + movementTime);
            android.util.Log.d("PD_PROB_CALC", "PD Mean: " + mean_PD);
            android.util.Log.d("PD_PROB_CALC", "PD Variance: " + variance_PD);
            android.util.Log.d("PD_PROB_CALC", "PD Gaussian Prob: " + probability_PD);
            android.util.Log.d("PD_PROB_CALC", "Non-PD Mean: " + mean_NON_PD);
            android.util.Log.d("PD_PROB_CALC", "Non-PD Variance: " + variance_NON_PD);
            android.util.Log.d("PD_PROB_CALC", "Non-PD Gaussian Prob: " + probability_NON_PD);
            android.util.Log.d("PD_PROB_CALC", "Final PD Probability: " + probability);
        }

        return probability_PD / (probability_PD + probability_NON_PD);
    }

    private static float calculateIndexOfDifficulty(float w, float distance, float width) {
        return (float) (Math.log(distance / Math.sqrt(width * width - w * w) + 1) /  Math.log(2));
    }

    /**
     * Using Finger-Fitts law to calculate the mean of movement time
     */
    private static float calculateMeanMT(float a, float b, float indexOfDifficulty) {
        return (float) (a + b * indexOfDifficulty);
    }

    private static float calculateMTVariance(float c, float d, float indexOfDifficulty) {
        return (float) (c + d * indexOfDifficulty * indexOfDifficulty);
    }

    /**
     * Calculates the probability density function (PDF) of a normal (Gaussian) distribution
     * at a specific value 'x'.*
     * @param x The value at which to evaluate the distribution.
     * @param mean The mean (mu) of the distribution.
     * @param variance The variance (sigma squared) of the distribution.
     * @return The value of the probability density function at x. Returns 0 if variance is not positive.
     */
    private static float calculateGaussianProbability(float x, float mean, float variance) {
        if (variance <= 0) {
            return 0.0f; // Variance must be positive.
        }

        // Calculate the standard deviation (sigma)
        double stdDev = Math.sqrt(variance);

        // Calculate the exponent part of the PDF formula
        double exponent = -0.5 * Math.pow((x - mean) / stdDev, 2);

        // Calculate the coefficient part of the PDF formula
        double coefficient = 1.0 / (stdDev * Math.sqrt(2 * Math.PI));

        // Return the final PDF value
        return (float) (coefficient * Math.exp(exponent));
    }


}

