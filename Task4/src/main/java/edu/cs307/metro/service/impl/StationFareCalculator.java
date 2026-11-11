package edu.cs307.metro.service.impl;

import java.math.BigDecimal;

public final class StationFareCalculator {

    public BigDecimal calculate(int stopCount) {
        if (stopCount < 0) {
            throw new IllegalArgumentException("站间段数不能为负数。");
        }

        if (stopCount == 0) {
            return BigDecimal.ZERO;
        }

        if (stopCount <= 4) {
            return BigDecimal.valueOf(2);
        }

        if (stopCount <= 12) {
            int extraStops = stopCount - 4;
            int extraFare = (extraStops + 3) / 4;
            return BigDecimal.valueOf(2 + extraFare);
        }

        if (stopCount <= 24) {
            int extraStops = stopCount - 12;
            int extraFare = (extraStops + 5) / 6;
            return BigDecimal.valueOf(4 + extraFare);
        }

        long extraStops = (long) stopCount - 24;
        long extraFare = (extraStops + 7) / 8;
        return BigDecimal.valueOf(6 + extraFare);
    }
}