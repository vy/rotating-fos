package com.vlkan.rfos.policy;

import com.vlkan.rfos.Clock;
import com.vlkan.rfos.Rotatable;
import com.vlkan.rfos.RotationCallback;

import java.io.File;
import java.util.Objects;
import java.util.Timer;

public class RotationPolicyContext {

    private Clock clock;

    private Timer timer;

    private Rotatable rotatable;

    private File file;

    private RotationCallback callback;

    private RotationPolicyContext(Builder builder) {
        this.clock = builder.clock;
        this.timer = builder.timer;
        this.rotatable = builder.rotatable;
        this.file = builder.file;
        this.callback = builder.callback;
    }

    public Clock getClock() {
        return clock;
    }

    public Timer getTimer() {
        return timer;
    }

    public Rotatable getRotatable() {
        return rotatable;
    }

    public File getFile() {
        return file;
    }

    public RotationCallback getCallback() {
        return callback;
    }

    @Override
    public boolean equals(Object instance) {
        if (this == instance) return true;
        if (instance == null || getClass() != instance.getClass()) return false;
        RotationPolicyContext that = (RotationPolicyContext) instance;
        return Objects.equals(clock, that.clock) &&
                Objects.equals(timer, that.timer) &&
                Objects.equals(rotatable, that.rotatable) &&
                Objects.equals(file, that.file) &&
                Objects.equals(callback, that.callback);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clock, timer, rotatable, file, callback);
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
