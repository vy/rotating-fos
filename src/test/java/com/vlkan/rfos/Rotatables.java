package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;

import java.io.File;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;

public enum Rotatables {;

    public static Rotatable createSpyingRotatable(
            RotationConfig config,
            BlockingQueue<RotationPolicy> rotationPolicies,
            BlockingQueue<String> rotationInstantTexts) {
        return new Rotatable() {

            @Override
            public RotationConfig getConfig() {
                return config;
            }

            @Override
            public void rotate(RotationPolicy policy, Instant instant) {
                try {
                    rotationPolicies.put(policy);
                    String instantText = UtcHelper.INSTANT_FORMATTER.format(instant);
                    rotationInstantTexts.put(instantText);
                    config.getCallback().onSuccess(policy, instant, new File("/no/such/file"));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }

        };
    }

}
