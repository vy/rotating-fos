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
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Calendar;
import java.util.zip.GZIPOutputStream;

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

        // Determine file names.
        String className = RotatingFileOutputStream.class.getSimpleName();
        File file = new File(tmpDir.getRoot(), className + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir.getRoot(), className + "-%d{yyyy}.log").getAbsolutePath();
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
                .compress(compress)
                .file(fileName)
                .filePattern(fileNamePattern)
                .policy(policy)
                .callback(callback)
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
        stream.rotate(policy, now);

        // Verify the rotation trigger.
        inOrder
                .verify(callback)
                .onTrigger(Mockito.same(policy), Mockito.same(now));

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
        assertThat(rotatedFileLength).isEqualTo(expectedRotatedFileLength);
        assertThat(file.length()).isEqualTo(0);

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
    public void test_write_sensitive_policy() throws Exception {

        // Determine file names.
        String className = RotatingFileOutputStream.class.getSimpleName();
        File file = new File(tmpDir.getRoot(), className + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir.getRoot(), className + "-%d{yyyy}.log").getAbsolutePath();
        File rotatedFile = new File(fileNamePattern.replace("%d{yyyy}", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))));

        // Create the stream.
        int maxByteCount = 1024;
        SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy(maxByteCount);
        RotationCallback callback = Mockito.spy(LoggingRotationCallback.getInstance());
        InOrder callbackInOrder = Mockito.inOrder(callback);
        RotationConfig config = RotationConfig
                .builder()
                .file(fileName)
                .filePattern(fileNamePattern)
                .policy(policy)
                .callback(callback)
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
        assertThat(file.length()).isEqualTo(maxByteCount);

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
        assertThat(rotatedFile.length()).isEqualTo(maxByteCount);
        assertThat(file.length()).isEqualTo(1);

        // Verify no more callback interactions.
        Mockito.verifyNoMoreInteractions(callback);

    }

    @Test
    public void test_empty_files_are_not_rotated() throws Exception {

        // Determine file names.
        String className = RotatingFileOutputStream.class.getSimpleName();
        File file = new File(tmpDir.getRoot(), className + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir.getRoot(), className + "-%d{yyyy}.log").getAbsolutePath();

        // Create the stream.
        int maxByteCount = 1024;
        SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy(maxByteCount);
        RotationCallback callback = Mockito.spy(LoggingRotationCallback.getInstance());
        InOrder callbackInOrder = Mockito.inOrder(callback);
        RotationConfig config = RotationConfig
                .builder()
                .file(fileName)
                .filePattern(fileNamePattern)
                .policy(policy)
                .callback(callback)
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
        assertThat(file.length()).isEqualTo(payload.length);

        // Verify the rotation trigger.
        callbackInOrder
                .verify(callback)
                .onTrigger(Mockito.same(policy), Mockito.any(Instant.class));

        // Verify the rotation skip.
        Mockito.verifyNoMoreInteractions(callback);

    }

    @Test
    public void test_adding_file_header() throws IOException {

        // Determine file names.
        String className = RotatingFileOutputStream.class.getSimpleName();
        File file = new File(tmpDir.getRoot(), className + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir.getRoot(), className + "-%d{yyyy}.log").getAbsolutePath();
        File rotatedFile = new File(fileNamePattern.replace("%d{yyyy}", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))));

        // Create the stream config.
        int maxByteCount = 1024;
        SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy(maxByteCount);
        RotationCallback callback = Mockito.spy(LoggingRotationCallback.getInstance());
        InOrder callbackInOrder = Mockito.inOrder(callback);
        RotationConfig config = RotationConfig
                .builder()
                .file(fileName)
                .filePattern(fileNamePattern)
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

        // Verify no more interactions.
        Mockito.verifyNoMoreInteractions(callback);

    }

}
