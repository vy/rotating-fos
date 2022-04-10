
[![Actions Status](https://github.com/vy/rotating-fos/workflows/build/badge.svg)](https://github.com/vy/rotating-fos/actions)
[![Maven Central](https://img.shields.io/maven-central/v/com.vlkan.rfos/rotating-fos.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.vlkan.rfos%22)
[![License](https://img.shields.io/github/license/vy/rotating-fos.svg)](https://www.apache.org/licenses/LICENSE-2.0.txt)

`rotating-fos` is a Java 8 library providing `RotatingFileOutputStream` which
internally rotates a delegate `FileOutputStream` using provided rotation
policies similar to [logrotate](https://github.com/logrotate/logrotate),
[Log4j](https://logging.apache.org/log4j/) and [Logback](https://logback.qos.ch/).





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
