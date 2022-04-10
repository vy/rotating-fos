
<h1>

Rotating FOS 
[![Badge Build]][Actions]
[![Badge Version]][Maven]
[![Badge License]][License]

</h1>

*Rotating File-Output-Streams For Java8*



`rotating-fos` is a Java 8 library providing `RotatingFileOutputStream` which
internally rotates a delegate `FileOutputStream` using provided rotation
policies similar to [logrotate],
[Log4j] and [Logback].





## Credits

### Creator

**[Volkan Yazıcı]** - `2018 - 2021`

### Contributors

**[@pitschr]** - `Christoph Pitschmann`

  - *`RotationCallback#onOpen()` Method*
  - *Scheduler Shutdown At Exit* 
  - *Windows-specific Fixes*
  - *Java 9 Module Name*

**[@kc7bfi]** - `David Robison`

  *NPE due to write after close in #26*
  
**[@yawkat]** - `Jonas Konrad`

  *`RotatingFileOutputStream`* <br>
  *Thread-Safety Improvements*

**[@lukasbradley]** - `Lukas Bradley`

**[@liran2000]** - `Liran Mendelovich`

  *Rolling Via `maxBackupCount`*



<!----------------------------------------------------------------------------->

[Actions]: https://github.com/vy/rotating-fos/actions
[Maven]: https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.vlkan.rfos%22

[License]: LICENSE


<!---------------------------------{ Related }--------------------------------->

[LogRotate]: https://github.com/logrotate/logrotate
[LogBack]: https://logback.qos.ch/
[Log4J]: https://logging.apache.org/log4j/


<!-------------------------------{ Contributors }------------------------------>

[@liran2000]: https://github.com/liran2000/
[@pitschr]: https://github.com/pitschr
[@kc7bfi]: https://github.com/kc7bfi
[@lukasbradley]: https://github.com/lukasbradley/
[@yawkat]: https://yawk.at/
[Volkan Yazıcı]: https://vlkan.com/


<!----------------------------------{ Badges }--------------------------------->

[Badge Version]: https://img.shields.io/maven-central/v/com.vlkan.rfos/rotating-fos.svg
[Badge License]: https://img.shields.io/badge/License-Apache_2.0-blue.svg
[Badge Build]: https://github.com/vy/rotating-fos/workflows/build/badge.svg