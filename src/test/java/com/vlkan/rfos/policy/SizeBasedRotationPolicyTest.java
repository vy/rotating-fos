/*
 * Copyright 2019 Volkan Yazıcı
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

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class SizeBasedRotationPolicyTest {

    @Test
    public void test_invalid_maxByteCount() {
        for (int invalidMaxByteCount : new int[] {-1, 0}) {
            Assertions
                    .assertThatThrownBy(() -> new SizeBasedRotationPolicy(invalidMaxByteCount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid size");
        }
    }

}
