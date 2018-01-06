package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;
import com.vlkan.rfos.policy.RotationPolicyContext;
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
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.zip.GZIPOutputStream;

public class RotatingFileOutputStream extends OutputStream implements Rotatable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RotatingFileOutputStream.class);

    private final Builder builder;

    private final List<Thread> runningThreads;

    private volatile FileOutputStream stream;

    private RotatingFileOutputStream(Builder builder) {
        this.builder = builder;
        this.runningThreads = Collections.synchronizedList(new LinkedList<Thread>());
        this.stream = open();
        startPolicies(builder);
    }

    private void startPolicies(Builder builder) {
        RotationPolicyContext policyContext = RotationPolicyContext
                .builder()
                .clock(builder.clock)
                .file(builder.file)
                .timer(builder.timer)
                .rotatable(this)
                .callback(builder.callback)
                .build();
        for (RotationPolicy policy : builder.policies) {
            policy.start(policyContext);
        }
    }

    private FileOutputStream open() {
        try {
            return new FileOutputStream(builder.file, builder.append);
        } catch (IOException error) {
            String message = String.format("file open failure {file=%s}", builder.file);
            throw new RuntimeException(message);
        }
    }

    @Override
    public void rotate(RotationPolicy policy, LocalDateTime dateTime, RotationCallback callback) {
        try {
            unsafeRotate(policy, dateTime, callback);
        } catch (Exception error) {
            String message = String.format("rotation failure {dateTime=%s}", dateTime);
            RuntimeException extendedError = new RuntimeException(message);
            callback.onFailure(policy, dateTime, null, extendedError);
        }
    }

    private void unsafeRotate(RotationPolicy policy, LocalDateTime dateTime, RotationCallback callback) throws Exception {

        // Skip rotation if file is empty.
        if (builder.file.length() == 0) {
            LOGGER.debug("empty file, skipping rotation {file={}}");
            callback.onSuccess(policy, dateTime, null);
            return;
        }

        // Rename the file.
        File rotatedFile = builder.filePattern.create(dateTime).getAbsoluteFile();
        LOGGER.debug("renaming {file={}, rotatedFile={}}", builder.file, rotatedFile);
        boolean renamed = builder.file.renameTo(rotatedFile);
        if (!renamed) {
            String message = String.format("rename failure {file=%s, rotatedFile=%s}", builder.file, rotatedFile);
            IOException error = new IOException(message);
            callback.onFailure(policy, dateTime, rotatedFile, error);
            return;
        }

        // Re-open the file.
        LOGGER.debug("re-opening file {file={}}", builder.file);
        stream = open();

        // Compress the old file, if necessary.
        if (builder.compress) {
            asyncCompress(policy, dateTime, rotatedFile, callback);
            return;
        }

        // So far, so good;
        callback.onSuccess(policy, dateTime, rotatedFile);

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

    public List<Thread> getRunningThreads() {
        return runningThreads;
    }

    @Override
    public void write(int b) throws IOException {
        stream.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        stream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        stream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        stream.flush();
    }

    @Override
    public void close() throws IOException {
        builder.timer.cancel();
        stream.close();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private File file;

        private RotatingFilePattern filePattern;

        private Timer timer;

        private Set<RotationPolicy> policies;

        private boolean append = true;

        private boolean compress = false;

        private Clock clock = SystemClock.getInstance();

        private RotationCallback callback = LoggingRotationCallback.getInstance();

        private Builder() {
            // Do nothing.
        }

        public Builder file(File file) {
            this.file = file;
            return this;
        }

        public Builder file(String fileName) {
            this.file = new File(fileName);
            return this;
        }

        public Builder filePattern(RotatingFilePattern filePattern) {
            this.filePattern = filePattern;
            return this;
        }

        public Builder filePattern(String filePattern) {
            this.filePattern = new RotatingFilePattern(filePattern);
            return this;
        }

        public Builder timer(Timer timer) {
            this.timer = timer;
            return this;
        }

        public Builder policies(Set<RotationPolicy> policies) {
            this.policies = policies;
            return this;
        }

        public Builder policy(RotationPolicy policy) {
            if (policies == null) {
                policies = new LinkedHashSet<>();
            }
            policies.add(policy);
            return this;
        }

        public Builder append(boolean append) {
            this.append = append;
            return this;
        }

        public Builder compress(boolean compress) {
            this.compress = compress;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder callback(RotationCallback callback) {
            this.callback = callback;
            return this;
        }

        public RotatingFileOutputStream build() {
            prepare();
            validate();
            return new RotatingFileOutputStream(this);
        }

        private void prepare() {
            if (timer == null) {
                timer = new Timer();
            }
        }

        private void validate() {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(filePattern, "filePattern");
            if (policies == null || policies.isEmpty()) {
                throw new IllegalArgumentException("empty policies");
            }
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(callback, "callback");
        }

    }

}
