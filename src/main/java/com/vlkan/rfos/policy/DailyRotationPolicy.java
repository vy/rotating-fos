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

package com.vlkan.rfos.policy;

import com.vlkan.rfos.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class DailyRotationPolicy extends TimeBasedRotationPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyRotationPolicy.class);

    private static final DailyRotationPolicy INSTANCE = new DailyRotationPolicy();

    private DailyRotationPolicy() {
        // Do nothing.
    }

    public static DailyRotationPolicy getInstance() {
        return INSTANCE;
    }

    @Override
    public Instant getTriggerInstant(Clock clock) {
        return clock.midnight();
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    @Override
    public String toString() {
        return "DailyRotationPolicy";
    }

}
