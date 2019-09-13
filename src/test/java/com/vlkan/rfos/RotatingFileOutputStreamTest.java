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

package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;
import com.vlkan.rfos.policy.SizeBasedRotationPolicy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RotatingFileOutputStreamTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RotatingFileOutputStreamTest.class);

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Test
    public void test_write_insensitive_policy() throws Exception {
        test_write_insensitive_policy(false);
    }

    @Test
    public void test_write_insensitive_policy_with_compression() throws Exception {
        test_write_insensitive_policy(true);
    }

    private static final class RotationCallbackRecorder {

        private final BlockingQueue<RotationPolicy> callbackSuccessPolicies = new LinkedBlockingDeque<>(1);

        private final BlockingQueue<File> callbackSuccessFiles = new LinkedBlockingDeque<>(1);

        private final RotationCallback callback = new RotationCallback() {

            @Override
            public void onTrigger(RotationPolicy policy, Instant instant) {
                LOGGER.trace("onTrigger({}, {})", policy, instant);
            }

            @Override
            public void onSuccess(RotationPolicy policy, Instant instant, File file) {
                LOGGER.trace("onSuccess({}, {}, {})", policy, instant, file);
                try {
                    callbackSuccessPolicies.put(policy);
                    callbackSuccessFiles.put(file);
                } catch (InterruptedException ignored) {
                    LOGGER.warn("onSuccess() is interrupted");
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public void onFailure(RotationPolicy policy, Instant instant, File file, Exception error) {
                LOGGER.trace("onFailure({}, {}, {}, {})", policy, instant, file, error);
            }

        };

    }

    private void test_write_insensitive_policy(boolean compress) throws Exception {

        // Set file names.
        String className = RotatingFileOutputStream.class.getSimpleName();
        File file = new File(tmpDir.getRoot(), className + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir.getRoot(), className + "-%d{yyyy}.log").getAbsolutePath();
        String rotatedFileNameSuffix = compress ? ".gz" : "";
        File rotatedFile = new File(fileNamePattern.replace("%d{yyyy}", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))) + rotatedFileNameSuffix);

        // Create the timer which is advanced by permits in a queue.
        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        BlockingQueue<Object> timerTaskExecutionPermits = new LinkedBlockingDeque<>();
        BlockingQueue<Long> timerDelays = new LinkedBlockingDeque<>(1);
        BlockingQueue<Long> timerPeriods = new LinkedBlockingDeque<>(1);
        BlockingQueue<Integer> timerTaskExecutionCounts = new LinkedBlockingDeque<>(1);
        Timer timer = new Timer() {
            @Override
            public void schedule(TimerTask task, long delay, long period) {
                new Thread(() -> {
                    int executionCount = 0;
                    boolean first = true;
                    Thread thread = Thread.currentThread();
                    while (true) {

                        LOGGER.trace("awaiting task execution permit");
                        try {
                            timerTaskExecutionPermits.poll(1, TimeUnit.SECONDS);
                        } catch (InterruptedException ignored) {
                            LOGGER.warn("task execution permit await is interrupted");
                            thread.interrupt();
                        }

                        if (first) {
                            LOGGER.trace("executing task for the first time");
                            first = false;
                            try {
                                timerDelays.put(delay);
                                timerPeriods.put(period);
                            } catch (InterruptedException ignored) {
                                LOGGER.warn("timer delay & period push is interrupted");
                                thread.interrupt();
                            }
                        } else {
                            LOGGER.trace("executing task");
                        }
                        task.run();

                        LOGGER.trace("pushing task execution count");
                        try {
                            timerTaskExecutionCounts.put(++executionCount);
                        } catch (InterruptedException ignored) {
                            LOGGER.warn("timer task execution count push is interrupted");
                            thread.interrupt();
                        }

                    }
                }).start();
            }
        };

        // Create the stream.
        int checkIntervalMillis = 50;
        int maxByteCount = 1024;
        SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy(checkIntervalMillis, maxByteCount);
        RotationCallbackRecorder callbackRecorder = new RotationCallbackRecorder();
        RotationConfig config = RotationConfig
                .builder()
                .compress(compress)
                .file(fileName)
                .filePattern(fileNamePattern)
                .timer(timer)
                .policy(policy)
                .callback(callbackRecorder.callback)
                .build();
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Allow timer to proceed.
        LOGGER.trace("pushing task execution permit");
        timerTaskExecutionPermits.put(1L);

        // Verify the timer call.
        Long timerDelay = timerDelays.poll(1, TimeUnit.SECONDS);
        assertThat(timerDelay).isEqualTo(0L);
        Long timerPeriod = timerPeriods.poll(1, TimeUnit.SECONDS);
        assertThat(timerPeriod).isEqualTo(checkIntervalMillis);

        // Verify timer task is executed.
        Integer timerTaskExecutionCount1 = timerTaskExecutionCounts.poll(1, TimeUnit.SECONDS);
        assertThat(timerTaskExecutionCount1).isEqualTo(1);

        // Verify no rotations so far.
        RotationPolicy callbackSuccessPolicy1 = callbackRecorder.callbackSuccessPolicies.peek();
        assertThat(callbackSuccessPolicy1).isNull();
        File callbackSuccessFile1 = callbackRecorder.callbackSuccessFiles.peek();
        assertThat(callbackSuccessFile1).isNull();

        // Increase the size of the file just to the edge.
        LOGGER.trace("writing to file");
        for (int byteIndex = 0; byteIndex < maxByteCount; byteIndex++) {
            stream.write(byteIndex);
        }
        stream.flush();
        assertThat(file.length()).isEqualTo(maxByteCount);

        // Allow timer to proceed.
        LOGGER.trace("pushing task execution permit");
        timerTaskExecutionPermits.put(2L);

        // Verify timer task is executed.
        Integer timerTaskExecutionCount2 = timerTaskExecutionCounts.poll(1, TimeUnit.SECONDS);
        assertThat(timerTaskExecutionCount2).isEqualTo(2);

        // Verify no rotations so far.
        RotationPolicy callbackSuccessPolicy2 = callbackRecorder.callbackSuccessPolicies.peek();
        assertThat(callbackSuccessPolicy2).isNull();
        File callbackSuccessFile2 = callbackRecorder.callbackSuccessFiles.peek();
        assertThat(callbackSuccessFile2).isNull();

        // Push the file size off the threshold.
        LOGGER.trace("writing more byte to file");
        stream.write(maxByteCount);
        stream.flush();
        assertThat(file.length()).isEqualTo(maxByteCount + 1);

        // Allow timer to proceed.
        LOGGER.trace("pushing task execution permit");
        timerTaskExecutionPermits.put(3L);

        // Verify timer task is executed.
        Integer timerTaskExecutionCount3 = timerTaskExecutionCounts.poll(1, TimeUnit.SECONDS);
        assertThat(timerTaskExecutionCount3).isEqualTo(3);

        // Verify the rotation.
        RotationPolicy callbackSuccessPolicy3 = callbackRecorder.callbackSuccessPolicies.poll(1, TimeUnit.SECONDS);
        assertThat(callbackSuccessPolicy3).isEqualTo(policy);
        File callbackSuccessFile3 = callbackRecorder.callbackSuccessFiles.poll(1, TimeUnit.SECONDS);
        assertThat(callbackSuccessFile3).isNotNull().isEqualTo(rotatedFile);
        long callbackSuccessFile3Length = callbackSuccessFile3.length();
        if (compress) {
            assertThat(callbackSuccessFile3Length).isGreaterThan(0);
        } else {
            assertThat(callbackSuccessFile3Length).isEqualTo(maxByteCount + 1);
        }
        assertThat(file.length()).isEqualTo(0);

    }

    @Test
    public void test_write_sensitive_policy() throws Exception {

        // Set file names.
        String className = RotatingFileOutputStream.class.getSimpleName();
        File file = new File(tmpDir.getRoot(), className + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir.getRoot(), className + "-%d{yyyy}.log").getAbsolutePath();
        File rotatedFile = new File(fileNamePattern.replace("%d{yyyy}", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))));

        // Create the stream.
        int maxByteCount = 1024;
        SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy(0, maxByteCount);
        RotationCallbackRecorder callbackRecorder = new RotationCallbackRecorder();
        Timer timer = Mockito.mock(Timer.class);
        RotationConfig config = RotationConfig
                .builder()
                .file(fileName)
                .filePattern(fileNamePattern)
                .timer(timer)
                .policy(policy)
                .callback(callbackRecorder.callback)
                .build();
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Verify no rotations so far.
        RotationPolicy callbackSuccessPolicy1 = callbackRecorder.callbackSuccessPolicies.peek();
        assertThat(callbackSuccessPolicy1).isNull();
        File callbackSuccessFile1 = callbackRecorder.callbackSuccessFiles.peek();
        assertThat(callbackSuccessFile1).isNull();

        // Increase the size of the file just to the edge.
        LOGGER.trace("writing to file");
        for (int byteIndex = 0; byteIndex < maxByteCount; byteIndex++) {
            stream.write(byteIndex);
        }
        stream.flush();
        assertThat(file.length()).isEqualTo(maxByteCount);

        // Verify no rotations so far.
        RotationPolicy callbackSuccessPolicy2 = callbackRecorder.callbackSuccessPolicies.peek();
        assertThat(callbackSuccessPolicy2).isNull();
        File callbackSuccessFile2 = callbackRecorder.callbackSuccessFiles.peek();
        assertThat(callbackSuccessFile2).isNull();

        // Push the file size off the threshold.
        LOGGER.trace("writing more byte to file");
        stream.write(maxByteCount);

        // Verify the rotation.
        RotationPolicy callbackSuccessPolicy3 = callbackRecorder.callbackSuccessPolicies.poll(1, TimeUnit.SECONDS);
        assertThat(callbackSuccessPolicy3).isEqualTo(policy);
        File callbackSuccessFile3 = callbackRecorder.callbackSuccessFiles.poll(1, TimeUnit.SECONDS);
        assertThat(callbackSuccessFile3).isNotNull().isEqualTo(rotatedFile);
        long callbackSuccessFile3Length = callbackSuccessFile3.length();
        assertThat(callbackSuccessFile3Length).isEqualTo(maxByteCount);
        assertThat(file.length()).isEqualTo(1);

    }

    @Test
    public void test_empty_files_are_not_rotated() throws Exception {

        // Set file names.
        String className = RotatingFileOutputStream.class.getSimpleName();
        File file = new File(tmpDir.getRoot(), className + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir.getRoot(), className + "-%d{yyyy}.log").getAbsolutePath();

        // Create the stream.
        int maxByteCount = 1024;
        SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy(0, maxByteCount);
        RotationCallbackRecorder callbackRecorder = new RotationCallbackRecorder();
        Timer timer = Mockito.mock(Timer.class);
        RotationConfig config = RotationConfig
                .builder()
                .file(fileName)
                .filePattern(fileNamePattern)
                .timer(timer)
                .policy(policy)
                .callback(callbackRecorder.callback)
                .build();
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Verify no rotations so far.
        RotationPolicy callbackSuccessPolicy1 = callbackRecorder.callbackSuccessPolicies.peek();
        assertThat(callbackSuccessPolicy1).isNull();
        File callbackSuccessFile1 = callbackRecorder.callbackSuccessFiles.peek();
        assertThat(callbackSuccessFile1).isNull();

        // Write payload with size exceeding the threshold.
        LOGGER.trace("writing to file");
        byte[] payload = new byte[2 * maxByteCount];
        for (int byteIndex = 0; byteIndex < payload.length; byteIndex++) {
            payload[byteIndex] = (byte) byteIndex;
        }
        stream.write(payload);
        stream.flush();
        assertThat(file.length()).isEqualTo(payload.length);

        // Verify no rotations so far.
        RotationPolicy callbackSuccessPolicy2 = callbackRecorder.callbackSuccessPolicies.peek();
        assertThat(callbackSuccessPolicy2).isNull();
        File callbackSuccessFile2 = callbackRecorder.callbackSuccessFiles.peek();
        assertThat(callbackSuccessFile2).isNull();

    }

}
