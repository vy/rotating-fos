
[![Badge Build]][Actions]
[![Badge Version]][Maven]
[![Badge License]][License]

`rotating-fos` is a Java 8 library providing `RotatingFileOutputStream` which
internally rotates a delegate `FileOutputStream` using provided rotation
policies similar to [logrotate],
[Log4j] and [Logback].





# Contributors

- [Christoph (pitschr) Pitschmann][Pitschmann] (Windows-specific
  fixes, `RotationCallback#onOpen()` method, Java 9 module name, scheduler
  shutdown at exit)
- [David (kc7bfi) Robison][Robison] (NPE due to write after close in #26)
- [Jonas (yawkat) Konrad][Konrad] (`RotatingFileOutputStream`
  thread-safety improvements)
- [Lukas Bradley][Bradley]
- [Liran Mendelovich][Mendelovich] (rolling via `maxBackupCount`)

# License

Copyright &copy; 2018-2021 [Volkan Yazıcı][Yazıcı]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.


<!----------------------------------------------------------------------------->

[Badge Version]: https://img.shields.io/maven-central/v/com.vlkan.rfos/rotating-fos.svg
[Badge License]: https://img.shields.io/badge/License-Apache_2.0-blue.svg
[Badge Build]: https://github.com/vy/rotating-fos/workflows/build/badge.svg


[Actions]: https://github.com/vy/rotating-fos/actions
[Maven]: https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.vlkan.rfos%22

[License]: LICENSE


[LogRotate]: https://github.com/logrotate/logrotate
[LogBack]: https://logback.qos.ch/
[Log4J]: https://logging.apache.org/log4j/

[Mendelovich]: https://github.com/liran2000/
[Pitschmann]: https://github.com/pitschr
[Robison]: https://github.com/kc7bfi
[Bradley]: https://github.com/lukasbradley/
[Konrad]: https://yawk.at/
[Yazıcı]: https://vlkan.com/