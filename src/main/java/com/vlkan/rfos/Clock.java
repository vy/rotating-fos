package com.vlkan.rfos;

import org.joda.time.LocalDateTime;

public interface Clock {

    LocalDateTime now();

    LocalDateTime midnight();

    LocalDateTime sundayMidnight();

}
