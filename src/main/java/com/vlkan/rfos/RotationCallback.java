/*
 * Copyright 2018-2020 Volkan Yazıcı
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
import java.io.OutputStream;
import java.time.Instant;

public interface RotationCallback {

    /**
     * Invoked by {@link RotatingFileOutputStream} at the beginning of every
     * {@link RotatingFileOutputStream#rotate(RotationPolicy, Instant)} call.
     * The callback will be awaited in a synchronized block to complete the
     * rotation.
     */
    void onTrigger(RotationPolicy policy, Instant instant);

    /**
     * Invoked by {@link RotatingFileOutputStream} at start or during rotation.
     * At start, {@code policy} argument will be null. The callback will be
     * awaited in a synchronized block to complete the rotation.
     * @param policy null at start, policy at rotation
     */
    void onOpen(RotationPolicy policy, Instant instant, OutputStream stream);

    /**
     * Invoked by {@link RotatingFileOutputStream} prior to closing the internal
     * {@link OutputStream}. The callback might be awaited in a synchronized
     * block to complete the rotation. Modifications to the file at this stage
     * will not be reflected back to the policies.
     * @param policy null on {@link RotatingFileOutputStream#close()}, policy at rotation
     */
    void onClose(RotationPolicy policy, Instant instant, OutputStream stream);

    /**
     * Invoked by {@link RotatingFileOutputStream} after a successful rotation.
     * The callback will be awaited in a synchronized block to complete the rotation.
     */
    void onSuccess(RotationPolicy policy, Instant instant, File file);

    /**
     * Invoked by {@link RotatingFileOutputStream} after a failed rotation attempt.
     * The call might be awaited in a synchronized block to complete the rotation.
     */
    void onFailure(RotationPolicy policy, Instant instant, File file, Exception error);

}
