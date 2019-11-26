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

import com.vlkan.rfos.policy.SizeBasedRotationPolicy;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
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

    private void test_write_insensitive_policy(boolean compress) throws Exception {

        // Set file names.
        String className = RotatingFileOutputStream.class.getSimpleName();
        File file = new File(tmpDir.getRoot(), className + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir.getRoot(), className + "-%d{yyyy}.log").getAbsolutePath();
        String rotatedFileNameSuffix = compress ? ".gz" : "";
        File rotatedFile = new File(fileNamePattern.replace(
                "%d{yyyy}",
                String.valueOf(Calendar.getInstance().get(Calendar.YEAR))) + rotatedFileNameSuffix);

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
        RecordingRotationCallback callback = new RecordingRotationCallback(3);
        RotationConfig config = RotationConfig
                .builder()
                .compress(compress)
                .file(fileName)
                .filePattern(fileNamePattern)
                .timer(timer)
                .policy(policy)
                .callback(callback)
                .build();
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Verify the initial file open.
        RecordingRotationCallback.CallContext callbackCallContext0 = callback
                .receivedCallContexts
                .poll(1, TimeUnit.SECONDS);
        assertThat(callbackCallContext0).isInstanceOf(RecordingRotationCallback.OnOpenContext.class);

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
        RecordingRotationCallback.CallContext callbackCallContext1 = callback.receivedCallContexts.peek();
        assertThat(callbackCallContext1).isNull();

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
        RecordingRotationCallback.CallContext callbackCallContext2 = callback.receivedCallContexts.peek();
        assertThat(callbackCallContext2).isNull();

        // Push the file size off the threshold.
        LOGGER.trace("writing more bytes to file");
        stream.write(maxByteCount);
        stream.flush();
        assertThat(file.length()).isEqualTo(maxByteCount + 1);

        // Allow timer to proceed.
        LOGGER.trace("pushing task execution permit");
        timerTaskExecutionPermits.put(3L);

        // Verify timer task is executed.
        Integer timerTaskExecutionCount3 = timerTaskExecutionCounts.poll(1, TimeUnit.SECONDS);
        assertThat(timerTaskExecutionCount3).isEqualTo(3);

        // Verify the rotation trigger.
        RecordingRotationCallback.CallContext callbackCallContext3 = callback
                .receivedCallContexts
                .poll(1, TimeUnit.SECONDS);
        assertThat(callbackCallContext3).isInstanceOf(RecordingRotationCallback.OnTriggerContext.class);

        // Verify the rotation file open.
        RecordingRotationCallback.CallContext callbackCallContext4 = callback
                .receivedCallContexts
                .poll(1, TimeUnit.SECONDS);
        assertThat(callbackCallContext4).isInstanceOf(RecordingRotationCallback.OnOpenContext.class);

        // Verify the rotation.
        RecordingRotationCallback.OnSuccessContext callbackCallContext5 =
                (RecordingRotationCallback.OnSuccessContext) callback
                        .receivedCallContexts
                        .poll(1, TimeUnit.SECONDS);
        assertThat(callbackCallContext5).isNotNull();
        assertThat(callbackCallContext5.policy).isEqualTo(policy);
        assertThat(callbackCallContext5.file).isEqualTo(rotatedFile);
        long callbackSuccessFile3Length = callbackCallContext5.file.length();
        if (compress) {
            assertThat(callbackSuccessFile3Length).isGreaterThan(0);
        } else {
            assertThat(callbackSuccessFile3Length).isEqualTo(maxByteCount + 1);
        }
        assertThat(file.length()).isEqualTo(0);

        // Verify the callback queue is drained.
        RecordingRotationCallback.CallContext callbackCallContext6 = callback.receivedCallContexts.peek();
        assertThat(callbackCallContext6).isNull();

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
        RecordingRotationCallback callback = new RecordingRotationCallback(3);
        Timer timer = Mockito.mock(Timer.class);
        RotationConfig config = RotationConfig
                .builder()
                .file(fileName)
                .filePattern(fileNamePattern)
                .timer(timer)
                .policy(policy)
                .callback(callback)
                .build();
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Verify the initial file open.
        RecordingRotationCallback.CallContext callbackCallContext0 = callback
                .receivedCallContexts
                .poll(1, TimeUnit.SECONDS);
        assertThat(callbackCallContext0).isInstanceOf(RecordingRotationCallback.OnOpenContext.class);

        // Verify no rotations so far.
        RecordingRotationCallback.CallContext callbackCallContext1 = callback.receivedCallContexts.peek();
        assertThat(callbackCallContext1).isNull();

        // Increase the size of the file just to the edge.
        LOGGER.trace("writing to file");
        for (int byteIndex = 0; byteIndex < maxByteCount; byteIndex++) {
            stream.write(byteIndex);
        }
        stream.flush();
        assertThat(file.length()).isEqualTo(maxByteCount);

        // Verify no rotations so far.
        RecordingRotationCallback.CallContext callbackCallContext2 = callback.receivedCallContexts.peek();
        assertThat(callbackCallContext2).isNull();

        // Push the file size off the threshold.
        LOGGER.trace("writing more bytes to file");
        stream.write(maxByteCount);

        // Verify the rotation trigger.
        RecordingRotationCallback.CallContext callbackCallContext3 = callback
                .receivedCallContexts
                .poll(1, TimeUnit.SECONDS);
        assertThat(callbackCallContext3).isInstanceOf(RecordingRotationCallback.OnTriggerContext.class);

        // Verify the rotation file open.
        RecordingRotationCallback.CallContext callbackCallContext4 = callback
                .receivedCallContexts
                .poll(1, TimeUnit.SECONDS);
        assertThat(callbackCallContext4).isInstanceOf(RecordingRotationCallback.OnOpenContext.class);

        // Verify the rotation.
        RecordingRotationCallback.OnSuccessContext callbackCallContext5 =
                (RecordingRotationCallback.OnSuccessContext) callback
                        .receivedCallContexts
                        .poll(1, TimeUnit.SECONDS);
        assertThat(callbackCallContext5).isNotNull();
        assertThat(callbackCallContext5.policy).isEqualTo(policy);
        assertThat(callbackCallContext5.file).isEqualTo(rotatedFile);
        long callbackSuccessFile3Length = callbackCallContext5.file.length();
        assertThat(callbackSuccessFile3Length).isEqualTo(maxByteCount);
        assertThat(file.length()).isEqualTo(1);

        // Verify the callback queue is drained.
        RecordingRotationCallback.CallContext callbackCallContext6 = callback.receivedCallContexts.peek();
        assertThat(callbackCallContext6).isNull();

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
        RecordingRotationCallback callback = new RecordingRotationCallback(1);
        Timer timer = Mockito.mock(Timer.class);
        RotationConfig config = RotationConfig
                .builder()
                .file(fileName)
                .filePattern(fileNamePattern)
                .timer(timer)
                .policy(policy)
                .callback(callback)
                .build();
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Verify the initial file open.
        RecordingRotationCallback.CallContext callbackCallContext0 = callback
                .receivedCallContexts
                .poll(1, TimeUnit.SECONDS);
        assertThat(callbackCallContext0).isInstanceOf(RecordingRotationCallback.OnOpenContext.class);

        // Verify no rotations so far.
        RecordingRotationCallback.CallContext callbackCallContext1 = callback.receivedCallContexts.peek();
        assertThat(callbackCallContext1).isNull();

        // Write payload with size exceeding the threshold.
        LOGGER.trace("writing to file");
        byte[] payload = new byte[2 * maxByteCount];
        for (int byteIndex = 0; byteIndex < payload.length; byteIndex++) {
            payload[byteIndex] = (byte) byteIndex;
        }
        stream.write(payload);
        stream.flush();
        assertThat(file.length()).isEqualTo(payload.length);

        // Verify the rotation trigger.
        RecordingRotationCallback.CallContext callbackCallContext2 = callback
                .receivedCallContexts
                .poll(1, TimeUnit.SECONDS);
        assertThat(callbackCallContext2).isInstanceOf(RecordingRotationCallback.OnTriggerContext.class);

        // Verify the rotation skip.
        RecordingRotationCallback.CallContext callbackCallContext3 = callback.receivedCallContexts.peek();
        assertThat(callbackCallContext3).isNull();

    }

    @Test
    public void test_adding_file_header() throws IOException {

        // Set file names.
        String className = RotatingFileOutputStream.class.getSimpleName();
        File file = new File(tmpDir.getRoot(), className + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir.getRoot(), className + "-%d{yyyy}.log").getAbsolutePath();
        File rotatedFile = new File(fileNamePattern.replace("%d{yyyy}", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))));

        RotationCallback callback = Mockito.spy(LoggingRotationCallback.getInstance());
        InOrder callbackInOrder = Mockito.inOrder(callback);

        // Create the stream config.
        int maxByteCount = 1024;
        SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy(0, maxByteCount);
        Timer timer = Mockito.mock(Timer.class);
        RotationConfig config = RotationConfig
                .builder()
                .file(fileName)
                .filePattern(fileNamePattern)
                .timer(timer)
                .policy(policy)
                .callback(callback)
                .build();

        // Create the 1st header injector.
        byte[] header1 = {1, 2, 3, 4};
        Mockito
                .doAnswer(invocation -> {
                    LOGGER.trace("injecting the 1st header");
                    OutputStream outputStream = invocation.getArgument(2);
                    outputStream.write(header1);
                    outputStream.flush();
                    return null;
                })
                .when(callback)
                .onOpen(Mockito.isNull(),
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));

        // Create the 2nd header injector.
        byte[] header2 = {5, 6, 7, 8};
        Mockito
                .doAnswer(invocation -> {
                    LOGGER.trace("injecting the 2nd header");
                    OutputStream outputStream = invocation.getArgument(2);
                    outputStream.write(header2);
                    outputStream.flush();
                    return null;
                })
                .when(callback)
                .onOpen(Mockito.same(policy),
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));

        // Create the stream.
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Verify the initial file open.
        callbackInOrder
                .verify(callback)
                .onOpen(Mockito.isNull(),
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));

        // Write the 1st payload, should not exceed the size limit.
        LOGGER.trace("writing the 1st payload");
        byte[] payload1 = new byte[maxByteCount - header1.length];
        for (int byteIndex = 0; byteIndex < payload1.length; byteIndex++) {
            payload1[byteIndex] = (byte) (byteIndex % Byte.MAX_VALUE);
        }
        stream.write(payload1);

        // Write the 2nd payload.
        LOGGER.trace("writing the 2nd payload");
        byte[] payload2 = {9};
        stream.write(payload2);
        stream.flush();

        // Verify the rotation trigger.
        callbackInOrder
                .verify(callback)
                .onTrigger(Mockito.same(policy), Mockito.any(Instant.class));

        // Verify the rotation file open.
        callbackInOrder
                .verify(callback)
                .onOpen(Mockito.same(policy),
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));

        // Verify the rotation.
        Mockito
                .verify(callback)
                .onSuccess(
                        Mockito.same(policy),
                        Mockito.any(Instant.class),
                        Mockito.eq(rotatedFile));

        // Verify the rotated file.
        long rotatedFileLength = rotatedFile.length();
        Assertions.assertThat(rotatedFileLength).isEqualTo(maxByteCount);
        byte[] rotatedFileBytes = new byte[Math.toIntExact(rotatedFileLength)];
        try (FileInputStream rotatedFileInputStream = new FileInputStream(rotatedFile)) {
            int readRotatedFileByteCount = rotatedFileInputStream.read(rotatedFileBytes);
            Assertions.assertThat(readRotatedFileByteCount).isEqualTo(rotatedFileBytes.length);
        }
        byte[] expectedRotatedFileBytes = new byte[maxByteCount];
        System.arraycopy(header1, 0, expectedRotatedFileBytes, 0, header1.length);
        System.arraycopy(payload1, 0, expectedRotatedFileBytes, header1.length, payload1.length);
        Assertions.assertThat(rotatedFileBytes).isEqualTo(expectedRotatedFileBytes);

        // Verify the re-opened file.
        long reopenedFileLength = file.length();
        Assertions.assertThat(reopenedFileLength).isEqualTo(header2.length + payload2.length);
        byte[] reopenedFileBytes = new byte[Math.toIntExact(reopenedFileLength)];
        try (FileInputStream reopenedFileInputStream = new FileInputStream(file)) {
            int readReopenedFileByteCount = reopenedFileInputStream.read(reopenedFileBytes);
            Assertions.assertThat(readReopenedFileByteCount).isEqualTo(reopenedFileBytes.length);
        }
        byte[] expectedReopenedFileBytes = new byte[header2.length + payload2.length];
        System.arraycopy(header2, 0, expectedReopenedFileBytes, 0, header2.length);
        System.arraycopy(payload2, 0, expectedReopenedFileBytes, header2.length, payload2.length);
        Assertions.assertThat(reopenedFileBytes).isEqualTo(expectedReopenedFileBytes);

    }

}
