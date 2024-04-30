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

package com.vlkan.rfos.policy;

import com.vlkan.rfos.Clock;
import com.vlkan.rfos.Rotatable;
import com.vlkan.rfos.RotationConfig;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

class LineCountRotationPolicyTest {

    @Test
    void test_invalid_maxLineCount() {
        for (int invalidMaxLineCount : new int[] {-1, 0}) {
            Assertions
                    .assertThatThrownBy(() -> new LineCountRotationPolicy(invalidMaxLineCount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid count");
        }
    }

    @Test
    void test_maxLineCount_1(@TempDir(cleanup = CleanupMode.ON_SUCCESS) Path tempDir) throws IOException {

        // Create a non-empty initial file
        final Path tempFilePath = tempDir.resolve("test.log");
        Files.write(
                tempFilePath,
                new byte[]{'a', '\n'},
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.TRUNCATE_EXISTING);

        // Create a `Rotatable` mock
        Rotatable rotatable = Mockito.mock(Rotatable.class);
        RotationConfig rotationConfig = Mockito.mock(RotationConfig.class);
        Mockito.when(rotationConfig.getFile()).thenReturn(tempFilePath.toFile());
        Clock clock = Mockito.mock(Clock.class);
        Mockito.when(clock.now()).thenReturn(Instant.EPOCH);
        Mockito.when(rotationConfig.getClock()).thenReturn(clock);
        Mockito.when(rotatable.getConfig()).thenReturn(rotationConfig);

        // Create and start the policy
        LineCountRotationPolicy policy = new LineCountRotationPolicy(1);
        policy.start(rotatable);

        // Verify the `Rotatable` in order
        InOrder inOrder = Mockito.inOrder(rotatable);

        // Test `acceptWrite(int)`
        policy.acceptWrite('b');
        policy.acceptWrite('\n');
        inOrder.verify(rotatable).rotate(Mockito.same(policy), Mockito.any());
        policy.acceptWrite('c');
        policy.acceptWrite('\n');
        inOrder.verify(rotatable).rotate(Mockito.same(policy), Mockito.any());
        policy.acceptWrite('d');
        inOrder.verify(rotatable, Mockito.never()).rotate(Mockito.same(policy), Mockito.any());

        // Test `acceptWrite(byte[])`
        policy.acceptWrite("b\nc\nd".getBytes(StandardCharsets.US_ASCII));
        inOrder.verify(rotatable).rotate(Mockito.same(policy), Mockito.any());

        // Test `acceptWrite(byte[],int,int)`
        policy.acceptWrite(("\n\n" + "b\nc\nd" + "\n\n").getBytes(StandardCharsets.US_ASCII), 2, 5);
        inOrder.verify(rotatable).rotate(Mockito.same(policy), Mockito.any());

    }

}
