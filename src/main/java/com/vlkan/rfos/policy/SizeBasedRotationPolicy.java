package com.vlkan.rfos.policy;

import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.TimerTask;

public class SizeBasedRotationPolicy implements RotationPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(SizeBasedRotationPolicy.class);

    private final long checkIntervalMillis;

    private final long maxByteCount;

    public SizeBasedRotationPolicy(long checkIntervalMillis, long maxByteCount) {

        if (checkIntervalMillis < 1) {
            String message = String.format("invalid interval {checkIntervalMillis=%d}", checkIntervalMillis);
            throw new IllegalArgumentException(message);
        }
        this.checkIntervalMillis = checkIntervalMillis;

        if (maxByteCount < 1) {
            String message = String.format("invalid size {maxByteCount=%d}", maxByteCount);
            throw new IllegalArgumentException(message);
        }
        this.maxByteCount = maxByteCount;

    }

    public long getCheckIntervalMillis() {
        return checkIntervalMillis;
    }

    public long getMaxByteCount() {
        return maxByteCount;
    }

    @Override
    public void start(final RotationPolicyContext context) {
        TimerTask timerTask = createTimerTask(context);
        context.getTimer().schedule(timerTask, 0, checkIntervalMillis);
    }

    private TimerTask createTimerTask(final RotationPolicyContext context) {
        return new TimerTask() {
            @Override
            public void run() {

                // Get file size.
                LocalDateTime now = context.getClock().now();
                File file = context.getFile();
                long byteCount;
                try {
                    byteCount = file.length();
                } catch (Exception error) {
                    String message = String.format("failed accessing file size (file=%s)", file);
                    Exception extendedError = new IOException(message, error);
                    context.getCallback().onFailure(SizeBasedRotationPolicy.this, now, file, extendedError);
                    return;
                }

                // Rotate if necessary.
                if (byteCount > maxByteCount) {
                    LOGGER.debug("triggering {byteCount={}}", byteCount);
                    context.getRotatable().rotate(SizeBasedRotationPolicy.this, now, context.getCallback());
                }

            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SizeBasedRotationPolicy that = (SizeBasedRotationPolicy) o;
        return checkIntervalMillis == that.checkIntervalMillis && maxByteCount == that.maxByteCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(checkIntervalMillis, maxByteCount);
    }

    @Override
    public String toString() {
        return String.format(
                "SizeBasedRotationPolicy{checkIntervalMillis=%d, maxByteCount=%d}",
                checkIntervalMillis, maxByteCount);
    }

}
