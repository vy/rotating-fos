package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;

public class RotatingFileOutputStreamConfig {

    private final Builder builder;

    private RotatingFileOutputStreamConfig(Builder builder) {
        this.builder = builder;
    }

    public File getFile() {
        return builder.file;
    }

    public RotatingFilePattern getFilePattern() {
        return builder.filePattern;
    }

    public Timer getTimer() {
        return builder.timer;
    }

    public Set<RotationPolicy> getPolicies() {
        return builder.policies;
    }

    public boolean isAppend() {
        return builder.append;
    }

    public boolean isCompress() {
        return builder.compress;
    }

    public Clock getClock() {
        return builder.clock;
    }

    public RotationCallback getCallback() {
        return builder.callback;
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

        public RotatingFileOutputStreamConfig build() {
            prepare();
            validate();
            return new RotatingFileOutputStreamConfig(this);
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
