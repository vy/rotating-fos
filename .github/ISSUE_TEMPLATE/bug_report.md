---
name: Bug report
about: Submit a bug report
---

## Description
A clear and concise description of what the bug is.

## Configuration

**Version:** ...

**Operating system:** ...

**JDK:** ...

**Rotation configuration:**
```java
RotationConfig
        .builder()
        .file("...")
        .filePattern("...")
        // ...
        .build();
```

## Logs
```
Stacktraces, errors, etc. relevant applications logs.
`LoggingRotationCallback` logs will be really handy.
```

## Additional context
- Is access to `RotatingFileOutputStream` multi-threaded?
- Which `RotatingFileOutputStream` methods are used?
