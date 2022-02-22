---
name: Bug report
about: Submit a bug report
title: ''
labels: ''
assignees: ''

---

**Version:** ...

**Description**
A clear and concise description of what the bug is.

**Configuration**
```java
RotationConfig
        .builder()
        .file("...")
        .filePattern("...")
        // ...
        .build();
```

**`LoggingRotationCallback` logs**
...

**Additional context**
- Is access to `RotatingFileOutputStream` multi-threaded?
- Which `RotatingFileOutputStream` methods are used?
