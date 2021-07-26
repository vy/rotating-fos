/*
 * Copyright 2018-2021 Volkan Yazıcı
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

import com.vlkan.rfos.policy.DailyRotationPolicy;
import com.vlkan.rfos.policy.RotationPolicy;
import com.vlkan.rfos.policy.SizeBasedRotationPolicy;
import com.vlkan.rfos.policy.WeeklyRotationPolicy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

class RotatingFileOutputStreamTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RotatingFileOutputStreamTest.class);

    @TempDir
    File tmpDir;

    private ScheduledExecutorService executorService;

    @BeforeEach
    void setupExecutorService() {
        executorService = new ScheduledThreadPoolExecutor(2);
    }

    @AfterEach
    void stopExecutorService() {
        executorService.shutdownNow();
    }

    @Test
    void test_write_insensitive_policy() throws Exception {
        test_write_insensitive_policy(false);
    }

    @Test
    void test_write_insensitive_policy_with_compression() throws Exception {
        test_write_insensitive_policy(true);
    }

    private void test_write_insensitive_policy(boolean compress) throws Exception {

        // Determine file names.
        String fileNamePrefix = "writeInsensitivePolicy-compress-" + String.valueOf(compress).toLowerCase();
        File file = new File(tmpDir, fileNamePrefix + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir, fileNamePrefix + "-%d{yyyy}.log").getAbsolutePath();
        String rotatedFileNameSuffix = compress ? ".gz" : "";
        Instant now = Instant.now();
        File rotatedFile = new File(
                fileNamePattern.replace(
                        "%d{yyyy}",
                        String.valueOf(now.atZone(UtcHelper.ZONE_ID).getYear()))
                        + rotatedFileNameSuffix);

        // Create the policy.
        RotationPolicy policy = Mockito.mock(RotationPolicy.class);
        Mockito.when(policy.isWriteSensitive()).thenReturn(false);
        Mockito.when(policy.toString()).thenReturn("MockedPolicy");

        // Create the stream.
        RotationCallback callback = Mockito.spy(LoggingRotationCallback.getInstance());
        RotationConfig config = RotationConfig
                .builder()
                .executorService(executorService)
                .compress(compress)
                .file(fileName)
                .filePattern(fileNamePattern)
                .policy(policy)
                .callbacks(Collections.singleton(callback))
                .build();
        InOrder inOrder = Mockito.inOrder(callback, policy);
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Verify the initial file open.
        inOrder
                .verify(callback)
                .onOpen(Mockito.isNull(),
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));

        // Verify the policy start.
        inOrder.verify(policy).start(Mockito.eq(stream));

        // Write some bytes.
        byte[] payload = "stuff to be written".getBytes(StandardCharsets.UTF_8);
        stream.write(payload);
        stream.flush();

        // Verify that policy is not acknowledged on write.
        Mockito.verify(policy).isWriteSensitive();
        Mockito.verify(policy, Mockito.never()).acceptWrite(Mockito.anyLong());

        // Trigger rotation.
        LOGGER.trace("triggering rotation");
        stream.rotate(policy, now);

        // Close the stream.
        LOGGER.trace("closing stream");
        stream.close();

        // Verify the rotation trigger.
        inOrder
                .verify(callback)
                .onTrigger(Mockito.same(policy), Mockito.same(now));

        // Verify the rotation file close.
        inOrder
                .verify(callback)
                .onClose(
                        Mockito.same(policy),
                        Mockito.same(now),
                        Mockito.any(OutputStream.class));

        // Verify the rotation file open.
        inOrder
                .verify(callback)
                .onOpen(Mockito.same(policy),
                        Mockito.same(now),
                        Mockito.any(OutputStream.class));

        // Verify the rotation.
        inOrder
                .verify(callback, Mockito.timeout(1_000))
                .onSuccess(
                        Mockito.same(policy),
                        Mockito.same(now),
                        Mockito.eq(rotatedFile));
        long rotatedFileLength = rotatedFile.length();
        int expectedRotatedFileLength = compress
                ? findCompressedLength(payload)
                : payload.length;
        Assertions.assertThat(rotatedFileLength).isEqualTo(expectedRotatedFileLength);
        Assertions.assertThat(file.length()).isEqualTo(0);

        // Verify the stream close and the policy shutdown. (We cannot use
        // InOrder here since we don't know whether the rotation background task
        // due to compression or the user-invoked close() will finish earlier.)
        Mockito
                .verify(callback)
                .onClose(
                        Mockito.isNull(),
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));
        Mockito.verify(policy).stop();

        // Verify no more interactions.
        Mockito.verifyNoMoreInteractions(callback);
        Mockito.verifyNoMoreInteractions(policy);

    }

    private static int findCompressedLength(byte[] inputBytes) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzippedOutputStream = new GZIPOutputStream(outputStream)) {
                gzippedOutputStream.write(inputBytes);
            }
            return outputStream.toByteArray().length;
        } catch (IOException error) {
            throw new RuntimeException("compress failure", error);
        }
    }

    @Test
    void test_write_sensitive_policy() throws Exception {

        // Determine file names.
        String fileNamePrefix = "writeSensitivePolicy";
        File file = new File(tmpDir, fileNamePrefix + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir, fileNamePrefix + "-%d{yyyy}.log").getAbsolutePath();
        File rotatedFile = new File(fileNamePattern.replace("%d{yyyy}", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))));

        // Create the stream.
        int maxByteCount = 1024;
        SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy(maxByteCount);
        RotationCallback callback = Mockito.spy(LoggingRotationCallback.getInstance());
        InOrder callbackInOrder = Mockito.inOrder(callback);
        RotationConfig config = RotationConfig
                .builder()
                .executorService(executorService)
                .file(fileName)
                .filePattern(fileNamePattern)
                .policy(policy)
                .callbacks(Collections.singleton(callback))
                .build();
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Verify the initial file open.
        callbackInOrder
                .verify(callback)
                .onOpen(Mockito.isNull(),
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));

        // Verify no rotations so far.
        Mockito.verifyNoMoreInteractions(callback);

        // Increase the size of the file just to the edge.
        LOGGER.trace("writing to file");
        for (int byteIndex = 0; byteIndex < maxByteCount; byteIndex++) {
            stream.write(byteIndex);
        }
        Assertions.assertThat(file.length()).isEqualTo(maxByteCount);

        // Verify no rotations so far.
        Mockito.verifyNoMoreInteractions(callback);

        // Push the file size off the threshold.
        LOGGER.trace("writing more bytes to file");
        stream.write(0xDEADBEEF);

        // Verify the rotation trigger.
        callbackInOrder
                .verify(callback)
                .onTrigger(
                        Mockito.same(policy),
                        Mockito.any(Instant.class));

        // Verify the rotation file close.
        callbackInOrder
                .verify(callback)
                .onClose(
                        Mockito.same(policy),
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));

        // Verify the rotation file open.
        callbackInOrder
                .verify(callback)
                .onOpen(Mockito.same(policy),
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));

        // Verify the rotation.
        callbackInOrder
                .verify(callback)
                .onSuccess(
                        Mockito.same(policy),
                        Mockito.any(Instant.class),
                        Mockito.eq(rotatedFile));
        Assertions.assertThat(rotatedFile.length()).isEqualTo(maxByteCount);
        Assertions.assertThat(file.length()).isEqualTo(1);

        // Verify no more callback interactions.
        Mockito.verifyNoMoreInteractions(callback);

        // Close the stream to avoid Windows failing to clean the temporary directory.
        stream.close();

    }

    @Test
    void test_empty_files_are_not_rotated() throws Exception {

        // Determine file names.
        String className = RotatingFileOutputStream.class.getSimpleName();
        File file = new File(tmpDir, className + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir, className + "-%d{yyyy}.log").getAbsolutePath();

        // Create the stream.
        int maxByteCount = 1024;
        SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy(maxByteCount);
        RotationCallback callback = Mockito.spy(LoggingRotationCallback.getInstance());
        InOrder callbackInOrder = Mockito.inOrder(callback);
        RotationConfig config = RotationConfig
                .builder()
                .executorService(executorService)
                .file(fileName)
                .filePattern(fileNamePattern)
                .policy(policy)
                .callbacks(Collections.singleton(callback))
                .build();
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Verify the initial file open.
        callbackInOrder
                .verify(callback)
                .onOpen(Mockito.isNull(),
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));

        // Write some payload of size exceeding the threshold. This should trigger
        // an attempt to rotate the initial file, but the actual rotation should
        // have skipped since the file is empty.
        LOGGER.trace("writing to file");
        byte[] payload = new byte[2 * maxByteCount];
        for (int byteIndex = 0; byteIndex < payload.length; byteIndex++) {
            payload[byteIndex] = (byte) byteIndex;
        }
        stream.write(payload);
        stream.flush();
        Assertions.assertThat(file.length()).isEqualTo(payload.length);

        // Verify the rotation trigger.
        callbackInOrder
                .verify(callback)
                .onTrigger(Mockito.same(policy), Mockito.any(Instant.class));

        // Verify the rotation skip.
        Mockito.verifyNoMoreInteractions(callback);

        // Close the stream to avoid Windows failing to clean the temporary directory.
        stream.close();

    }

    @Test
    void test_adding_file_header() throws IOException {

        // Determine file names.
        String className = RotatingFileOutputStream.class.getSimpleName();
        File file = new File(tmpDir, className + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir, className + "-%d{yyyy}.log").getAbsolutePath();
        File rotatedFile = new File(fileNamePattern.replace("%d{yyyy}", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))));

        // Create the stream config.
        int maxByteCount = 1024;
        SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy(maxByteCount);
        RotationCallback callback = Mockito.spy(LoggingRotationCallback.getInstance());
        InOrder callbackInOrder = Mockito.inOrder(callback);
        RotationConfig config = RotationConfig
                .builder()
                .executorService(executorService)
                .file(fileName)
                .filePattern(fileNamePattern)
                .policy(policy)
                .callbacks(Collections.singleton(callback))
                .build();

        // Create the header injectors.
        byte[] header1 = "header1".getBytes(StandardCharsets.UTF_8);
        byte[] header2 = "header2".getBytes(StandardCharsets.UTF_8);
        Assertions.assertThat(header1.length).isLessThan(maxByteCount);
        Assertions.assertThat(header2.length).isLessThan(maxByteCount);
        Mockito
                .doAnswer(invocation -> {
                    LOGGER.trace("injecting the 1st header");
                    OutputStream outputStream = invocation.getArgument(2);
                    outputStream.write(header1);
                    return null;
                })
                .doAnswer(invocation -> {
                    LOGGER.trace("injecting the 2nd header");
                    OutputStream outputStream = invocation.getArgument(2);
                    outputStream.write(header2);
                    return null;
                })
                .when(callback)
                .onOpen(Mockito.any(),      // null at start
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));

        // Create the footer injectors.
        byte[] footer1 = "footer1".getBytes(StandardCharsets.UTF_8);
        byte[] footer2 = "footer2".getBytes(StandardCharsets.UTF_8);
        Mockito
                .doAnswer(invocation -> {
                    LOGGER.trace("injecting the 1st footer");
                    OutputStream outputStream = invocation.getArgument(2);
                    outputStream.write(footer1);
                    return null;
                })
                .doAnswer(invocation -> {
                    LOGGER.trace("injecting the 2nd footer");
                    OutputStream outputStream = invocation.getArgument(2);
                    outputStream.write(footer2);
                    return null;
                })
                .when(callback)
                .onClose(
                        Mockito.any(),      // null on user-invoked close()
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
        byte[] payload2 = {(byte) (payload1[payload1.length - 1] + 1)};
        stream.write(payload2);

        // Close the stream.
        LOGGER.trace("closing stream");
        stream.close();

        // Verify the rotation trigger.
        callbackInOrder
                .verify(callback)
                .onTrigger(
                        Mockito.same(policy),
                        Mockito.any(Instant.class));

        // Verify the rotation file close.
        callbackInOrder
                .verify(callback)
                .onClose(
                        Mockito.same(policy),
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));

        // Verify the rotation file open.
        callbackInOrder
                .verify(callback)
                .onOpen(Mockito.same(policy),
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));

        // Verify the rotation.
        callbackInOrder
                .verify(callback)
                .onSuccess(
                        Mockito.same(policy),
                        Mockito.any(Instant.class),
                        Mockito.eq(rotatedFile));

        // Verify the file close.
        callbackInOrder
                .verify(callback)
                .onClose(
                        Mockito.isNull(),
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));

        // Verify the rotated file.
        long rotatedFileLength = rotatedFile.length();
        int expectedRotatedFileLength = header1.length + payload1.length + footer1.length;
        Assertions.assertThat(rotatedFileLength).isEqualTo(expectedRotatedFileLength);
        byte[] rotatedFileBytes = readFileBytes(rotatedFile, expectedRotatedFileLength);
        byte[] expectedRotatedFileBytes = copyArrays(header1, payload1, footer1);
        Assertions.assertThat(rotatedFileBytes).isEqualTo(expectedRotatedFileBytes);

        // Verify the re-opened file.
        long reopenedFileLength = file.length();
        int expectedReopenedFileLength = header2.length + payload2.length + footer2.length;
        Assertions.assertThat(reopenedFileLength).isEqualTo(expectedReopenedFileLength);
        byte[] reopenedFileBytes = readFileBytes(file, Math.toIntExact(reopenedFileLength));
        byte[] expectedReopenedFileBytes = copyArrays(header2, payload2, footer2);
        Assertions.assertThat(reopenedFileBytes).isEqualTo(expectedReopenedFileBytes);

        // Verify no more interactions.
        Mockito.verifyNoMoreInteractions(callback);

    }

    private static byte[] readFileBytes(File file, int byteCount) throws IOException {
        byte[] buffer = new byte[byteCount];
        try (FileInputStream stream = new FileInputStream(file)) {
            int readByteCount = stream.read(buffer);
            Assertions.assertThat(readByteCount).isEqualTo(byteCount);
        }
        return buffer;
    }

    private static byte[] copyArrays(byte[]... sources) {
        int targetLength = 0;
        for (byte[] source : sources) {
            targetLength += source.length;
        }
        byte[] target = new byte[targetLength];
        int targetIndex = 0;
        for (byte[] source : sources) {
            System.arraycopy(source, 0, target, targetIndex, source.length);
            targetIndex += source.length;
        }
        return target;
    }

    @Test
    void test_rotation_and_write_failure_after_close() throws Exception {

        // Determine file names.
        String fileNamePrefix = "rotationAndWriteFailureAfterClose";
        File file = new File(tmpDir, fileNamePrefix + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir, fileNamePrefix + "-%d{HHmmss-SSS}.log").getAbsolutePath();

        // Create the stream config.
        RotationPolicy policy = Mockito.mock(RotationPolicy.class);
        Mockito.when(policy.toString()).thenReturn("MockedPolicy");
        RotationCallback callback = Mockito.mock(RotationCallback.class);
        Mockito.when(callback.toString()).thenReturn("MockedCallback");
        RotationConfig config = RotationConfig
                .builder()
                .executorService(executorService)
                .file(fileName)
                .filePattern(fileNamePattern)
                .policy(policy)
                .callbacks(Collections.singleton(callback))
                .build();

        // Create the stream, write some, and close it.
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);
        stream.write("payload".getBytes(StandardCharsets.UTF_8));
        stream.close();

        // Verify rotation failure.
        InOrder inOrder = Mockito.inOrder(callback);
        stream.rotate(null, Instant.now());
        inOrder
                .verify(callback)
                .onFailure(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.argThat(error -> {
                            Throwable cause = error.getCause();
                            return cause instanceof IOException &&
                                    cause
                                            .getMessage()
                                            .contains("either closed or not initialized yet");
                        }));

        // Verify write(int) failure.
        Assertions
                .assertThatThrownBy(() -> stream.write(1))
                .isInstanceOf(IOException.class)
                .hasMessage("either closed or not initialized yet");

        // Verify write(byte[]) failure.
        Assertions
                .assertThatThrownBy(() -> stream.write(new byte[]{0}))
                .isInstanceOf(IOException.class)
                .hasMessage("either closed or not initialized yet");

        // Verify write(byte[], int, int) failure.
        Assertions
                .assertThatThrownBy(() -> stream.write(new byte[]{0}, 0, 1))
                .isInstanceOf(IOException.class)
                .hasMessage("either closed or not initialized yet");

        // Verify no more failures were reported to the callback.
        inOrder
                .verify(callback, Mockito.never())
                .onFailure(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any());

    }

    @Test
    void test_time_based_policies_are_stopped_after_close() throws Exception {

        // Determine file names.
        String fileNamePrefix = "timeBasedPoliciesAfterClose";
        File file = new File(tmpDir, fileNamePrefix + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir, fileNamePrefix + "-%d{HHmmss-SSS}.log").getAbsolutePath();

        // Create the scheduler mock.
        ScheduledFuture<?> scheduledFuture = Mockito.mock(ScheduledFuture.class);
        Mockito.when(scheduledFuture.toString()).thenReturn("MockedScheduledFuture");
        ScheduledExecutorService executorService = Mockito.mock(ScheduledExecutorService.class);
        Mockito
                .when(executorService.schedule(
                        Mockito.any(Runnable.class),
                        Mockito.anyLong(),
                        Mockito.same(TimeUnit.MILLISECONDS)))
                .thenAnswer((Answer<ScheduledFuture<?>>) invocationOnMock -> scheduledFuture);
        Mockito.when(executorService.toString()).thenReturn("MockedScheduledExecutorService");

        // Create the stream config.
        LinkedHashSet<RotationPolicy> policies =
                new LinkedHashSet<>(
                        Arrays.asList(
                                WeeklyRotationPolicy.getInstance(),
                                DailyRotationPolicy.getInstance()));
        RotationConfig config = RotationConfig
                .builder()
                .executorService(executorService)
                .file(fileName)
                .filePattern(fileNamePattern)
                .policies(policies)
                .executorService(executorService)
                .build();

        // Create the stream, write some, and close it.
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);
        stream.write("payload".getBytes(StandardCharsets.UTF_8));
        stream.close();

        // Verify the task scheduling.
        InOrder inOrder = Mockito.inOrder(scheduledFuture, executorService);
        inOrder
                .verify(executorService, Mockito.times(2))
                .schedule(
                        Mockito.any(Runnable.class),
                        Mockito.anyLong(),
                        Mockito.same(TimeUnit.MILLISECONDS));

        // Verify the task cancellation.
        inOrder
                .verify(scheduledFuture, Mockito.times(2))
                .cancel(Mockito.eq(true));

        // Verify no more interactions.
        Mockito.verifyNoMoreInteractions(scheduledFuture);
        Mockito.verifyNoMoreInteractions(executorService);

    }

}
