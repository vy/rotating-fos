/*
 * Copyright 2018-2021 Volkan Yazıcı
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

import com.vlkan.rfos.policy.RotationPolicy;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

public class RotationConfig {

    private static final boolean DEFAULT_APPEND = true;

    private static final boolean DEFAULT_COMPRESS = false;

    private static final Clock DEFAULT_CLOCK = SystemClock.getInstance();

    private static final Set<RotationCallback> DEFAULT_CALLBACKS =
            Collections.singleton(LoggingRotationCallback.getInstance());

    private static final int DEFAULT_MAX_BACKUP_COUNT = -1;

    private enum DefaultExecutorServiceHolder {;

        private static final ScheduledExecutorService INSTANCE = createDefaultExecutorService();

        private static ScheduledThreadPoolExecutor createDefaultExecutorService() {
            int threadCount = readDefaultThreadCount();
            return new ScheduledThreadPoolExecutor(
                    threadCount,
                    new ThreadFactory() {

                        private int threadCount = 0;

                        @Override
                        public synchronized Thread newThread(Runnable runnable) {
                            String name = String.format("RotationJanitor-%02d", ++threadCount);
                            Thread thread = new Thread(runnable, name);
                            thread.setDaemon(true);
                            return thread;
                        }

                    });
        }

        private static int readDefaultThreadCount() {
            String threadCountProperty = System.getProperty("RotationJanitorCount");
            return threadCountProperty != null
                    ? Integer.parseInt(threadCountProperty)
                    : Runtime.getRuntime().availableProcessors();
        }

    }

    private final File file;

    private final RotatingFilePattern filePattern;

    private final ScheduledExecutorService executorService;

    private final Set<RotationPolicy> policies;

    private final boolean append;

    private final boolean compress;

    private final int maxBackupCount;

    private final Clock clock;

    private final Set<RotationCallback> callbacks;

    private RotationConfig(Builder builder) {
        this.file = builder.file;
        this.filePattern = builder.filePattern;
        this.executorService = builder.executorService;
        this.policies = Collections.unmodifiableSet(builder.policies);
        this.append = builder.append;
        this.compress = builder.compress;
        this.maxBackupCount = builder.maxBackupCount;
        this.clock = builder.clock;
        this.callbacks = Collections.unmodifiableSet(builder.callbacks);
    }

    public File getFile() {
        return file;
    }

    public RotatingFilePattern getFilePattern() {
        return filePattern;
    }

    public static ScheduledExecutorService getDefaultExecutorService() {
        return DefaultExecutorServiceHolder.INSTANCE;
    }

    public ScheduledExecutorService getExecutorService() {
        return executorService;
    }

    public Set<RotationPolicy> getPolicies() {
        return policies;
    }

    public static boolean getDefaultAppend() {
        return DEFAULT_APPEND;
    }

    public boolean isAppend() {
        return append;
    }

    public static boolean getDefaultCompress() {
        return DEFAULT_COMPRESS;
    }

    public boolean isCompress() {
        return compress;
    }

    public static int getDefaultMaxBackupCount() {
        return DEFAULT_MAX_BACKUP_COUNT;
    }

    public int getMaxBackupCount() {
		return maxBackupCount;
	}

	public static Clock getDefaultClock() {
        return DEFAULT_CLOCK;
    }

	public Clock getClock() {
        return clock;
    }

    public static Set<RotationCallback> getDefaultCallbacks() {
        return DEFAULT_CALLBACKS;
    }

    /**
     * Returns the first callback in the registered set of callbacks.
     *
     * @deprecated This method is kept for backward-compatibility reasons, use {@link #getCallbacks()} instead.
     */
    @Deprecated
    public RotationCallback getCallback() {
        return callbacks.iterator().next();
    }

    public Set<RotationCallback> getCallbacks() {
        return callbacks;
    }

    @Override
    public boolean equals(Object instance) {
        if (this == instance) return true;
        if (instance == null || getClass() != instance.getClass()) return false;
        RotationConfig that = (RotationConfig) instance;
        return append == that.append &&
                compress == that.compress &&
				maxBackupCount == that.maxBackupCount &&
                Objects.equals(file, that.file) &&
                Objects.equals(filePattern, that.filePattern) &&
                Objects.equals(executorService, that.executorService) &&
                Objects.equals(policies, that.policies) &&
                Objects.equals(clock, that.clock) &&
                Objects.equals(callbacks, that.callbacks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                file,
                filePattern,
                executorService,
                policies,
                append,
                compress,
                maxBackupCount,
                clock,
                callbacks);
    }

    @Override
    public String toString() {
        return String.format("RotationConfig{file=%s}", file);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private File file;

        private RotatingFilePattern filePattern;

        private ScheduledExecutorService executorService;

        private Set<RotationPolicy> policies;

        private boolean append = DEFAULT_APPEND;

        private boolean compress = DEFAULT_COMPRESS;

        private int maxBackupCount = DEFAULT_MAX_BACKUP_COUNT;

        private Clock clock = DEFAULT_CLOCK;

        private Set<RotationCallback> callbacks = new LinkedHashSet<>(DEFAULT_CALLBACKS);

        private Builder() {
            // Do nothing.
        }

        public Builder file(File file) {
            this.file = Objects.requireNonNull(file, "file");
            return this;
        }

        public Builder file(String fileName) {
            Objects.requireNonNull(fileName, "fileName");
            this.file = new File(fileName);
            return this;
        }

        public Builder filePattern(RotatingFilePattern filePattern) {
            this.filePattern = Objects.requireNonNull(filePattern, "filePattern");
            return this;
        }

        public Builder filePattern(String filePattern) {
            Objects.requireNonNull(filePattern, "filePattern");
            this.filePattern = RotatingFilePattern
                    .builder()
                    .pattern(filePattern)
                    .build();
            return this;
        }

        public Builder executorService(ScheduledExecutorService executorService) {
            this.executorService = Objects.requireNonNull(executorService, "executorService");
            return this;
        }

        public Builder policies(Set<RotationPolicy> policies) {
            this.policies = Objects.requireNonNull(policies, "policies");
            return this;
        }

        public Builder policy(RotationPolicy policy) {
            Objects.requireNonNull(policy, "policy");
            if (policies == null) {
                policies = new LinkedHashSet<>();
            }
            policies.add(policy);
            return this;
        }

        public Builder append(boolean append) {
            this.append = append;
            return this;
        }

        public Builder compress(boolean compress) {
            this.compress = compress;
            return this;
        }

        public Builder maxBackupCount(int maxBackupCount) {
            this.maxBackupCount = maxBackupCount;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public Builder callback(RotationCallback callback) {
            Objects.requireNonNull(callback, "callback");
            callbacks.add(callback);
            return this;
        }

        public Builder callbacks(Set<RotationCallback> callbacks) {
            this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
            return this;
        }

        public RotationConfig build() {
            prepare();
            validate();
            return new RotationConfig(this);
        }

        private void prepare() {
            if (executorService == null) {
                executorService = getDefaultExecutorService();
            }
        }

        private void validate() {
            Objects.requireNonNull(file, "file");
            if ((maxBackupCount <= 0 && filePattern == null) || (maxBackupCount > 0 && filePattern != null)) {
                throw new IllegalArgumentException("one of either maxBackupCount or filePattern must be provided");
            }
            if (policies == null || policies.isEmpty()) {
                throw new IllegalArgumentException("no rotation policy is provided");
            }
        }

    }

}
