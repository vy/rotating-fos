/*
 * Copyright 2018-2023 Volkan Yazıcı <volkan@yazi.ci>
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

/**
 * Configuration for constructing {@link RotatingFileOutputStream} instances.
 */
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

    /**
     * @return the file to be written by the stream
     */
    public File getFile() {
        return file;
    }

    /**
     * Gets the file pattern to be used while rotating files, if set; otherwise,
     * rotated files will be named by other means, e.g., backup indices.
     *
     * @return the file pattern to be used, if set; otherwise, {@code null},
     * denoting that rotated files will be named by other means, e.g., backup
     * indices
     *
     * @see #getMaxBackupCount()
     */
    public RotatingFilePattern getFilePattern() {
        return filePattern;
    }

    /**
     * Gets the default scheduler for time-based policies and compression tasks.
     * <p>
     * Note that, this scheduler is shared between {@link RotatingFileOutputStream}
     * instances, unless it is explicitly set to another one in the
     * configuration. Hence, avoid shutting this scheduler instance down.
     * </p><p>
     * By default, the scheduler thread pool size is equal to the amount of CPU
     * cores available. This can be overridden by setting the {@code RotationJanitorCount}
     * system property.
     * </p>
     *
     * @return the default scheduler for time-based policies and compression tasks
     */
    public static ScheduledExecutorService getDefaultExecutorService() {
        return DefaultExecutorServiceHolder.INSTANCE;
    }

    /**
     * Gets the scheduler for time-based policies and compression tasks.
     * <p>
     * Unless explicitly set, this defaults to the one returned by {@link #getDefaultExecutorService()}.
     * Since the default scheduler can be shared by multiple streams, be
     * cautious while closing the scheduler returned by this method.
     * </p>
     *
     * @return the scheduler for time-based policies and compression tasks
     */
    public ScheduledExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * @return the registered set of rotation policies
     */
    public Set<RotationPolicy> getPolicies() {
        return policies;
    }

    /**
     * @return the default value of the {@code append} flag, indicating, if
     * true, then bytes will be written to the end of the file rather than the
     * beginning
     */
    public static boolean getDefaultAppend() {
        return DEFAULT_APPEND;
    }

    /**
     * @return the {@code append} flag, indicating, if true, then bytes will be
     * written to the end of the file rather than the beginning
     */
    public boolean isAppend() {
        return append;
    }

    /**
     * @return the default value of the {@code compress} flag, indicating, if
     * true, rotated files will be compressed in the background
     */
    public static boolean getDefaultCompress() {
        return DEFAULT_COMPRESS;
    }

    /**
     * Gets the {@code compress} flag, indicating, if true, rotated files will
     * be compressed in the background.
     * <p>
     * Note that this option cannot be combined with {@code maxBackupCount}.
     * </p>
     *
     * @return the {@code compress} flag, indicating, if true, rotated files
     * will be compressed in the background.
     *
     * @see #getMaxBackupCount()
     */
    public boolean isCompress() {
        return compress;
    }

    /**
     * @return the default value of the {@code maxBackupCount}, indicating, if
     * greater than zero, rotated files will be named as {@code file.0},
     * {@code file.1}, {@code file.2}, ..., {@code file.N} in the order from the
     * newest to the oldest, where {@code N} denoting the {@code maxBackupCount}
     */
    public static int getDefaultMaxBackupCount() {
        return DEFAULT_MAX_BACKUP_COUNT;
    }

    /**
     * Gets the {@code maxBackupCount}, indicating, if greater than zero,
     * rotated files will be named as {@code file.0}, {@code file.1},
     * {@code file.2}, ..., {@code file.N} in the order from the newest to the
     * oldest, where {@code N} denoting the {@code maxBackupCount}.
     * <p>
     * Note that this option cannot be combined with {@code filePattern} or
     * {@code compress}.
     * </p>
     *
     * @return the {@code maxBackupCount}, indicating, if greater than zero,
     * rotated files will be named as {@code file.0}, {@code file.1},
     * {@code file.2}, ..., {@code file.N} in the order from the newest to the
     * oldest, where {@code N} denoting the {@code maxBackupCount}
     */
    public int getMaxBackupCount() {
		return maxBackupCount;
	}

    /**
     * @return the default clock implementation
     */
	public static Clock getDefaultClock() {
        return DEFAULT_CLOCK;
    }

    /**
     * @return the clock implementation
     */
	public Clock getClock() {
        return clock;
    }

    /**
     * @return the default callbacks
     */
    public static Set<RotationCallback> getDefaultCallbacks() {
        return DEFAULT_CALLBACKS;
    }

    /**
     * @return the first callback in the registered set of callbacks
     *
     * @deprecated This method is kept for backward-compatibility reasons, use {@link #getCallbacks()} instead.
     */
    @Deprecated
    public RotationCallback getCallback() {
        return callbacks.iterator().next();
    }

    /**
     * @return the registered set of callbacks
     */
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

    /**
     * @param config a rotation configuration
     *
     * @return a rotation configuration builder using the given configuration
     */
    public static Builder builder(RotationConfig config) {
        Objects.requireNonNull(config, "config");
        return new Builder(config);
    }

    /**
     * @return a rotation configuration builder where optional fields are populated with defaults
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The rotation configuration builder.
     */
    public static class Builder {

        private File file;

        private RotatingFilePattern filePattern;

        private ScheduledExecutorService executorService;

        private Set<RotationPolicy> policies;

        private boolean append = DEFAULT_APPEND;

        private boolean compress = DEFAULT_COMPRESS;

        private int maxBackupCount = DEFAULT_MAX_BACKUP_COUNT;

        private Clock clock = DEFAULT_CLOCK;

        private Set<RotationCallback> callbacks =
                // We need a defensive copy for Set#add() in
                // callback(RotationCallback) setter.
                new LinkedHashSet<>(DEFAULT_CALLBACKS);

        private Builder(RotationConfig config) {
            this.file = config.file;
            this.filePattern = config.filePattern;
            this.executorService = config.executorService;
            this.policies = config.policies;
            this.append = config.append;
            this.compress = config.append;
            this.maxBackupCount = config.maxBackupCount;
            this.clock = config.clock;
            this.callbacks = config.callbacks;
        }

        private Builder() {}

        /**
         * Sets the file to be written by the stream.
         *
         * @param file the file to be written by the stream
         *
         * @return this builder
         */
        public Builder file(File file) {
            this.file = Objects.requireNonNull(file, "file");
            return this;
        }

        /**
         * Sets the file to be written by the stream.
         *
         * @param fileName the file to be written by the stream
         *
         * @return this builder
         */
        public Builder file(String fileName) {
            Objects.requireNonNull(fileName, "fileName");
            this.file = new File(fileName);
            return this;
        }

        /**
         * Sets the file pattern to be used while rotating files, if set;
         * otherwise, rotated files will be named by other means, e.g., backup
         * indices.
         * <p>
         * Note that this option cannot be combined with {@code maxBackupCount}.
         * </p>
         *
         * @param filePattern the file pattern to be used while rotating files
         *
         * @return this builder
         */
        public Builder filePattern(RotatingFilePattern filePattern) {
            this.filePattern = Objects.requireNonNull(filePattern, "filePattern");
            return this;
        }

        /**
         * Sets the file pattern to be used while rotating files, e.g.,
         * {@code /tmp/app-%d{yyyyMMdd-HHmmss.SSS}.log}. Use the setter
         * {@link #filePattern(RotatingFilePattern)} to have full control
         * on other available file pattern settings.
         * <p>
         * If undefined, rotated files will be named by other means, e.g.,
         * backup indices.
         * </p><p>
         * Note that this option cannot be combined with {@code maxBackupCount}.
         * </p>
         *
         * @param filePattern the file pattern to be used while rotating files
         *
         * @return this builder
         *
         * @see RotatingFilePattern
         */
        public Builder filePattern(String filePattern) {
            Objects.requireNonNull(filePattern, "filePattern");
            this.filePattern = RotatingFilePattern
                    .builder()
                    .pattern(filePattern)
                    .build();
            return this;
        }

        /**
         * Sets the scheduler for time-based policies and compression tasks.
         * <p>
         * If unset, the default one will be used, whose thread pool size is
         * equal to the amount of CPU cores available. This can be overridden by
         * setting the {@code RotationJanitorCount} system property.
         * </p>
         *
         * @param executorService the scheduler for time-based policies and compression tasks
         *
         * @return this builder
         *
         * @see #getDefaultExecutorService()
         */
        public Builder executorService(ScheduledExecutorService executorService) {
            this.executorService = Objects.requireNonNull(executorService, "executorService");
            return this;
        }

        /**
         * Sets the rotation policies to be employed.
         *
         * @param policies the rotation policies to be employed
         *
         * @return this builder
         */
        public Builder policies(Set<RotationPolicy> policies) {
            Objects.requireNonNull(policies, "policies");
            // We need a defensive copy for Set#add() in policy(Policy) setter.
            this.policies = new LinkedHashSet<>(policies);
            return this;
        }

        /**
         * Adds a rotation policy to be employed along with the ones already registered.
         *
         * @param policy a rotation policy
         *
         * @return this builder
         */
        public Builder policy(RotationPolicy policy) {
            Objects.requireNonNull(policy, "policy");
            if (policies == null) {
                policies = new LinkedHashSet<>();
            }
            policies.add(policy);
            return this;
        }

        /**
         * Sets the {@code append} flag, indicating, if true, then bytes will be
         * written to the end of the file rather than the beginning.
         *
         * @param append the {@code append} flag, indicating, if true, then
         *               bytes will be written to the end of the file rather
         *               than the beginning
         *
         * @return this builder
         *
         * @see #getDefaultAppend()
         */
        public Builder append(boolean append) {
            this.append = append;
            return this;
        }

        /**
         * Sets the {@code compress} flag, indicating, if true, rotated files
         * will be compressed in the background.
         * <p>
         * Note that this option cannot be combined with {@code maxBackupCount}.
         * </p>
         *
         * @param compress if true, rotated files will be compressed in the
         *                 background
         *
         * @return this builder
         *
         * @see #getDefaultCompress()
         */
        public Builder compress(boolean compress) {
            this.compress = compress;
            return this;
        }

        /**
         * Gets the {@code maxBackupCount}, indicating, if greater than zero,
         * rotated files will be named as {@code file.0}, {@code file.1},
         * {@code file.2}, ..., {@code file.N} in the order from the newest to the
         * oldest, where {@code N} denoting the {@code maxBackupCount}.
         * <p>
         * Note that this option cannot be combined with {@code filePattern} or
         * {@code compress}.
         * </p>
         *
         * @param maxBackupCount if greater than zero, rotated files will be
         *                       named as {@code file.0}, {@code file.1},
         *                       {@code file.2}, ..., {@code file.N} in the
         *                       order from the newest to the oldest, where
         *                       {@code N} denoting the {@code maxBackupCount}
         *
         * @return this builder
         */
        public Builder maxBackupCount(int maxBackupCount) {
            this.maxBackupCount = maxBackupCount;
            return this;
        }

        /**
         * Sets the clock implementation to be used.
         *
         * @param clock a clock instance
         *
         * @return this builder
         */
        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /**
         * Adds a callback to be employed along with the ones already registered.
         *
         * @param callback a callback to be employed along with the ones already
         *                 registered
         *
         * @return this builder
         *
         * @see #getDefaultCallbacks()
         */
        public Builder callback(RotationCallback callback) {
            Objects.requireNonNull(callback, "callback");
            callbacks.add(callback);
            return this;
        }

        /**
         * Sets the callbacks to employed.
         *
         * @param callbacks the callbacks to employed
         *
         * @return this builder
         */
        public Builder callbacks(Set<RotationCallback> callbacks) {
            Objects.requireNonNull(callbacks, "callbacks");
            // We need a defensive copy for Set#add() in callback(RotationCallback) setter.
            this.callbacks = new LinkedHashSet<>(callbacks);
            return this;
        }

        /**
         * @return a {@link RotationConfig} constructed using the given properties
         */
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
            if (maxBackupCount > 0) {
                String conflictingField = null;
                if (filePattern != null) {
                    conflictingField = "filePattern";
                } else if (compress) {
                    conflictingField = "compress";
                }
                if (conflictingField != null) {
                    throw new IllegalArgumentException(
                            "maxBackupCount and " + conflictingField + " cannot be combined");
                }
            } else if (filePattern == null) {
                throw new IllegalArgumentException(
                        "one of either maxBackupCount or filePattern must be provided");
            }
            if (policies == null || policies.isEmpty()) {
                throw new IllegalArgumentException("no rotation policy is provided");
            }
        }

    }

}
