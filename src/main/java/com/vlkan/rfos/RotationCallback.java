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
import java.io.OutputStream;
import java.time.Instant;

public interface RotationCallback {

    /**
     * Triggered by the relevant {@link RotationPolicy} prior to rotation.
     */
    void onTrigger(RotationPolicy policy, Instant instant);

    /**
     * Triggered by {@link RotatingFileOutputStream} either at start or during
     * rotation. At start, {@code policy} argument will be null. During rotation,
     * the callback will be awaited to complete the rotation, hence make sure
     * that the method doesn't block.
     */
    void onOpen(RotationPolicy policy, Instant instant, OutputStream stream);

    /**
     * Triggered by {@link RotatingFileOutputStream} after a successful rotation.
     * Note that the callback will be awaited to complete the rotation, hence make
     * sure that the method doesn't block.
     */
    void onSuccess(RotationPolicy policy, Instant instant, File file);

    /**
     * Triggered by {@link RotatingFileOutputStream} after a failed rotation attempt.
     * Note that the call might get executed within the synchronized block, hence
     * make sure that the method doesn't block.
     */
    void onFailure(RotationPolicy policy, Instant instant, File file, Exception error);

}
