package com.vlkan.rfos.policy;

import com.vlkan.rfos.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class DailyRotationPolicy extends TimeBasedRotationPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyRotationPolicy.class);

    private static final DailyRotationPolicy INSTANCE = new DailyRotationPolicy();

    private DailyRotationPolicy() {
        // Do nothing.
    }

    public static DailyRotationPolicy getInstance() {
        return INSTANCE;
    }

    @Override
    public Instant getTriggerDateTime(Clock clock) {
        return clock.midnight();
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    @Override
    public String toString() {
        return "DailyRotationPolicy";
    }

}
