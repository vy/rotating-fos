
[![Actions Status](https://github.com/vy/rotating-fos/workflows/build/badge.svg)](https://github.com/vy/rotating-fos/actions)
[![Maven Central](https://img.shields.io/maven-central/v/com.vlkan.rfos/rotating-fos.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.vlkan.rfos%22)
[![License](https://img.shields.io/github/license/vy/rotating-fos.svg)](https://www.apache.org/licenses/LICENSE-2.0.txt)

`rotating-fos` is a Java 8 library providing `RotatingFileOutputStream` which
internally rotates a delegate `FileOutputStream` using provided rotation
policies similar to [logrotate](https://github.com/logrotate/logrotate),
[Log4j](https://logging.apache.org/log4j/) and [Logback](https://logback.qos.ch/).



# Caveats

- **`append` is enabled for `RotatingFileOutputStream` by default**, whereas
  it is disabled (and hence truncates the file at start) for standard
  `FileOutputStream` by default.

- **Rotated file conflicts are not resolved by `rotating-fos`.** Once a
  rotation policy gets triggered, `rotating-fos` applies the given
  `filePattern` to determine the rotated file name. In order to avoid
  previously generated files to be overridden, prefer a sufficiently
  fine-grained date-time pattern.

  For instance, given `filePattern` is `/tmp/app-%d{yyyyMMdd}.log`, if
  `SizeBasedRotationPolicy` gets triggered multiple times within a day, the last
  one will override the earlier generations in the same day. In order to avoid
  this, you should use a date-time pattern with a higher resolution, such as
  `/tmp/app-%d{yyyyMMdd-HHmmss-SSS}.log`.

- **Make sure `RotationCallback` methods are not blocking.** Callbacks are
  invoked using the `ScheduledExecutorService` passed via `RotationConfig`.
  Hence blocking callback methods have a direct impact on time-sensitive
  policies and compression tasks.

- **When `append` is enabled, be cautious while using `onOpen` and `onClose`
  callbacks.** These callbacks might be employed to introduce headers and/or
  footers to certain type of files, e.g., [CSV](https://en.wikipedia.org/wiki/Comma-separated_values).
  Though one needs to avoid injecting the same header and/or footer multiple
  times when a file is re-opened for append. Note that this is not a problem
  for files opened/closed via rotation.

# Contributors

- [Christoph (pitschr) Pitschmann](https://github.com/pitschr) (Windows-specific
  fixes, `RotationCallback#onOpen()` method, Java 9 module name, scheduler
  shutdown at exit)
- [David (kc7bfi) Robison](https://github.com/kc7bfi) (NPE due to write after close in #26)
- [Jonas (yawkat) Konrad](https://yawk.at/) (`RotatingFileOutputStream`
  thread-safety improvements)
- [Lukas Bradley](https://github.com/lukasbradley/)
- [Liran Mendelovich](https://github.com/liran2000/) (rolling via `maxBackupCount`)

# License

Copyright &copy; 2018-2021 [Volkan Yazıcı](https://vlkan.com/)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
