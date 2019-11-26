package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.OutputStream;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

class RecordingRotationCallback implements RotationCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecordingRotationCallback.class);

    interface CallContext {}

    final BlockingQueue<CallContext> receivedCallContexts;

    RecordingRotationCallback(int receivedCallContextCapacity) {
        this.receivedCallContexts = new LinkedBlockingQueue<>(receivedCallContextCapacity);
    }

    static final class OnTriggerContext implements CallContext {

        final RotationPolicy policy;

        final Instant instant;

        private OnTriggerContext(RotationPolicy policy, Instant instant) {
            this.policy = policy;
            this.instant = instant;
        }

    }

    @Override
    public void onTrigger(RotationPolicy policy, Instant instant) {
        LOGGER.trace("onTrigger({}, {})", policy, instant);
        try {
            receivedCallContexts.put(new OnTriggerContext(policy, instant));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    static final class OnOpenContext implements CallContext {

        final RotationPolicy policy;

        final Instant instant;

        final OutputStream outputStream;

        private OnOpenContext(RotationPolicy policy, Instant instant, OutputStream outputStream) {
            this.policy = policy;
            this.instant = instant;
            this.outputStream = outputStream;
        }

    }

    @Override
    public void onOpen(RotationPolicy policy, Instant instant, OutputStream outputStream) {
        LOGGER.trace("onOpen({}, {})", policy, instant);
        try {
            receivedCallContexts.put(new OnOpenContext(policy, instant, outputStream));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    static final class OnSuccessContext implements CallContext {

        final RotationPolicy policy;

        final Instant instant;

        final File file;

        private OnSuccessContext(RotationPolicy policy, Instant instant, File file) {
            this.policy = policy;
            this.instant = instant;
            this.file = file;
        }

    }

    @Override
    public void onSuccess(RotationPolicy policy, Instant instant, File file) {
        LOGGER.trace("onSuccess({}, {}, {})", policy, instant, file);
        try {
            receivedCallContexts.put(new OnSuccessContext(policy, instant, file));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    static final class OnFailureContext implements CallContext {

        final RotationPolicy policy;

        final Instant instant;

        final File file;

        final Exception error;

        private OnFailureContext(RotationPolicy policy, Instant instant, File file, Exception error) {
            this.policy = policy;
            this.instant = instant;
            this.file = file;
            this.error = error;
        }

    }

    @Override
    public void onFailure(RotationPolicy policy, Instant instant, File file, Exception error) {
        LOGGER.trace("onFailure({}, {}, {}, {})", policy, instant, file, error);
        try {
            receivedCallContexts.put(new OnFailureContext(policy, instant, file, error));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

}
