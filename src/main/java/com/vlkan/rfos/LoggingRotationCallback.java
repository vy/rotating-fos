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
    public void onTrigger(RotationPolicy policy, Instant instant) {
        LOGGER.debug("rotation trigger {policy={}, instant={}}", policy, instant);
    }

    @Override
    public void onSuccess(RotationPolicy policy, Instant instant, File file) {
        LOGGER.debug("rotation success {policy={}, instant={}, file={}}", policy, instant, file);
    }

    @Override
    public void onFailure(RotationPolicy policy, Instant instant, File file, Exception error) {
        String message = String.format("rotation failure {policy=%s, instant=%s, file=%s}", policy, instant, file);
        LOGGER.error(message, error);
    }

}
