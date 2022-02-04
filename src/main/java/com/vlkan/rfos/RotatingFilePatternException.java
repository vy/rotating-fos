/*
 * Copyright 2018-2022 Volkan Yazıcı
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

/**
 * Used by {@link RotatingFilePattern} for communicating failures.
 *
 * @deprecated This class will be removed in the next major release and
 * {@link IllegalArgumentException} will be used instead.
 */
@Deprecated
public class RotatingFilePatternException extends IllegalArgumentException {

    @Deprecated
    public RotatingFilePatternException(String message) {
        super(message);
    }

    @Deprecated
    public RotatingFilePatternException(String message, Throwable cause) {
        super(message, cause);
    }

}
