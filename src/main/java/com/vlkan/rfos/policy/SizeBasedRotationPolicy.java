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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

public class SizeBasedRotationPolicy implements RotationPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(SizeBasedRotationPolicy.class);

    private final long maxByteCount;

    private Rotatable rotatable;

    public SizeBasedRotationPolicy(long maxByteCount) {
        if (maxByteCount < 1) {
            String message = String.format("invalid size {maxByteCount=%d}", maxByteCount);
            throw new IllegalArgumentException(message);
        }
        this.maxByteCount = maxByteCount;
    }

    public long getMaxByteCount() {
        return maxByteCount;
    }

    @Override
    public boolean isWriteSensitive() {
        return true;
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
    }

    private void rotate(Instant instant, long byteCount, Rotatable rotatable) {
        LOGGER.debug("triggering {byteCount={}}", byteCount);
        rotatable.rotate(this, instant);
    }

    @Override
    public boolean equals(Object instance) {
        if (this == instance) return true;
        if (instance == null || getClass() != instance.getClass()) return false;
        SizeBasedRotationPolicy that = (SizeBasedRotationPolicy) instance;
        return maxByteCount == that.maxByteCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxByteCount);
    }

    @Override
    public String toString() {
        return String.format("SizeBasedRotationPolicy{maxByteCount=%d}", maxByteCount);
    }

}
