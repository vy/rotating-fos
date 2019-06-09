package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;

public class LoggingRotationCallback implements RotationCallback {

    private static final LoggingRotationCallback INSTANCE = new LoggingRotationCallback();

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingRotationCallback.class);

    private LoggingRotationCallback() {
        // Do nothing.
    }

    public static LoggingRotationCallback getInstance() {
        return INSTANCE;
    }

    @Override
    public void onTrigger(RotationPolicy policy, Instant dateTime) {
        LOGGER.debug("rotation trigger {policy={}, dateTime={}}", policy, dateTime);
    }

    @Override
    public void onSuccess(RotationPolicy policy, Instant dateTime, File file) {
        LOGGER.debug("rotation success {policy={}, dateTime={}, file={}}", policy, dateTime, file);
    }

    @Override
    public void onFailure(RotationPolicy policy, Instant dateTime, File file, Exception error) {
        String message = String.format("rotation failure {policy=%s, dateTime=%s, file=%s}", policy, dateTime, file);
        LOGGER.error(message, error);
    }

}
