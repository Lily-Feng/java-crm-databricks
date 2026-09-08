package com.minicrm.domain.shared;

public record Probability(double value) {

    public Probability {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("probability must be within [0,1]: " + value);
        }
    }

    public static Probability of(double value) {
        return new Probability(value);
    }
}
