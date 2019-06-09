package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class RotatingFileOutputStream extends OutputStream implements Rotatable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RotatingFileOutputStream.class);

    private final RotationConfig config;

    private final List<Thread> runningThreads;

    private final List<RotationPolicy> writeSensitivePolicies;

    private volatile ByteCountingOutputStream stream;

    public RotatingFileOutputStream(RotationConfig config) {
        this.config = config;
        this.runningThreads = Collections.synchronizedList(new LinkedList<>());
        this.writeSensitivePolicies = collectWriteSensitivePolicies(config.getPolicies());
        this.stream = open();
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

    private ByteCountingOutputStream open() {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(config.getFile(), config.isAppend());
            long size = config.isAppend() ? config.getFile().length() : 0;
            return new ByteCountingOutputStream(fileOutputStream, size);
        } catch (IOException error) {
            String message = String.format("file open failure {file=%s}", config.getFile());
            throw new RuntimeException(message);
        }
    }

    @Override
    public void rotate(RotationPolicy policy, Instant dateTime) {
        try {
            unsafeRotate(policy, dateTime);
        } catch (Exception error) {
            String message = String.format("rotation failure {dateTime=%s}", dateTime);
            RuntimeException extendedError = new RuntimeException(message, error);
            config.getCallback().onFailure(policy, dateTime, null, extendedError);
        }
    }

    private void unsafeRotate(RotationPolicy policy, Instant dateTime) throws Exception {

        File rotatedFile;
        synchronized (this) {

            // Skip rotation if the file is empty.
            if (config.getFile().length() == 0) {
                LOGGER.debug("empty file, skipping rotation {file={}}", config.getFile());
                return;
            }

            // Rename the file.
            rotatedFile = config.getFilePattern().create(dateTime).getAbsoluteFile();
            LOGGER.debug("renaming {file={}, rotatedFile={}}", config.getFile(), rotatedFile);
            boolean renamed = config.getFile().renameTo(rotatedFile);
            if (!renamed) {
                String message = String.format("rename failure {file=%s, rotatedFile=%s}", config.getFile(), rotatedFile);
                IOException error = new IOException(message);
                config.getCallback().onFailure(policy, dateTime, rotatedFile, error);
                return;
            }

            // Re-open the file.
            LOGGER.debug("re-opening file {file={}}", config.getFile());
            ByteCountingOutputStream newStream = open();
            ByteCountingOutputStream oldStream = stream;
            stream = newStream;
            oldStream.parent().close();

        }

        // Compress the old file, if necessary.
        if (config.isCompress()) {
            asyncCompress(policy, dateTime, rotatedFile, config.getCallback());
            return;
        }

        // So far, so good;
        config.getCallback().onSuccess(policy, dateTime, rotatedFile);

    }

    private void asyncCompress(final RotationPolicy policy, final Instant dateTime, final File rotatedFile, final RotationCallback callback) {
        String threadName = String.format("%s.compress(%s)", RotatingFileOutputStream.class.getSimpleName(), rotatedFile);
        Runnable threadTask = new Runnable() {
            @Override
            public void run() {
                Thread thread = Thread.currentThread();
                runningThreads.add(thread);
                File compressedFile = getCompressedFile(rotatedFile);
                try {
                    unsafeSyncCompress(rotatedFile, compressedFile);
                    callback.onSuccess(policy, dateTime, compressedFile);
                } catch (Exception error) {
                    String message = String.format(
                            "compression failure {dateTime=%s, rotatedFile=%s, compressedFile=%s}",
                            dateTime, rotatedFile, compressedFile);
                    RuntimeException extendedError = new RuntimeException(message, error);
                    callback.onFailure(policy, dateTime, rotatedFile, extendedError);
                } finally {
                    runningThreads.remove(thread);
                }
            }
        };
        new Thread(threadTask, threadName).start();
    }

    private File getCompressedFile(File rotatedFile) {
        String compressedFileName = String.format("%s.gz", rotatedFile.getAbsolutePath());
        return new File(compressedFileName);
    }

    private static void unsafeSyncCompress(File rotatedFile, File compressedFile) throws IOException {
        LOGGER.debug("compressing {rotatedFile={}, compressedFile={}}", rotatedFile, compressedFile);
        try (InputStream sourceStream = new FileInputStream(rotatedFile)) {
            try (OutputStream targetStream = new FileOutputStream(compressedFile)) {
                try (GZIPOutputStream gzipTargetStream = new GZIPOutputStream(targetStream)) {
                    copy(sourceStream, gzipTargetStream);
                    LOGGER.debug("deleting old file {rotatedFile={}}", rotatedFile);
                    boolean deleted = rotatedFile.delete();
                    if (!deleted) {
                        String message = String.format("failed deleting old file {rotatedFile=%s}", rotatedFile);
                        throw new IOException(message);
                    }
                }
            }
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

    public List<Thread> getRunningThreads() {
        return runningThreads;
    }

    @Override
    public synchronized void write(int b) throws IOException {
        long byteCount = stream.size() + 1;
        notifyWriteSensitivePolicies(byteCount);
        stream.write(b);
    }

    @Override
    public synchronized void write(byte[] b) throws IOException {
        long byteCount = stream.size() + b.length;
        notifyWriteSensitivePolicies(byteCount);
        stream.write(b);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        long byteCount = stream.size() + len;
        notifyWriteSensitivePolicies(byteCount);
        stream.write(b, off, len);
    }

    private void notifyWriteSensitivePolicies(long byteCount) {
        // noinspection ForLoopReplaceableByForEach
        for (int writeSensitivePolicyIndex = 0; writeSensitivePolicyIndex < writeSensitivePolicies.size(); writeSensitivePolicyIndex++) {
            RotationPolicy writeSensitivePolicy = writeSensitivePolicies.get(writeSensitivePolicyIndex);
            writeSensitivePolicy.acceptWrite(byteCount);
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        stream.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        config.getTimer().cancel();
        stream.parent().close();
        stream = null;
    }

    @Override
    public String toString() {
        return String.format("RotatingFileOutputStream{file=%s}", config.getFile());
    }

}
