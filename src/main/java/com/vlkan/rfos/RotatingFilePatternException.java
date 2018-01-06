package com.vlkan.rfos;

public class RotatingFilePatternException extends IllegalArgumentException {

    public RotatingFilePatternException(String message) {
        super(message);
    }

    public RotatingFilePatternException(String message, Throwable cause) {
        super(message, cause);
    }

}
