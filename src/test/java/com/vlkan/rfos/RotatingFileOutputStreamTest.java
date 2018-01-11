package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;
import com.vlkan.rfos.policy.SizeBasedRotationPolicy;
import org.joda.time.LocalDateTime;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RotatingFileOutputStreamTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RotatingFileOutputStreamTest.class);

    @Test
    public void test() throws Exception {
        test(true);
        test(false);
    }

    private void test(boolean compress) throws Exception {

        // Set file names.
        String className = RotatingFileOutputStream.class.getSimpleName();
        File file = new File(Filesystem.tmpDir(), className + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(Filesystem.tmpDir(), className + "-%d{yyyy}.log").getAbsolutePath();
        String rotatedFileNameSuffix = compress ? ".gz" : "";
        File rotatedFile = new File(fileNamePattern.replace("%d{yyyy}", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))) + rotatedFileNameSuffix);
        String rotatedFileName = rotatedFile.getAbsolutePath();

        // Cleanup files.
        Filesystem.delete(fileName);
        Filesystem.delete(rotatedFileName);

        // Create the rotation callback.
        final BlockingQueue<RotationPolicy> callbackSuccessPolicies = new LinkedBlockingDeque<>(1);
        final BlockingQueue<File> callbackSuccessFiles = new LinkedBlockingDeque<>(1);
        RotationCallback callback = new RotationCallback() {

            @Override
            public void onTrigger(RotationPolicy policy, LocalDateTime dateTime) {
                LOGGER.trace("onTrigger({}, {})", policy, dateTime);
            }

            @Override
            public void onConflict(RotationPolicy policy, LocalDateTime dateTime) {
                LOGGER.trace("onConflict({}, {})", policy, dateTime);
            }

            @Override
            public void onSuccess(RotationPolicy policy, LocalDateTime dateTime, File file) {
                LOGGER.trace("onSuccess({}, {}, {})", policy, dateTime, file);
                try {
                    callbackSuccessPolicies.put(policy);
                    callbackSuccessFiles.put(file);
                } catch (InterruptedException ignored) {
                    LOGGER.warn("onSuccess() is interrupted");
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public void onFailure(RotationPolicy policy, LocalDateTime dateTime, File file, Exception error) {
                LOGGER.trace("onFailure({}, {}, {}, {})", policy, dateTime, file, error);
            }

        };

        // Create the timer.
        final BlockingQueue<Object> timerTaskExecutionPermits = new LinkedBlockingDeque<>();
        final BlockingQueue<Long> timerDelays = new LinkedBlockingDeque<>(1);
        final BlockingQueue<Long> timerPeriods = new LinkedBlockingDeque<>(1);
        final BlockingQueue<Integer> timerTaskExecutionCounts = new LinkedBlockingDeque<>(1);
        Timer timer = new Timer() {
            @Override
            public void schedule(final TimerTask task, final long delay, final long period) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int executionCount = 0;
                        boolean first = true;
                        Thread thread = Thread.currentThread();
                        while (true) {
                            LOGGER.trace("awaiting task execution permit");
                            try {
                                timerTaskExecutionPermits.poll(1, TimeUnit.SECONDS);
                            } catch (InterruptedException ignored) {
                                LOGGER.warn("task execution permit await is interrupted");
                                thread.interrupt();
                            }
                            if (first) {
                                LOGGER.trace("executing task for the first time");
                                first = false;
                                try {
                                    timerDelays.put(delay);
                                    timerPeriods.put(period);
                                } catch (InterruptedException ignored) {
                                    LOGGER.warn("timer delay & period push is interrupted");
                                    thread.interrupt();
                                }
                            } else {
                                LOGGER.trace("executing task");
                            }
                            task.run();
                            LOGGER.trace("pushing task execution count");
                            try {
                                timerTaskExecutionCounts.put(++executionCount);
                            } catch (InterruptedException ignored) {
                                LOGGER.warn("timer task execution count push is interrupted");
                                thread.interrupt();
                            }
                        }
                    }
                }).start();
            }
        };

        // Create the stream.
        int checkIntervalMillis = 50;
        int maxByteCount = 1024;
        SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy(checkIntervalMillis, maxByteCount);
        RotationConfig config = RotationConfig
                .builder()
                .compress(compress)
                .file(fileName)
                .filePattern(fileNamePattern)
                .timer(timer)
                .policy(policy)
                .callback(callback)
                .build();
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Allow timer to proceed.
        LOGGER.trace("pushing task execution permit");
        timerTaskExecutionPermits.put(1L);

        // Verify the timer call.
        Long timerDelay = timerDelays.poll(1, TimeUnit.SECONDS);
        assertThat(timerDelay).isEqualTo(0L);
        Long timerPeriod = timerPeriods.poll(1, TimeUnit.SECONDS);
        assertThat(timerPeriod).isEqualTo(checkIntervalMillis);

        // Verify timer task is executed.
        Integer timerTaskExecutionCount1 = timerTaskExecutionCounts.poll(1, TimeUnit.SECONDS);
        assertThat(timerTaskExecutionCount1).isEqualTo(1);

        // Verify no rotations so far.
        RotationPolicy callbackSuccessPolicy1 = callbackSuccessPolicies.peek();
        assertThat(callbackSuccessPolicy1).isNull();
        File callbackSuccessFile1 = callbackSuccessFiles.peek();
        assertThat(callbackSuccessFile1).isNull();

        // Increase the size of the file just to the edge.
        LOGGER.trace("writing to file");
        for (int byteIndex = 0; byteIndex < maxByteCount; byteIndex++) {
            stream.write(byteIndex);
        }
        stream.flush();
        assertThat(file.length()).isEqualTo(maxByteCount);

        // Allow timer to proceed.
        LOGGER.trace("pushing task execution permit");
        timerTaskExecutionPermits.put(2L);

        // Verify timer task is executed.
        Integer timerTaskExecutionCount2 = timerTaskExecutionCounts.poll(1, TimeUnit.SECONDS);
        assertThat(timerTaskExecutionCount2).isEqualTo(2);

        // Verify no rotations so far.
        RotationPolicy callbackSuccessPolicy2 = callbackSuccessPolicies.peek();
        assertThat(callbackSuccessPolicy2).isNull();
        File callbackSuccessFile2 = callbackSuccessFiles.peek();
        assertThat(callbackSuccessFile2).isNull();

        // Push the file size off the threshold.
        LOGGER.trace("writing more to file");
        stream.write(maxByteCount);
        stream.flush();
        assertThat(file.length()).isEqualTo(maxByteCount + 1);

        // Allow timer to proceed.
        LOGGER.trace("pushing task execution permit");
        timerTaskExecutionPermits.put(3L);

        // Verify timer task is executed.
        Integer timerTaskExecutionCount3 = timerTaskExecutionCounts.poll(1, TimeUnit.SECONDS);
        assertThat(timerTaskExecutionCount3).isEqualTo(3);

        // Verify the rotation.
        RotationPolicy callbackSuccessPolicy3 = callbackSuccessPolicies.poll(1, TimeUnit.SECONDS);
        assertThat(callbackSuccessPolicy3).isEqualTo(policy);
        File callbackSuccessFile3 = callbackSuccessFiles.poll(1, TimeUnit.SECONDS);
        assertThat(callbackSuccessFile3).isEqualTo(rotatedFile);
        long callbackSuccessFile3Length = callbackSuccessFile3.length();
        if (compress) {
            assertThat(callbackSuccessFile3Length).isGreaterThan(0);
        } else {
            assertThat(callbackSuccessFile3Length).isEqualTo(maxByteCount + 1);
        }
        assertThat(file.length()).isEqualTo(0);

    }

}
