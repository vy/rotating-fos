/*
 * Copyright 2018-2020 Volkan Yazıcı
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.internal.ArrayComparisonFailure;
import org.junit.rules.TemporaryFolder;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vlkan.rfos.policy.RotationPolicy;
import com.vlkan.rfos.policy.SizeBasedRotationPolicy;

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
        assertThat(rotatedFileLength).isEqualTo(expectedRotatedFileLength);
        assertThat(file.length()).isEqualTo(0);

        // Verify the stream close. (We cannot use InOrder here since we don't
        // know whether the rotation background task or the user-invoked close()
        // will finish earlier.)
        Mockito
                .verify(callback)
                .onClose(
                        Mockito.isNull(),
                        Mockito.any(Instant.class),
                        Mockito.any(OutputStream.class));

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
    public void rollingFileTest() throws Exception {
    	LOGGER.debug("rollingFileTest begin");
    	File dir = Paths.get(tmpDir.getRoot().toString(), "rollingFileTest").toFile();
    	dir.mkdir();
    	int maxByteCount = 10;
		int maxBackupIndex = 10;
		String fileName = "app.log";
		RotationConfig config = RotationConfig
	        .builder()
	        .file(dir + File.separator + fileName)
	        .rollingFile(true)
	        .policy(new SizeBasedRotationPolicy(maxByteCount))
	        .maxBackupIndex(maxBackupIndex )
	        .build();
		try (RotatingFileOutputStream stream = new RotatingFileOutputStream(config)) {
			for (int i = 0; i < 50; i++) {
				stream.write(String.format("a%04d",i).getBytes(StandardCharsets.UTF_8));
			}
		}
		
		File[] outputFiles = dir.listFiles();
		assertEquals("maxBackupIndex value not as expected", maxBackupIndex + 1, outputFiles.length);
		Map<String, String> suffixToContent = new HashMap<>();
		suffixToContent.put("", "a0048a0049");
		suffixToContent.put(".1", "a0046a0047");
		suffixToContent.put(".10", "a0028a0029");
		int validatedCount = 0;
		for (File file : outputFiles) {
			LOGGER.debug("Validating file: {}", file);
			String suffix = file.getName().substring(fileName.length());
			LOGGER.debug("suffix: {}", suffix);
			String expectedContent = suffixToContent.get(suffix);
			if (expectedContent != null &&(fileName + suffix).equals(file.getName())) {
				LOGGER.debug("Validating file content for: {}", fileName);
				validateContent(maxByteCount, fileName, file, expectedContent);
				validatedCount++;
			}
		}
		assertEquals("Did not validate content of all files", validatedCount, suffixToContent.size());
		LOGGER.debug("rollingFileTest end");
    }

	private void validateContent(int maxByteCount, String fileName, File file, String expectedContent)
			throws IOException, ArrayComparisonFailure {
		byte[] fileBytes = readFileBytes(file, maxByteCount);
		byte[] expectedBytes = expectedContent.getBytes(StandardCharsets.UTF_8);
		assertArrayEquals("File content not as expected for: " + fileName + ". Expected: " + 
			new String(expectedBytes, StandardCharsets.UTF_8) + ", actual: " + new String(fileBytes, StandardCharsets.UTF_8), expectedBytes, fileBytes);
	}

}
