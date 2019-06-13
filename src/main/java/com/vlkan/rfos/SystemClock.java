package com.vlkan.rfos;

import java.time.*;

public class SystemClock implements Clock {

    private static final SystemClock INSTANCE = new SystemClock();

    private static final ZoneId UTC_ZONE_ID = ZoneId.of("UTC");

    SystemClock() {
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
        Instant instant = now();
        ZonedDateTime utcInstant = instant.atZone(UTC_ZONE_ID);
        return LocalDate
                .from(utcInstant)
                .atStartOfDay()
                .plusDays(1)
                .toInstant(ZoneOffset.UTC);
    }

    @Override
    public Instant sundayMidnight() {
        Instant instant = now();
        ZonedDateTime utcInstant = instant.atZone(UTC_ZONE_ID);
        LocalDateTime todayStart = LocalDate.from(utcInstant).atStartOfDay();
        int todayIndex = todayStart.getDayOfWeek().getValue() - 1;
        int sundayOffset = 7 - todayIndex;
        return todayStart
                .plusDays(sundayOffset)
                .toInstant(ZoneOffset.UTC);
    }

}
