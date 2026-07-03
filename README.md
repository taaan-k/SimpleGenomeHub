# SimpleGenomeHub

SimpleGenomeHub is a Java 11 desktop application for genome and annotation management, sequence extraction, BLAST workflows, functional annotation, enrichment analysis, and synteny-related tools.

For details and example operations, please refer to EXample Workflow.pdf

## Directory Layout

```text
src/
lib/
  TBtools_JRE1.6.jar
bin/
  tools/
    windows-x64/
    macos-x64/
toolchain/
  windows-x64/
  macos-x64/
  linux-x64/
build.xml
README.md
.gitignore
```

## Files To Download

This repository does not include bundled JDK files.

Download JDK 11 archives and extract them to:

- `toolchain/windows-x64/`
- `toolchain/macos-x64/`
- `toolchain/linux-x64/`

Required final paths:

```text
toolchain/windows-x64/bin/java.exe
toolchain/windows-x64/bin/javac.exe
toolchain/macos-x64/bin/java
toolchain/macos-x64/bin/javac
toolchain/linux-x64/bin/java
toolchain/linux-x64/bin/javac
```

## Build

Compile jar:

```powershell
ant
```

Build release packages:

```powershell
ant clean release-bundles
```

Build single-platform packages:

```powershell
ant clean package-windows
ant clean package-macos
```

Release files are written to `packages/`.
