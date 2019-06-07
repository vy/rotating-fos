package com.vlkan.rfos;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Random;

public class ByteCountingOutputStreamTest {

    private static final Random RANDOM = new Random(0);

    @Test
    public void test() throws IOException {
        for (int testIndex = 0; testIndex < 100; testIndex++) {
            int textSize = RANDOM.nextInt(1024 * 1024);
            String text = generateRandomString(textSize);
            test(text);
        }
    }

    private void test(String text) throws IOException {
        String encoding = "UTF-8";
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            try (ByteCountingOutputStream byteCountingOutputStream = new ByteCountingOutputStream(byteArrayOutputStream, 0)) {
                try (PrintStream printStream = new PrintStream(byteCountingOutputStream, false, encoding)) {
                    printStream.print(text);
                }
                long actualSize = byteCountingOutputStream.size();
                int expectedSize = text.getBytes(encoding).length;
                Assertions.assertThat(actualSize).isEqualTo(expectedSize);
            }
        }
    }

    private static String generateRandomString(int length) {
        int maxOffset = Character.MAX_VALUE - Character.MIN_VALUE;
        StringBuilder builder = new StringBuilder();
        while (length-- > 0) {
            int offset = RANDOM.nextInt(maxOffset);
            char c = (char) (Character.MIN_VALUE + offset);
            builder.append(c);
        }
        return builder.toString();
    }

}
