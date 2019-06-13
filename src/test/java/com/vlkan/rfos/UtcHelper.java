package com.vlkan.rfos;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public enum UtcHelper {;

    public static final ZoneId ZONE_ID = ZoneId.of("UTC");

    public static final DateTimeFormatter INSTANT_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withLocale(Locale.US)
            .withZone(ZONE_ID);

}
