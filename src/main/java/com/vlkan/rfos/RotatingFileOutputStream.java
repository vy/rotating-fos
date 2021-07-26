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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vlkan.rfos.policy.RotationPolicy;

public class RotatingFileOutputStream extends OutputStream implements Rotatable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RotatingFileOutputStream.class);

    private final RotationConfig config;

    private final List<RotationCallback> callbacks;

    private final List<RotationPolicy> writeSensitivePolicies;

    private volatile ByteCountingOutputStream stream;

    public RotatingFileOutputStream(RotationConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.callbacks = new ArrayList<>(config.getCallbacks());
        this.writeSensitivePolicies = collectWriteSensitivePolicies(config.getPolicies());
        this.stream = open(null, config.getClock().now());
        startPolicies();
    }

    private static List<RotationPolicy> collectWriteSensitivePolicies(Set<RotationPolicy> policies) {
        List<RotationPolicy> writeSensitivePolicies = new ArrayList<>();
        for (RotationPolicy policy : policies) {
            if (policy.isWriteSensitive()) {
                writeSensitivePolicies.add(policy);
            }
        }
        return writeSensitivePolicies;
    }

    private void startPolicies() {
        for (RotationPolicy policy : config.getPolicies()) {
            policy.start(this);
        }
    }

    private ByteCountingOutputStream open(RotationPolicy policy, Instant instant) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(config.getFile(), config.isAppend());
            invokeCallbacks(callback -> callback.onOpen(policy, instant, fileOutputStream));
            long size = config.isAppend() ? config.getFile().length() : 0;
            return new ByteCountingOutputStream(fileOutputStream, size);
        } catch (IOException error) {
            String message = String.format("file open failure {file=%s}", config.getFile());
            throw new RuntimeException(message);
        }
    }

    @Override
    public void rotate(RotationPolicy policy, Instant instant) {
        try {
            unsafeRotate(policy, instant);
        } catch (Exception error) {
            String message = String.format("rotation failure {instant=%s}", instant);
            RuntimeException extendedError = new RuntimeException(message, error);
            invokeCallbacks(callback -> callback.onFailure(policy, instant, null, extendedError));
        }
    }

    private synchronized void unsafeRotate(RotationPolicy policy, Instant instant) throws Exception {

        // Check arguments.
        Objects.requireNonNull(instant, "instant");

        // Check the state.
        unsafeCheckStream();

        // Notify the trigger listeners.
        invokeCallbacks(callback -> callback.onTrigger(policy, instant));

        // Skip rotation if the file is empty.
        if (config.getFile().length() == 0) {
            LOGGER.debug("empty file, skipping rotation {file={}}", config.getFile());
            return;
        }

        // Close the file. (Required before rename on Windows!)
        invokeCallbacks(callback -> callback.onClose(policy, instant, stream));
        stream.close();

        // Rename backups, if enabled.
        File rotatedFile;
        boolean renamed;
        if (config.getMaxBackupCount() > 0) {
            File[] rotatedFileRef = {null};
            renamed = renameBackups() && backupFile(rotatedFileRef);
            rotatedFile = rotatedFileRef[0];
        }

        // Otherwise, rename using the provided file pattern.
        else {
        	rotatedFile = config.getFilePattern().create(instant).getAbsoluteFile();
            LOGGER.debug("renaming {file={}, rotatedFile={}}", config.getFile(), rotatedFile);
            renamed = config.getFile().renameTo(rotatedFile);
        }

        if (!renamed) {
            String message = String.format("rename failure {file=%s, rotatedFile=%s}", config.getFile(), rotatedFile);
            IOException error = new IOException(message);
            invokeCallbacks(callback -> callback.onFailure(policy, instant, rotatedFile, error));
            return;
        }

        // Re-open the file.
        LOGGER.debug("re-opening file {file={}}", config.getFile());
        stream = open(policy, instant);

        // Compress the old file, if necessary.
        if (config.isCompress()) {
            asyncCompress(policy, instant, rotatedFile);
            return;
        }

        // So far, so good;
        invokeCallbacks(callback -> callback.onSuccess(policy, instant, rotatedFile));

    }

    private boolean renameBackups() {
        File newFile = getBackupFile(config.getMaxBackupCount() - 1);
        for (int backupIndex = config.getMaxBackupCount() - 2; backupIndex >= 0; backupIndex--) {
            File oldFile = getBackupFile(backupIndex);
            if (!oldFile.exists()) {
                continue;
            }
            LOGGER.debug("renaming backup {} to {}", oldFile, newFile);
            boolean renamed = oldFile.renameTo(newFile);
            if (!renamed) {
                LOGGER.error("failed renaming backup {} to {}", oldFile, newFile);
                return false;
            }
            newFile = oldFile;
        }
        return true;
    }

    private boolean backupFile(File[] backupFileRef) {
        File newFile = backupFileRef[0] = getBackupFile(0);
        File oldFile = config.getFile();
        LOGGER.debug("renaming {} to {} for backup", oldFile, newFile);
        return oldFile.renameTo(newFile);
    }

    private File getBackupFile(int backupIndex) {
        String parent = config.getFile().getParent();
        if (parent == null) {
            parent = ".";
        }
        String fileName = config.getFile().getName() + '.' + backupIndex;
        return Paths.get(parent, fileName).toFile();
    }

    private void asyncCompress(RotationPolicy policy, Instant instant, File rotatedFile) {
        config.getExecutorService().execute(new Runnable() {

            private final String displayName =
                    String.format(
                            "%s.compress(%s)",
                            RotatingFileOutputStream.class.getSimpleName(), rotatedFile);

            @Override
            public void run() {
                File compressedFile = getCompressedFile(rotatedFile);
                try {
                    unsafeSyncCompress(rotatedFile, compressedFile);
                    invokeCallbacks(callback -> callback.onSuccess(policy, instant, compressedFile));
                } catch (Exception error) {
                    String message = String.format(
                            "compression failure {instant=%s, rotatedFile=%s, compressedFile=%s}",
                            instant, rotatedFile, compressedFile);
                    RuntimeException extendedError = new RuntimeException(message, error);
                    invokeCallbacks(callback -> callback.onFailure(policy, instant, rotatedFile, extendedError));
                }
            }

            @Override
            public String toString() {
                return displayName;
            }

        });
    }

    private File getCompressedFile(File rotatedFile) {
        String compressedFileName = String.format("%s.gz", rotatedFile.getAbsolutePath());
        return new File(compressedFileName);
    }

    private static void unsafeSyncCompress(File rotatedFile, File compressedFile) throws IOException {

        // Compress the file.
        LOGGER.debug("compressing {rotatedFile={}, compressedFile={}}", rotatedFile, compressedFile);
        try (InputStream sourceStream = new FileInputStream(rotatedFile)) {
            try (FileOutputStream targetStream = new FileOutputStream(compressedFile);
                 GZIPOutputStream gzipTargetStream = new GZIPOutputStream(targetStream)) {
                copy(sourceStream, gzipTargetStream);
            }
        }

        // Delete the rotated file. (On Windows, delete must take place after closing the file input stream!)
        LOGGER.debug("deleting old file {rotatedFile={}}", rotatedFile);
        boolean deleted = rotatedFile.delete();
        if (!deleted) {
            String message = String.format("failed deleting old file {rotatedFile=%s}", rotatedFile);
            throw new IOException(message);
        }

    }

    private static void copy(InputStream source, OutputStream target) throws IOException {
        byte[] buffer = new byte[8192];
        int readByteCount;
        while ((readByteCount = source.read(buffer)) > 0) {
            target.write(buffer, 0, readByteCount);
        }
    }

    @Override
    public RotationConfig getConfig() {
        return config;
    }

    @Override
    public synchronized void write(int b) throws IOException {
        unsafeCheckStream();
        long byteCount = stream.size() + 1;
        notifyWriteSensitivePolicies(byteCount);
        stream.write(b);
    }

    @Override
    public synchronized void write(byte[] b) throws IOException {
        unsafeCheckStream();
        long byteCount = stream.size() + b.length;
        notifyWriteSensitivePolicies(byteCount);
        stream.write(b);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        unsafeCheckStream();
        long byteCount = stream.size() + len;
        notifyWriteSensitivePolicies(byteCount);
        stream.write(b, off, len);
    }

    private void notifyWriteSensitivePolicies(long byteCount) {
        // noinspection ForLoopReplaceableByForEach (avoid iterator instantion)
        for (int writeSensitivePolicyIndex = 0;
             writeSensitivePolicyIndex < writeSensitivePolicies.size();
             writeSensitivePolicyIndex++) {
            RotationPolicy writeSensitivePolicy = writeSensitivePolicies.get(writeSensitivePolicyIndex);
            writeSensitivePolicy.acceptWrite(byteCount);
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        if (stream != null) {
            stream.flush();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (stream == null) {
            return;
        }
        invokeCallbacks(callback -> callback.onClose(null, config.getClock().now(), stream));
        stopPolicies();
        stream.close();
        stream = null;
    }

    private void stopPolicies() {
        config.getPolicies().forEach(RotationPolicy::stop);
    }

    private void invokeCallbacks(Consumer<RotationCallback> invoker) {
        // noinspection ForLoopReplaceableByForEach (avoid iterator instantion)
        for (int callbackIndex = 0; callbackIndex < callbacks.size(); callbackIndex++) {
            RotationCallback callback = callbacks.get(callbackIndex);
            invoker.accept(callback);
        }
    }

    private void unsafeCheckStream() throws IOException {
        if (stream == null) {
            throw new IOException("either closed or not initialized yet");
        }
    }

    @Override
    public String toString() {
        return String.format("RotatingFileOutputStream{file=%s}", config.getFile());
    }

}
