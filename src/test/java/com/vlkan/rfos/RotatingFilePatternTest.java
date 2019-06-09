package com.vlkan.rfos;

import org.assertj.core.api.ThrowableAssert;
import org.junit.Test;

import java.io.File;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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
        for (final String invalidPattern : invalidPatterns) {
            ThrowableAssert.ThrowingCallable callable = new ThrowableAssert.ThrowingCallable() {
                @Override
                public void call() throws Throwable {
                    new RotatingFilePattern(invalidPattern);
                }
            };
            assertThatThrownBy(callable)
                    .as("pattern=%s", invalidPattern)
                    .isInstanceOf(RotatingFilePatternException.class);
        }
    }

    @Test
    public void test_valid_patterns() {
        Instant dateTime = Instant.now();
        Map<String, File> fileByPattern = new LinkedHashMap<>();
        fileByPattern.put("%d{yyyy-mm-dd}", new File(DateTimeFormatter.ofPattern("yyyy-mm-dd").format(dateTime)));
        fileByPattern.put("%d{yyyy-mm-dd}.log/foo%%", new File(String.format("%s.log/foo%%", DateTimeFormatter.ofPattern("yyyy-mm-dd").format(dateTime))));
        fileByPattern.put("tmp/%d{yyyymmdd-HH}", new File(String.format("tmp/%s", DateTimeFormatter.ofPattern("yyyymmdd-HH").format(dateTime))));
        fileByPattern.put("/tmp/%d{yyyymmdd-HH}", new File(String.format("/tmp/%s", DateTimeFormatter.ofPattern("yyyymmdd-HH").format(dateTime))));
        fileByPattern.put(
                "%d{yyyy}/%d{mm}/%d{yyyymmdd}.log",
                new File(String.format(
                        "%s/%s/%s.log",
                        DateTimeFormatter.ofPattern("yyyy").format(dateTime),
                        DateTimeFormatter.ofPattern("mm").format(dateTime),
                        DateTimeFormatter.ofPattern("yyyymmdd").format(dateTime))));
        for (String pattern : fileByPattern.keySet()) {
            File expectedFile = fileByPattern.get(pattern);
            File actualFile = new RotatingFilePattern(pattern).create(dateTime);
            assertThat(actualFile).as("pattern=%s", pattern).isEqualTo(expectedFile);
        }
    }

}
