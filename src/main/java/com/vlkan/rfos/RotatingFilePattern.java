package com.vlkan.rfos;

import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class RotatingFilePattern {

    private static final char ESCAPE_CHAR = '%';

    private static final char DATE_TIME_DIRECTIVE_CHAR = 'd';

    private static final char DATE_TIME_BLOCK_START_CHAR = '{';

    private static final char DATE_TIME_BLOCK_END_CHAR = '}';

    private interface Field {

        void render(StringBuilder builder, LocalDateTime dateTime);

    }

    private static class TextField implements Field {

        private final String text;

        private TextField(String text) {
            this.text = text;
        }

        @Override
        public void render(StringBuilder builder, LocalDateTime ignored) {
            builder.append(text);
        }

    }

    private static class DateTimeField implements Field {

        private final DateTimeFormatter dateTimeFormatter;

        private DateTimeField(DateTimeFormatter dateTimeFormatter) {
            this.dateTimeFormatter = dateTimeFormatter;
        }

        @Override
        public void render(StringBuilder builder, LocalDateTime dateTime) {
            String formattedDateTime = dateTimeFormatter.print(dateTime);
            builder.append(formattedDateTime);
        }

    }

    private final List<Field> fields;

    public RotatingFilePattern(String pattern) {
        this.fields = readPattern(pattern, Locale.getDefault());
    }

    public RotatingFilePattern(String pattern, Locale locale) {
        this.fields = readPattern(pattern, locale);
    }

    private static List<Field> readPattern(String pattern, Locale locale) {

        List<Field> fields = new LinkedList<>();
        StringBuilder textBuilder = new StringBuilder();
        int totalCharCount = pattern.length();
        boolean foundDateTimeDirective = false;
        for (int charIndex = 0; charIndex < totalCharCount;) {

            char c0 = pattern.charAt(charIndex);
            if (c0 == ESCAPE_CHAR) {

                // Check if escape character is escaped.
                boolean hasOneMoreChar = (totalCharCount - charIndex - 1) > 0;
                if (hasOneMoreChar) {
                    char c1 = pattern.charAt(charIndex + 1);
                    if (c1 == ESCAPE_CHAR) {
                        textBuilder.append(c1);
                        charIndex += 2;
                        continue;
                    }
                }

                // Append collected text so far, if there is any.
                if (textBuilder.length() > 0) {
                    String text = textBuilder.toString();
                    TextField field = new TextField(text);
                    fields.add(field);
                    textBuilder = new StringBuilder();
                }

                // Try to read the directive.
                boolean hasSufficientDateTimeChars = (totalCharCount - charIndex - 3) > 0;
                if (hasSufficientDateTimeChars) {
                    char c1 = pattern.charAt(charIndex + 1);
                    if (c1 == DATE_TIME_DIRECTIVE_CHAR) {
                        int blockStartIndex = charIndex + 2;
                        char c2 = pattern.charAt(blockStartIndex);
                        if (c2 == DATE_TIME_BLOCK_START_CHAR) {
                            int blockEndIndex = pattern.indexOf(DATE_TIME_BLOCK_END_CHAR, blockStartIndex + 1);
                            if (blockEndIndex >= 0) {
                                String dateTimePattern = pattern.substring(blockStartIndex + 1, blockEndIndex);
                                DateTimeFormatter dateTimeFormatter;
                                try {
                                    dateTimeFormatter = DateTimeFormat.forPattern(dateTimePattern).withLocale(locale);
                                } catch (Exception error) {
                                    String message = String.format(
                                            "invalid date time pattern (position=%d, pattern=%s, dateTimePattern=%s)",
                                            charIndex, pattern, dateTimePattern);
                                    throw new RotatingFilePatternException(message, error);
                                }
                                DateTimeField dateTimeField = new DateTimeField(dateTimeFormatter);
                                fields.add(dateTimeField);
                                foundDateTimeDirective = true;
                                charIndex = blockEndIndex + 1;
                                continue;
                            }
                        }
                    }

                }

                // Escape character leads to a dead end.
                String message = String.format("invalid escape character (position=%d, pattern=%s)", charIndex, pattern);
                throw new RotatingFilePatternException(message);

            } else {
                textBuilder.append(c0);
                charIndex += 1;
            }

        }

        // Append collected text so far, if there is any.
        if (textBuilder.length() > 0) {
            String text = textBuilder.toString();
            TextField field = new TextField(text);
            fields.add(field);
        }

        // Bail out if could not locate any date time directives.
        if (!foundDateTimeDirective) {
            String message = String.format("missing date time directive (pattern=%s)", pattern);
            throw new RotatingFilePatternException(message);
        }

        // Return collected fields so far.
        return fields;

    }

    public File create(LocalDateTime dateTime) {
        StringBuilder pathNameBuilder = new StringBuilder();
        for (Field field : fields) {
            field.render(pathNameBuilder, dateTime);
        }
        String pathName = pathNameBuilder.toString();
        return new File(pathName);
    }

}
