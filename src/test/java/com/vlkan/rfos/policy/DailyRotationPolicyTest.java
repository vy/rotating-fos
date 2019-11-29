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

import com.vlkan.rfos.Clock;
import com.vlkan.rfos.Rotatable;
import com.vlkan.rfos.RotatingFilePattern;
import com.vlkan.rfos.RotationConfig;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DailyRotationPolicyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyRotationPolicyTest.class);

    @Test
    public void test() {

        // Create the scheduler mock.
        ScheduledExecutorService executorService = Mockito.mock(ScheduledExecutorService.class);
        Mockito
                .when(executorService.schedule(
                        Mockito.any(Runnable.class),
                        Mockito.anyLong(),
                        Mockito.same(TimeUnit.MILLISECONDS)))
                .thenAnswer(new Answer<Object>() {

                    private int invocationCount = 0;

                    @Override
                    public Object answer(InvocationOnMock invocation) {
                        Runnable runnable = invocation.getArgument(0);
                        if (++invocationCount < 3) {
                            runnable.run();
                        } else {
                            LOGGER.trace("skipping execution {invocationCount={}}", invocationCount);
                        }
                        return null;
                    }

                });

        // Create the clock mock.
        Clock clock = Mockito.mock(Clock.class);
        Instant midnight1 = Instant.parse("2017-12-29T00:00:00.000Z");
        long waitPeriod1Millis = 1_000;
        Instant now1 = midnight1.minus(Duration.ofMillis(waitPeriod1Millis));
        Instant midnight2 = Instant.parse("2017-12-30T00:00:00.000Z");
        long waitPeriod2Millis = 2_000;
        Instant now2 = midnight2.minus(Duration.ofMillis(waitPeriod2Millis));
        Mockito
                .when(clock.now())
                .thenReturn(now1)
                .thenReturn(now2);
        Mockito
                .when(clock.midnight())
                .thenReturn(midnight1)
                .thenReturn(midnight2);

        // Create the config.
        DailyRotationPolicy policy = DailyRotationPolicy.getInstance();
        File file = Mockito.mock(File.class);
        RotatingFilePattern filePattern = Mockito.mock(RotatingFilePattern.class);
        RotationConfig config = RotationConfig
                .builder()
                .file(file)
                .filePattern(filePattern)
                .clock(clock)
                .executorService(executorService)
                .policy(policy)
                .build();

        // Create the rotatable mock.
        Rotatable rotatable = Mockito.mock(Rotatable.class);
        Mockito.when(rotatable.getConfig()).thenReturn(config);

        // Start policy.
        policy.start(rotatable);

        // Verify the 1st execution.
        Mockito
                .verify(executorService)
                .schedule(
                        Mockito.any(Runnable.class),
                        Mockito.eq(waitPeriod1Millis),
                        Mockito.same(TimeUnit.MILLISECONDS));

        // Verify the 1st rotation.
        Mockito
                .verify(rotatable)
                .rotate(Mockito.same(policy), Mockito.eq(midnight1));

        // Verify the 2nd execution.
        Mockito
                .verify(executorService, Mockito.atLeastOnce())
                .schedule(
                        Mockito.any(Runnable.class),
                        Mockito.eq(waitPeriod2Millis),
                        Mockito.same(TimeUnit.MILLISECONDS));

        // Verify the 2nd rotation.
        Mockito
                .verify(rotatable)
                .rotate(Mockito.same(policy), Mockito.eq(midnight2));

    }

}
