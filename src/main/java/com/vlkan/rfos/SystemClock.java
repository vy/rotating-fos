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

import java.time.*;

public class SystemClock implements Clock {

    private static final SystemClock INSTANCE = new SystemClock();

    private static final ZoneId UTC_ZONE_ID = ZoneId.of("UTC");

    SystemClock() {
        // Do nothing.
    }

    public static SystemClock getInstance() {
        return INSTANCE;
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public Instant midnight() {
        Instant instant = now();
        ZonedDateTime utcInstant = instant.atZone(UTC_ZONE_ID);
        return LocalDate
                .from(utcInstant)
                .atStartOfDay()
                .plusDays(1)
                .toInstant(ZoneOffset.UTC);
    }

    @Override
    public Instant sundayMidnight() {
        Instant instant = now();
        ZonedDateTime utcInstant = instant.atZone(UTC_ZONE_ID);
        LocalDateTime todayStart = LocalDate.from(utcInstant).atStartOfDay();
        int todayIndex = todayStart.getDayOfWeek().getValue() - 1;
        int sundayOffset = 7 - todayIndex;
        return todayStart
                .plusDays(sundayOffset)
                .toInstant(ZoneOffset.UTC);
    }

}
