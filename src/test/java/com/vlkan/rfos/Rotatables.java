package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;
import org.joda.time.LocalDateTime;

import java.io.File;
import java.util.concurrent.BlockingQueue;

public enum Rotatables {;

    public static Rotatable createSpyingRotatable(
            RotationConfig config,
            BlockingQueue<RotationPolicy> rotationPolicies,
            BlockingQueue<String> rotationDateTimeTexts) {
        return new Rotatable() {

            @Override
            public RotationConfig getConfig() {
                return config;
            }

            @Override
            public void rotate(RotationPolicy policy, LocalDateTime dateTime) {
                try {
                    rotationPolicies.put(policy);
                    rotationDateTimeTexts.put(dateTime.toString());
                    config.getCallback().onSuccess(policy, dateTime, new File("/no/such/file"));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }

        };
    }

}
