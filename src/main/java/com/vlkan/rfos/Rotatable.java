/*
 * Copyright 2018-2024 Volkan Yazıcı <volkan@yazi.ci>
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

import java.time.Instant;

/**
 * Interface for representing a rotatable object to policies.
 *
 * @see RotatingFileOutputStream
 * @see RotationPolicy
 */
public interface Rotatable {

    /**
     * Triggers a rotation originating from the given policy at the given instant.
     *
     * @param policy the triggering policy, can be {@code null}
     * @param instant the trigger instant
     */
    void rotate(RotationPolicy policy, Instant instant);

    /**
     * Gets the configuration employed.
     *
     * @return the configuration employed
     */
    RotationConfig getConfig();

}
