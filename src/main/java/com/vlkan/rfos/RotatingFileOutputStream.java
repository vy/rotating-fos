package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPOutputStream;

public class RotatingFileOutputStream extends OutputStream implements Rotatable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RotatingFileOutputStream.class);

    private final RotationConfig config;

    private final List<Thread> runningThreads;

    private final Lock rotationLock;

    private volatile FileOutputStream stream;

    public RotatingFileOutputStream(RotationConfig config) {
        this.config = config;
        this.runningThreads = Collections.synchronizedList(new LinkedList<Thread>());
        this.rotationLock = new ReentrantLock();
        this.stream = open();
        startPolicies();
    }

    private void startPolicies() {
        for (RotationPolicy policy : config.getPolicies()) {
            policy.start(this);
        }
    }

    private FileOutputStream open() {
        try {
            return new FileOutputStream(config.getFile(), config.isAppend());
        } catch (IOException error) {
            String message = String.format("file open failure {file=%s}", config.getFile());
            throw new RuntimeException(message);
        }
    }

    @Override
    public void rotate(RotationPolicy policy, LocalDateTime dateTime) {
        boolean acquired = rotationLock.tryLock();
        if (!acquired) {
            config.getCallback().onConflict(policy, dateTime);
        } else {
            try {
                unsafeRotate(policy, dateTime);
            } catch (Exception error) {
                String message = String.format("rotation failure {dateTime=%s}", dateTime);
                RuntimeException extendedError = new RuntimeException(message);
                config.getCallback().onFailure(policy, dateTime, null, extendedError);
            } finally {
                rotationLock.unlock();
            }
        }
    }

    private void unsafeRotate(RotationPolicy policy, LocalDateTime dateTime) throws Exception {

        // Skip rotation if file is empty.
        if (config.getFile().length() == 0) {
            LOGGER.debug("empty file, skipping rotation {file={}}");
            config.getCallback().onSuccess(policy, dateTime, null);
            return;
        }

        // Rename the file.
        File rotatedFile = config.getFilePattern().create(dateTime).getAbsoluteFile();
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
        FileOutputStream newStream = open();
        FileOutputStream oldStream;
        Lock writeLock = config.getLock().writeLock();
        writeLock.lock();
        try {
            oldStream = stream;
            stream = newStream;
        } finally {
            writeLock.unlock();
        }
        oldStream.close();

        // Compress the old file, if necessary.
        if (config.isCompress()) {
            asyncCompress(policy, dateTime, rotatedFile, config.getCallback());
            return;
        }

        // So far, so good;
        config.getCallback().onSuccess(policy, dateTime, rotatedFile);

    }

    private void asyncCompress(final RotationPolicy policy, final LocalDateTime dateTime, final File rotatedFile, final RotationCallback callback) {
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
    public void write(int b) throws IOException {
        Lock readLock = config.getLock().readLock();
        readLock.lock();
        try {
            stream.write(b);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        Lock readLock = config.getLock().readLock();
        readLock.lock();
        try {
            stream.write(b);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        Lock readLock = config.getLock().readLock();
        readLock.lock();
        try {
            stream.write(b, off, len);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void flush() throws IOException {
        Lock readLock = config.getLock().readLock();
        readLock.lock();
        try {
            stream.flush();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        config.getTimer().cancel();
        Lock readLock = config.getLock().readLock();
        readLock.lock();
        try {
            stream.close();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public String toString() {
        return String.format("RotatingFileOutputStream{file=%s}", config.getFile());
    }

}
