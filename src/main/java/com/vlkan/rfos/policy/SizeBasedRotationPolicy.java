/*
 * Copyright 2019 Volkan Yazıcı
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permits and
 * limitations under the License.
 */

package com.vlkan.rfos.policy;

import com.vlkan.rfos.Rotatable;
import com.vlkan.rfos.RotationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.TimerTask;

public class SizeBasedRotationPolicy implements RotationPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(SizeBasedRotationPolicy.class);

    private final long checkIntervalMillis;

    private final long maxByteCount;

    private Rotatable rotatable;

    public SizeBasedRotationPolicy(long checkIntervalMillis, long maxByteCount) {

        if (checkIntervalMillis < 0) {
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
    public boolean isWriteSensitive() {
        return checkIntervalMillis == 0;
    }

    @Override
    public void acceptWrite(long byteCount) {
        if (byteCount > maxByteCount) {
            Instant instant = rotatable.getConfig().getClock().now();
            rotate(instant, byteCount, rotatable);
        }
    }

    @Override
    public void start(Rotatable rotatable) {
        this.rotatable = rotatable;
        if (checkIntervalMillis > 0) {
            TimerTask timerTask = createTimerTask(rotatable);
            rotatable.getConfig().getTimer().schedule(timerTask, 0, checkIntervalMillis);
        }
    }

    private TimerTask createTimerTask(Rotatable rotatable) {
        RotationConfig config = rotatable.getConfig();
        return new TimerTask() {
            @Override
            public void run() {

                // Get file size.
                Instant instant = config.getClock().now();
                File file = config.getFile();
                long byteCount;
                try {
                    byteCount = file.length();
                } catch (Exception error) {
                    String message = String.format("failed accessing file size {file=%s}", file);
                    Exception extendedError = new IOException(message, error);
                    config.getCallback().onFailure(SizeBasedRotationPolicy.this, instant, file, extendedError);
                    return;
                }

                // Rotate if necessary.
                if (byteCount > maxByteCount) {
                    rotate(instant, byteCount, rotatable);
                }

            }
        };
    }

    private void rotate(Instant instant, long byteCount, Rotatable rotatable) {
        LOGGER.debug("triggering {byteCount={}}", byteCount);
        rotatable.getConfig().getCallback().onTrigger(SizeBasedRotationPolicy.this, instant);
        rotatable.rotate(SizeBasedRotationPolicy.this, instant);
    }

    @Override
    public boolean equals(Object instance) {
        if (this == instance) return true;
        if (instance == null || getClass() != instance.getClass()) return false;
        SizeBasedRotationPolicy that = (SizeBasedRotationPolicy) instance;
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
