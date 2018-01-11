package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RotationConfig {

    private final File file;

    private final RotatingFilePattern filePattern;

    private final Timer timer;

    private final ReadWriteLock lock;

    private final Set<RotationPolicy> policies;

    private final boolean append;

    private final boolean compress;

    private final Clock clock;

    private final RotationCallback callback;

    private RotationConfig(Builder builder) {
        this.file = builder.file;
        this.filePattern = builder.filePattern;
        this.timer = builder.timer;
        this.lock = builder.lock;
        this.policies = builder.policies;
        this.append = builder.append;
        this.compress = builder.compress;
        this.clock = builder.clock;
        this.callback = builder.callback;
    }

    public File getFile() {
        return file;
    }

    public RotatingFilePattern getFilePattern() {
        return filePattern;
    }

    public Timer getTimer() {
        return timer;
    }

    public ReadWriteLock getLock() {
        return lock;
    }

    public Set<RotationPolicy> getPolicies() {
        return policies;
    }

    public boolean isAppend() {
        return append;
    }

    public boolean isCompress() {
        return compress;
    }

    public Clock getClock() {
        return clock;
    }

    public RotationCallback getCallback() {
        return callback;
    }

    @Override
    public boolean equals(Object instance) {
        if (this == instance) return true;
        if (instance == null || getClass() != instance.getClass()) return false;
        RotationConfig that = (RotationConfig) instance;
        return append == that.append &&
                compress == that.compress &&
                Objects.equals(file, that.file) &&
                Objects.equals(filePattern, that.filePattern) &&
                Objects.equals(timer, that.timer) &&
                Objects.equals(lock, that.lock) &&
                Objects.equals(policies, that.policies) &&
                Objects.equals(clock, that.clock) &&
                Objects.equals(callback, that.callback);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, filePattern, timer, lock, policies, append, compress, clock, callback);
    }

    @Override
    public String toString() {
        return String.format("RotationConfig{file=%s}", file);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private File file;

        private RotatingFilePattern filePattern;

        private Timer timer;

        private ReadWriteLock lock;

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

        public Builder lock(ReadWriteLock lock) {
            this.lock = lock;
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

        public RotationConfig build() {
            prepare();
            validate();
            return new RotationConfig(this);
        }

        private void prepare() {
            if (timer == null) {
                timer = new Timer();
            }
            if (lock == null) {
                lock = new ReentrantReadWriteLock();
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
