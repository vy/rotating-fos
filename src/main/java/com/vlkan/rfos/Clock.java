package com.vlkan.rfos;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public interface Clock {

    Instant now();

    Instant midnight();

    Instant sundayMidnight();

    String ISO_LOCAL_DATE_TIME_WITH_MILLIS = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(ISO_LOCAL_DATE_TIME_WITH_MILLIS) ;

    // TODO Should we move this to a util class? - Lukas Bradley
    static Instant parse(String isoLocalDateTimeWithMillis) {
        return Instant.from(FORMATTER.parse(isoLocalDateTimeWithMillis)) ;
    }

}
