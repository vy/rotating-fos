package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;
import com.vlkan.rfos.policy.SizeBasedRotationPolicy;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
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
    public void test_write_insensitive_policy() throws Exception {
        test_write_insensitive_policy(false);
    }

    @Test
    public void test_write_insensitive_policy_with_compression() throws Exception {
        test_write_insensitive_policy(true);
    }

    private static final class RotationCallbackRecorder {

        private final BlockingQueue<RotationPolicy> callbackSuccessPolicies = new LinkedBlockingDeque<>(1);

        private final BlockingQueue<File> callbackSuccessFiles = new LinkedBlockingDeque<>(1);

        private final RotationCallback callback = new RotationCallback() {

            @Override
            public void onTrigger(RotationPolicy policy, Instant dateTime) {
                LOGGER.trace("onTrigger({}, {})", policy, dateTime);
            }

            @Override
            public void onSuccess(RotationPolicy policy, Instant dateTime, File file) {
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
            public void onFailure(RotationPolicy policy, Instant dateTime, File file, Exception error) {
                LOGGER.trace("onFailure({}, {}, {}, {})", policy, dateTime, file, error);
            }

        };

    }

    private void test_write_insensitive_policy(boolean compress) throws Exception {

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

        // Create the timer which is advanced by permits in a queue.
        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
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
        RotationCallbackRecorder callbackRecorder = new RotationCallbackRecorder();
        RotationConfig config = RotationConfig
                .builder()
                .compress(compress)
                .file(fileName)
                .filePattern(fileNamePattern)
                .timer(timer)
                .policy(policy)
                .callback(callbackRecorder.callback)
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
        RotationPolicy callbackSuccessPolicy1 = callbackRecorder.callbackSuccessPolicies.peek();
        assertThat(callbackSuccessPolicy1).isNull();
        File callbackSuccessFile1 = callbackRecorder.callbackSuccessFiles.peek();
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
        RotationPolicy callbackSuccessPolicy2 = callbackRecorder.callbackSuccessPolicies.peek();
        assertThat(callbackSuccessPolicy2).isNull();
        File callbackSuccessFile2 = callbackRecorder.callbackSuccessFiles.peek();
        assertThat(callbackSuccessFile2).isNull();

        // Push the file size off the threshold.
        LOGGER.trace("writing more byte to file");
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
        RotationPolicy callbackSuccessPolicy3 = callbackRecorder.callbackSuccessPolicies.poll(1, TimeUnit.SECONDS);
        assertThat(callbackSuccessPolicy3).isEqualTo(policy);
        File callbackSuccessFile3 = callbackRecorder.callbackSuccessFiles.poll(1, TimeUnit.SECONDS);
        assertThat(callbackSuccessFile3).isNotNull().isEqualTo(rotatedFile);
        long callbackSuccessFile3Length = callbackSuccessFile3.length();
        if (compress) {
            assertThat(callbackSuccessFile3Length).isGreaterThan(0);
        } else {
            assertThat(callbackSuccessFile3Length).isEqualTo(maxByteCount + 1);
        }
        assertThat(file.length()).isEqualTo(0);

    }

    @Test
    public void test_write_sensitive_policy() throws Exception {

        // Set file names.
        String className = RotatingFileOutputStream.class.getSimpleName();
        File file = new File(Filesystem.tmpDir(), className + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(Filesystem.tmpDir(), className + "-%d{yyyy}.log").getAbsolutePath();
        File rotatedFile = new File(fileNamePattern.replace("%d{yyyy}", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))));
        String rotatedFileName = rotatedFile.getAbsolutePath();

        // Cleanup files.
        Filesystem.delete(fileName);
        Filesystem.delete(rotatedFileName);

        // Create the stream.
        int maxByteCount = 1024;
        SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy(0, maxByteCount);
        RotationCallbackRecorder callbackRecorder = new RotationCallbackRecorder();
        Timer timer = Mockito.mock(Timer.class);
        RotationConfig config = RotationConfig
                .builder()
                .file(fileName)
                .filePattern(fileNamePattern)
                .timer(timer)
                .policy(policy)
                .callback(callbackRecorder.callback)
                .build();
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Verify no rotations so far.
        RotationPolicy callbackSuccessPolicy1 = callbackRecorder.callbackSuccessPolicies.peek();
        assertThat(callbackSuccessPolicy1).isNull();
        File callbackSuccessFile1 = callbackRecorder.callbackSuccessFiles.peek();
        assertThat(callbackSuccessFile1).isNull();

        // Increase the size of the file just to the edge.
        LOGGER.trace("writing to file");
        for (int byteIndex = 0; byteIndex < maxByteCount; byteIndex++) {
            stream.write(byteIndex);
        }
        stream.flush();
        assertThat(file.length()).isEqualTo(maxByteCount);

        // Verify no rotations so far.
        RotationPolicy callbackSuccessPolicy2 = callbackRecorder.callbackSuccessPolicies.peek();
        assertThat(callbackSuccessPolicy2).isNull();
        File callbackSuccessFile2 = callbackRecorder.callbackSuccessFiles.peek();
        assertThat(callbackSuccessFile2).isNull();

        // Push the file size off the threshold.
        LOGGER.trace("writing more byte to file");
        stream.write(maxByteCount);

        // Verify the rotation.
        RotationPolicy callbackSuccessPolicy3 = callbackRecorder.callbackSuccessPolicies.poll(1, TimeUnit.SECONDS);
        assertThat(callbackSuccessPolicy3).isEqualTo(policy);
        File callbackSuccessFile3 = callbackRecorder.callbackSuccessFiles.poll(1, TimeUnit.SECONDS);
        assertThat(callbackSuccessFile3).isNotNull().isEqualTo(rotatedFile);
        long callbackSuccessFile3Length = callbackSuccessFile3.length();
        assertThat(callbackSuccessFile3Length).isEqualTo(maxByteCount);
        assertThat(file.length()).isEqualTo(1);

    }

    @Test
    public void test_empty_files_are_not_rotated() throws Exception {

        // Set file names.
        String className = RotatingFileOutputStream.class.getSimpleName();
        File file = new File(Filesystem.tmpDir(), className + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(Filesystem.tmpDir(), className + "-%d{yyyy}.log").getAbsolutePath();
        File rotatedFile = new File(fileNamePattern.replace("%d{yyyy}", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))));
        String rotatedFileName = rotatedFile.getAbsolutePath();

        // Cleanup files.
        Filesystem.delete(fileName);
        Filesystem.delete(rotatedFileName);

        // Create the stream.
        int maxByteCount = 1024;
        SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy(0, maxByteCount);
        RotationCallbackRecorder callbackRecorder = new RotationCallbackRecorder();
        Timer timer = Mockito.mock(Timer.class);
        RotationConfig config = RotationConfig
                .builder()
                .file(fileName)
                .filePattern(fileNamePattern)
                .timer(timer)
                .policy(policy)
                .callback(callbackRecorder.callback)
                .build();
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Verify no rotations so far.
        RotationPolicy callbackSuccessPolicy1 = callbackRecorder.callbackSuccessPolicies.peek();
        assertThat(callbackSuccessPolicy1).isNull();
        File callbackSuccessFile1 = callbackRecorder.callbackSuccessFiles.peek();
        assertThat(callbackSuccessFile1).isNull();

        // Write payload with size exceeding the threshold.
        LOGGER.trace("writing to file");
        byte[] payload = new byte[2 * maxByteCount];
        for (int byteIndex = 0; byteIndex < payload.length; byteIndex++) {
            payload[byteIndex] = (byte) byteIndex;
        }
        stream.write(payload);
        stream.flush();
        assertThat(file.length()).isEqualTo(payload.length);

        // Verify no rotations so far.
        RotationPolicy callbackSuccessPolicy2 = callbackRecorder.callbackSuccessPolicies.peek();
        assertThat(callbackSuccessPolicy2).isNull();
        File callbackSuccessFile2 = callbackRecorder.callbackSuccessFiles.peek();
        assertThat(callbackSuccessFile2).isNull();

    }

}
