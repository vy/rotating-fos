/*
 * Copyright 2018-2021 Volkan Yazıcı
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.OutputStream;
import java.time.Instant;

/**
 * Callback logging every intercepted operation.
 */
public class LoggingRotationCallback implements RotationCallback {

    private static final LoggingRotationCallback INSTANCE = new LoggingRotationCallback();

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingRotationCallback.class);

    private LoggingRotationCallback() {
        // Do nothing.
    }

    public static LoggingRotationCallback getInstance() {
        return INSTANCE;
    }

    @Override
    public void onTrigger(RotationPolicy policy, Instant instant) {
        LOGGER.debug("rotation trigger {policy={}, instant={}}", policy, instant);
    }

    @Override
    public void onOpen(RotationPolicy policy, Instant instant, OutputStream ignored) {
        LOGGER.debug("file open {policy={}, instant={}}", policy, instant);
    }

    @Override
    public void onClose(RotationPolicy policy, Instant instant, OutputStream stream) {
        LOGGER.debug("file close {policy={}, instant={}}", policy, instant);
    }

    @Override
    public void onSuccess(RotationPolicy policy, Instant instant, File file) {
        LOGGER.debug("rotation success {policy={}, instant={}, file={}}", policy, instant, file);
    }

    @Override
    public void onFailure(RotationPolicy policy, Instant instant, File file, Exception error) {
        String message = String.format("rotation failure {policy=%s, instant=%s, file=%s}", policy, instant, file);
        LOGGER.error(message, error);
    }

}
