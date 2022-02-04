/*
 * Copyright 2018-2022 Volkan Yazıcı
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

package com.vlkan.rfos.policy;

import com.vlkan.rfos.Rotatable;

/**
 * The policy to trigger a file rotation in {@link Rotatable}.
 */
public interface RotationPolicy {

    /**
     * Starts the policy. That is, if it is a time-based policy, it can schedule
     * the next rotation.
     *
     * @param rotatable the rotatable accessing this policy
     */
    void start(Rotatable rotatable);

    /**
     * Stops the policy. That is, if it is a time-based policy, it can cancel
     * the scheduled next rotation task.
     */
    default void stop() {}

    /**
     * @return {@code true}, if the policy intercepts write operations via
     * {@link #acceptWrite(long)} method
     */
    boolean isWriteSensitive();

    /**
     * Invoked before every write operation, if {@link #isWriteSensitive()}
     * returns {@code true}.
     *
     * @param byteCount the number of bytes written to the active file so far,
     *                  including the ones about to be written right now
     */
    void acceptWrite(long byteCount);

}
