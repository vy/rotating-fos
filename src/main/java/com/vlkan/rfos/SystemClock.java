package com.vlkan.rfos;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;

public class SystemClock implements Clock {

    private static final SystemClock INSTANCE = new SystemClock();

    protected SystemClock() {
        // Do nothing.
    }

    public static SystemClock getInstance() {
        return INSTANCE;
    }

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now();
    }

    @Override
    public LocalDateTime midnight() {
        return currentDateTime().plusDays(1).withTimeAtStartOfDay().toLocalDateTime();
    }

    @Override
    public LocalDateTime sundayMidnight() {
        DateTime today = currentDateTime();
        int dayIndex = today.getDayOfWeek() - 1;
        int dayOffset = 7 - dayIndex;
        DateTime monday = today.plusDays(dayOffset);
        DateTime mondayStart = monday.withTimeAtStartOfDay();
        return mondayStart.toLocalDateTime();
    }

    protected DateTime currentDateTime() {
        return DateTime.now();
    }

}
