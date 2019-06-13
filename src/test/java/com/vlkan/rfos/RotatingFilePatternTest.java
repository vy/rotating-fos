package com.vlkan.rfos;

import org.assertj.core.api.ThrowableAssert;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.Test;

import java.io.File;
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
        for (String invalidPattern : invalidPatterns) {
            ThrowableAssert.ThrowingCallable callable = () -> new RotatingFilePattern(invalidPattern);
            assertThatThrownBy(callable)
                    .as("pattern=%s", invalidPattern)
                    .isInstanceOf(RotatingFilePatternException.class);
        }
    }

    @Test
    public void test_valid_patterns() {
        LocalDateTime dateTime = LocalDateTime.now();
        Map<String, File> fileByPattern = new LinkedHashMap<>();
        fileByPattern.put("%d{yyyy-mm-dd}", new File(DateTimeFormat.forPattern("yyyy-mm-dd").print(dateTime)));
        fileByPattern.put("%d{yyyy-mm-dd}.log/foo%%", new File(String.format("%s.log/foo%%", DateTimeFormat.forPattern("yyyy-mm-dd").print(dateTime))));
        fileByPattern.put("tmp/%d{yyyymmdd-HH}", new File(String.format("tmp/%s", DateTimeFormat.forPattern("yyyymmdd-HH").print(dateTime))));
        fileByPattern.put("/tmp/%d{yyyymmdd-HH}", new File(String.format("/tmp/%s", DateTimeFormat.forPattern("yyyymmdd-HH").print(dateTime))));
        fileByPattern.put(
                "%d{yyyy}/%d{mm}/%d{yyyymmdd}.log",
                new File(String.format(
                        "%s/%s/%s.log",
                        DateTimeFormat.forPattern("yyyy").print(dateTime),
                        DateTimeFormat.forPattern("mm").print(dateTime),
                        DateTimeFormat.forPattern("yyyymmdd").print(dateTime))));
        for (String pattern : fileByPattern.keySet()) {
            File expectedFile = fileByPattern.get(pattern);
            File actualFile = new RotatingFilePattern(pattern).create(dateTime);
            assertThat(actualFile).as("pattern=%s", pattern).isEqualTo(expectedFile);
        }
    }

}
