/*
 * Copyright 2018-2023 Volkan Yazıcı <volkan@yazi.ci>
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

package com.vlkan.rfos;

import com.vlkan.rfos.policy.TimeBasedRotationPolicy;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;

public enum SchedulerShutdownTestApp {;

    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerShutdownTestApp.class);

    private static final class DelayedRotationPolicy extends TimeBasedRotationPolicy {

        private static final Logger LOGGER = LoggerFactory.getLogger(DelayedRotationPolicy.class);

        private final Queue<Duration> delays;

        private DelayedRotationPolicy(Long... delays) {
            this.delays = Arrays
                    .stream(delays)
                    .map(Duration::ofMillis)
                    .collect(Collectors.toCollection(LinkedList::new));
        }

        @Override
        public Instant getTriggerInstant(Clock clock) {
            Duration delay = delays.poll();
            Objects.requireNonNull(delay, "delay");
            LOGGER.info("setting trigger with delay {}", delay);
            return clock.now().plus(delay);
        }

        @Override
        protected Logger getLogger() {
            return LOGGER;
        }

    }

    public static void main(String[] args) throws IOException {

        // Determine file names.
        String filePrefix =
                RotatingFileOutputStream.class.getSimpleName()
                        + "-"
                        + SchedulerShutdownTestApp.class.getSimpleName();
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        File file = new File(tmpDir, filePrefix + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir, filePrefix + "-%d{yyyy}.log").getAbsolutePath();

        // Create the stream config.
        long rotationDelay1Millis = 500L;
        long rotationDelay2Millis = 5L * 60L * 1_000L;  // 5 minutes
        DelayedRotationPolicy policy = new DelayedRotationPolicy(rotationDelay1Millis, rotationDelay2Millis);
        RotationCallback callback = Mockito.spy(LoggingRotationCallback.getInstance());
        RotationConfig config = RotationConfig
                .builder()
                .file(fileName)
                .filePattern(fileNamePattern)
                .policy(policy)
                .callback(callback)
                .build();

        // Create the stream.
        LOGGER.info("creating the stream");
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Write something to stream to avoid rotation being skipped.
        stream.write(filePrefix.getBytes(StandardCharsets.US_ASCII));

        // Verify the 1st rotation.
        LOGGER.info("verifying the 1st rotation");
        long expectedRotationDelay1Millis1 = Math.addExact(
                rotationDelay1Millis,
                /* extra threshold */ 1_000L);
        Mockito
                .verify(callback, Mockito.timeout(expectedRotationDelay1Millis1))
                .onTrigger(Mockito.eq(policy), Mockito.any(Instant.class));

        // Close the stream.
        LOGGER.info("closing stream");
        stream.close();
        LOGGER.info("closed stream");

    }

}
