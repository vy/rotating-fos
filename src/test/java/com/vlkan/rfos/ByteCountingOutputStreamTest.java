/*
 * Copyright 2018-2024 Volkan Yazıcı <volkan@yazi.ci>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permits and
 * limitations under the License.
 */

package com.vlkan.rfos;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Random;

class ByteCountingOutputStreamTest {

    private static final Random RANDOM = new Random(0);

    @Test
    void test() throws IOException {
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
