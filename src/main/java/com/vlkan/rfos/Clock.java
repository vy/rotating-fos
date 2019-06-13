package com.vlkan.rfos;

import java.time.Instant;

public interface Clock {

    Instant now();

    Instant midnight();

    Instant sundayMidnight();

}
