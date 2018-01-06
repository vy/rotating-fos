package com.vlkan.rfos.policy;

import com.vlkan.rfos.Clock;
import com.vlkan.rfos.Rotatable;
import com.vlkan.rfos.RotationCallback;

import java.io.File;
import java.util.Objects;
import java.util.Timer;

public class RotationPolicyContext {

    private final Builder builder;

    private RotationPolicyContext(Builder builder) {
        this.builder = builder;
    }

    public Clock getClock() {
        return builder.clock;
    }

    public Timer getTimer() {
        return builder.timer;
    }

    public Rotatable getRotatable() {
        return builder.rotatable;
    }

    public File getFile() {
        return builder.file;
    }

    public RotationCallback getCallback() {
        return builder.callback;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Clock clock;

        private Timer timer;

        private Rotatable rotatable;

        private File file;

        private RotationCallback callback;

        private Builder() {
            // Do nothing.
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder timer(Timer timer) {
            this.timer = timer;
            return this;
        }

        public Builder rotatable(Rotatable rotatable) {
            this.rotatable = rotatable;
            return this;
        }

        public Builder file(File file) {
            this.file = file;
            return this;
        }

        public Builder callback(RotationCallback callback) {
            this.callback = callback;
            return this;
        }

        public RotationPolicyContext build() {
            validate();
            return new RotationPolicyContext(this);
        }

        private void validate() {
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(timer, "timer");
            Objects.requireNonNull(rotatable, "rotatable");
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(callback, "callback");
        }

    }

}
