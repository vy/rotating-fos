/*
 * Copyright 2018-2024 Volkan Yazıcı <volkan@yazi.ci>
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

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public class ByteMatchingRotationPolicy implements RotationPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(SizeBasedRotationPolicy.class);

    private final byte targetByte;

    private final int maxOccurrenceCount;

    private long occurrenceCount;

    private Rotatable rotatable;

    public ByteMatchingRotationPolicy(byte targetByte, int maxOccurrenceCount) {
        if (maxOccurrenceCount < 1) {
            String message = String.format("invalid count {maxOccurrenceCount=%d}", maxOccurrenceCount);
            throw new IllegalArgumentException(message);
        }
        this.targetByte = targetByte;
        this.maxOccurrenceCount = maxOccurrenceCount;
    }

    @Override
    public void start(Rotatable rotatable) {
        this.rotatable = rotatable;
        occurrenceCount = countOccurrences(rotatable.getConfig().getFile());
        if (occurrenceCount > 0) {
            LOGGER.debug("starting with non-zero line count {lineCount={}}", occurrenceCount);
        }

    }

    private long countOccurrences(File file) {
        // No need to check if file exists, since policies get started after opening the file.
        final Path path = file.toPath();
        try (InputStream inputStream = Files.newInputStream(path)) {
            long lineCount = 0;
            byte[] buffer = new byte[8192];
            for (;;) {
                int length = inputStream.read(buffer);
                if (length < 0) {
                    break;
                }
                for (int i = 0; i < length; i++) {
                    if (buffer[i] == targetByte) {
                        lineCount++;
                    }
                }
            }
            return lineCount;
        } catch (Exception error) {
            final String message = String.format("read failure {file=%s}", file);
            throw new RuntimeException(message, error);
        }
    }

    /**
     * @return {@code true}, always.
     */
    @Override
    public boolean isWriteSensitive() {
        return true;
    }

    @Override
    public void acceptWrite(int b) {
        if (b == targetByte) {
            ++occurrenceCount;
            rotateIfNecessary();
        }
    }

    @Override
    public void acceptWrite(byte[] buf) {
        int matchCount = 0;
        for (byte b : buf) {
            if (b == targetByte) {
                ++matchCount;
            }
        }
        occurrenceCount += matchCount;
        rotateIfNecessary();
    }

    @Override
    public void acceptWrite(byte[] buf, int off, int len) {
        int matchCount = 0;
        int maxIdx = off + len;
        for (int idx = off; idx < maxIdx; idx++) {
            if (buf[idx] == targetByte) {
                ++matchCount;
            }
        }
        occurrenceCount += matchCount;
        rotateIfNecessary();
    }

    private void rotateIfNecessary() {
        if (occurrenceCount >= maxOccurrenceCount) {
            LOGGER.debug("triggering {occurrenceCount={}}", occurrenceCount);
            Instant instant = rotatable.getConfig().getClock().now();
            rotatable.rotate(this, instant);
            occurrenceCount = 0;
        }
    }

    @Override
    public boolean equals(Object instance) {
        if (this == instance) return true;
        if (instance == null || getClass() != instance.getClass()) {
            return false;
        }
        ByteMatchingRotationPolicy policy = (ByteMatchingRotationPolicy) instance;
        return maxOccurrenceCount == policy.maxOccurrenceCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxOccurrenceCount);
    }

    @Override
    public String toString() {
        return String.format(
                "ByteMatchingRotationPolicy{targetByte=0x%X, maxOccurrenceCount=%d}",
                targetByte, maxOccurrenceCount);
    }

}
