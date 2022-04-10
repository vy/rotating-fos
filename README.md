
<h1>

Rotating FOS 
[![Badge Version]][Maven]
[![Badge License]][License]
[![Badge Build]][Actions]

</h1>

*Rotating File-Output-Streams For Java8*

---

<div align = center>

  **⸢ [Usage] ⸥**
  **⸢ [Caveats] ⸥**
  **⸢ [Changelog] ⸥**
  
</div>

---

<br>
<br>

<div align = center>

This library provides a `RotatingFileOutputStream` which internally<br>
rotates a delegate `FileOutputStream` using provided rotation <br>
policies similar to **[LogRotate]** / **[Log4j]** / **[Logback]** .

</div>

<br>
<br>

<h1 align = center> Credits </h1>

### Creator

**[Volkan Yazıcı]** - `2018 - 2021`

<br>

### Contributors

**[@pitschr]** - `Christoph Pitschmann`

  - *`RotationCallback#onOpen()` Method*
  - *Scheduler Shutdown At Exit* 
  - *Windows-specific Fixes*
  - *Java 9 Module Name*

<br>

**[@kc7bfi]** - `David Robison`

  *NPE due to write after close in #26*

<br>  

**[@yawkat]** - `Jonas Konrad`

  *`RotatingFileOutputStream`* <br>
  *Thread-Safety Improvements*

<br>

**[@lukasbradley]** - `Lukas Bradley`

<br>

**[@liran2000]** - `Liran Mendelovich`

  *Rolling Via `maxBackupCount`*

<br>

<!----------------------------------------------------------------------------->

[Actions]: https://github.com/vy/rotating-fos/actions
[Maven]: https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.vlkan.rfos%22

[License]: LICENSE


<!-------------------------------{ QuickLinks }-------------------------------->

[Changelog]: Documentation/Changelog.md
[Caveats]: Documentation/Caveats.md
[Usage]: Documentation/Usage.md


<!---------------------------------{ Related }--------------------------------->

[LogRotate]: https://github.com/logrotate/logrotate
[LogBack]: https://logback.qos.ch/
[Log4J]: https://logging.apache.org/log4j/


<!-------------------------------{ Contributors }------------------------------>

[Volkan Yazıcı]: https://vlkan.com/

[@lukasbradley]: https://github.com/lukasbradley/
[@liran2000]: https://github.com/liran2000/
[@pitschr]: https://github.com/pitschr
[@kc7bfi]: https://github.com/kc7bfi
[@yawkat]: https://yawk.at/


<!----------------------------------{ Badges }--------------------------------->

[Badge Version]: https://img.shields.io/maven-central/v/com.vlkan.rfos/rotating-fos.svg
[Badge License]: https://img.shields.io/badge/License-Apache_2.0-blue.svg
[Badge Build]: https://github.com/vy/rotating-fos/workflows/build/badge.svg
