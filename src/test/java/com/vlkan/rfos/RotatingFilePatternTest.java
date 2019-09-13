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

import org.assertj.core.api.ThrowableAssert;
import org.junit.Test;

import java.io.File;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RotatingFilePatternTest {

    @Test
    public void test_invalid_patterns() {
        String[] invalidPatterns = new String[]{
                "",
                "-",
                "%",
                "%d",
                "%d{",
                "%d}",
                "%%",
                "foo%",
                "foo%d",
                "foo%d{",
                "foo%d{T}",
                "foo%%"
        };
        for (String invalidPattern : invalidPatterns) {
            ThrowableAssert.ThrowingCallable callable = () -> RotatingFilePattern
                    .builder()
                    .pattern(invalidPattern)
                    .locale(Locale.US)
                    .timeZoneId(UtcHelper.ZONE_ID)
                    .build();
            assertThatThrownBy(callable)
                    .as("pattern=%s", invalidPattern)
                    .isInstanceOf(RotatingFilePatternException.class);
        }
    }

    @Test
    public void test_valid_patterns() {
        Instant instant = Instant.now();
        Map<String, File> fileByPattern = new LinkedHashMap<>();
        fileByPattern.put("%d{yyyy-MM-dd}", new File(formatInstant("yyyy-MM-dd", instant)));
        fileByPattern.put("%d{yyyy-MM-dd}.log/foo%%", new File(String.format("%s.log/foo%%", formatInstant("yyyy-MM-dd", instant))));
        fileByPattern.put("tmp/%d{yyyyMMdd-HH}", new File(String.format("tmp/%s", formatInstant("yyyyMMdd-HH", instant))));
        fileByPattern.put("/tmp/%d{yyyyMMdd-HH}", new File(String.format("/tmp/%s", formatInstant("yyyyMMdd-HH", instant))));
        fileByPattern.put(
                "%d{yyyy}/%d{MM}/%d{yyyyMMdd}.log",
                new File(String.format(
                        "%s/%s/%s.log",
                        formatInstant("yyyy", instant),
                        formatInstant("MM", instant),
                        formatInstant("yyyyMMdd", instant))));
        for (String pattern : fileByPattern.keySet()) {
            File expectedFile = fileByPattern.get(pattern);
            File actualFile = RotatingFilePattern
                    .builder()
                    .pattern(pattern)
                    .locale(Locale.US)
                    .timeZoneId(UtcHelper.ZONE_ID)
                    .build()
                    .create(instant);
            assertThat(actualFile).as("pattern=%s", pattern).isEqualTo(expectedFile);
        }
    }

    private static String formatInstant(String pattern, Instant instant) {
        return DateTimeFormatter
                .ofPattern(pattern)
                .withZone(UtcHelper.ZONE_ID)
                .format(instant);
    }

}
