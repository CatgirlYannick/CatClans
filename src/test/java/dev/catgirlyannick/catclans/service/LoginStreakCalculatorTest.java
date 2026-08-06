package dev.catgirlyannick.catclans.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginStreakCalculatorTest {

    private final LoginStreakCalculator calculator = new LoginStreakCalculator(
            new BigDecimal("25"),
            new BigDecimal("1.3"),
            10,
            new BigDecimal("0.8"),
            2,
            RoundingMode.HALF_UP
    );

    @Test
    void followsTheConfirmedEarlyAndLateStreakFormula() {
        assertEquals(new BigDecimal("25.00"), calculator.xpForDay(1));
        assertEquals(new BigDecimal("32.50"), calculator.xpForDay(2));
        assertEquals(new BigDecimal("65.00"), calculator.xpForDay(3));
        assertEquals(new BigDecimal("260.00"), calculator.xpForDay(9));
        assertEquals(new BigDecimal("262.08"), calculator.xpForDay(10));
        assertEquals(new BigDecimal("264.18"), calculator.xpForDay(11));
    }
}
