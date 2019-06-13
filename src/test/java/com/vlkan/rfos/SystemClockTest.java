package com.vlkan.rfos;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SystemClockTest {

    private abstract static class GenericTest implements Runnable {

        abstract protected Map<String, List<String>> getCurrentDateTimeTextsByExpectedDateTimeText();

        abstract protected LocalDateTime getActualDateTime(Clock clock);

        @Override
        public void run() {
            Map<String, List<String>> currentDateTimeTextsByExpectedDateTimeText = getCurrentDateTimeTextsByExpectedDateTimeText();
            for (String expectedDateTimeText : currentDateTimeTextsByExpectedDateTimeText.keySet()) {
                List<String> currentDateTimeTexts = currentDateTimeTextsByExpectedDateTimeText.get(expectedDateTimeText);
                for (String currentDateTimeText : currentDateTimeTexts) {
                    testDateTime(currentDateTimeText, expectedDateTimeText);
                }
            }
        }

        private void testDateTime(String currentDateTimeText, String expectedDateTimeText) {
            final DateTime currentDateTime = DateTime.parse(currentDateTimeText);
            SystemClock clock = new SystemClock() {
                @Override
                protected DateTime currentDateTime() {
                    return currentDateTime;
                }
            };
            LocalDateTime actualDateTime = getActualDateTime(clock);
            String actualDateTimeText = actualDateTime.toString();
            assertThat(actualDateTimeText)
                    .as("currentDateTimeText=%s", currentDateTimeText)
                    .isEqualTo(expectedDateTimeText);
        }

    }

    @Test
    public void test_midnight() {

        // Create test cases.
        final Map<String, List<String>> currentDateTimeTextsByExpectedDateTimeText = new LinkedHashMap<>();
        currentDateTimeTextsByExpectedDateTimeText.put(
                "2017-01-02T00:00:00.000",
                Arrays.asList(
                        "2017-01-01T00:00:00.000",
                        "2017-01-01T01:00:00.000",
                        "2017-01-01T23:59:59.999"));
        currentDateTimeTextsByExpectedDateTimeText.put(
                "2017-12-30T00:00:00.000",
                Arrays.asList(
                        "2017-12-29T00:00:00.000",
                        "2017-12-29T01:00:00.000",
                        "2017-12-29T23:59:59.999"));
        currentDateTimeTextsByExpectedDateTimeText.put(
                "2018-01-01T00:00:00.000",
                Arrays.asList(
                        "2017-12-31T00:00:00.000",
                        "2017-12-31T01:00:00.000",
                        "2017-12-31T23:59:59.999"));

        // Execute tests.
        new GenericTest() {

            @Override
            protected Map<String, List<String>> getCurrentDateTimeTextsByExpectedDateTimeText() {
                return currentDateTimeTextsByExpectedDateTimeText;
            }

            @Override
            protected LocalDateTime getActualDateTime(Clock clock) {
                return clock.midnight();
            }

        }.run();

    }

    @Test
    public void test_sundayMidnight() {

        // Create test cases.
        final Map<String, List<String>> currentDateTimeTextsByExpectedDateTimeText = new LinkedHashMap<>();
        currentDateTimeTextsByExpectedDateTimeText.put(
                "2017-01-02T00:00:00.000",
                Arrays.asList(
                        "2017-01-01T00:00:00.000",
                        "2017-01-01T01:00:00.000",
                        "2017-01-01T23:59:59.999"));
        currentDateTimeTextsByExpectedDateTimeText.put(
                "2018-01-01T00:00:00.000",
                Arrays.asList(
                        "2017-12-25T00:00:00.000",
                        "2017-12-25T01:00:00.000",
                        "2017-12-25T01:59:59.999",
                        "2017-12-26T00:00:00.000",
                        "2017-12-26T01:00:00.000",
                        "2017-12-26T01:59:59.999",
                        "2017-12-31T00:00:00.000",
                        "2017-12-31T01:00:00.000",
                        "2017-12-31T01:59:59.999"));

        // Execute tests.
        new GenericTest() {

            @Override
            protected Map<String, List<String>> getCurrentDateTimeTextsByExpectedDateTimeText() {
                return currentDateTimeTextsByExpectedDateTimeText;
            }

            @Override
            protected LocalDateTime getActualDateTime(Clock clock) {
                return clock.sundayMidnight();
            }

        }.run();

    }

}
