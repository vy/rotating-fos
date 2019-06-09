package com.vlkan.rfos;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;

public class SystemClock implements Clock {

    private static final SystemClock INSTANCE = new SystemClock();

    protected SystemClock() {
        // Do nothing.
    }

    public static SystemClock getInstance() {
        return INSTANCE;
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public Instant midnight() {
        return midnight(currentDateTime().plus(Duration.ofDays(1)));
    }

    @Override
    public Instant sundayMidnight() {
        Instant today = currentDateTime();
        int dayIndex = today.get(ChronoField.DAY_OF_WEEK) - 1;
        int dayOffset = 7 - dayIndex;
        Instant monday = today.plus(Duration.ofDays(dayOffset));
        return midnight(monday);
    }

    protected Instant midnight(Instant instant) {
        return instant.with(ChronoField.NANO_OF_DAY, 0) ;
    }

    protected Instant currentDateTime() {
        return Instant.now();
    }

}
