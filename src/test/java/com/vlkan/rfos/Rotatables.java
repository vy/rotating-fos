/*
 * Copyright 2019 Volkan Yazıcı
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permits and
 * limitations under the License.
 */

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
