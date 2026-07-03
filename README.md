# SimpleGenomeHub

SimpleGenomeHub is a Java 11 desktop application for genome and annotation management, sequence extraction, BLAST workflows, functional annotation, enrichment analysis, and synteny-related tools.

## Minimal Repository Layout

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
build.xml
README.md
.gitignore
```

## What Is Kept

- `src/`: application source code
- `lib/TBtools_JRE1.6.jar`: required Java dependency
- `bin/tools/`: platform-scoped external executables used by BLAST, Genome Compare, tree building, and eggNOG workflows
- `toolchain/windows-x64/`: bundled Windows JDK used for Windows builds and Windows release assembly
- `toolchain/macos-x64/`: place the extracted macOS Intel JDK 11 here before creating a self-contained macOS release
- `build.xml`: Ant build script

## Build

Create the runnable package:

```powershell
ant dist
```

Create only the application jar:

```powershell
ant
```

The packaged output is written to `dist/`.

Platform release packages are written to `packages/`.

## Notes

- The source tree has been reduced to a minimal runnable state.
- `bin/tools/windows-x64/` is the current Windows tool payload.
- `bin/tools/macos-x64/` is the current macOS Intel tool payload.
- `toolchain/windows-x64/` is the current Windows JDK payload.
- `toolchain/macos-x64/` is the expected location for the macOS Intel JDK payload.
- User configuration is stored under the current user's home directory in `.SimpleGenomeHub`.

• 在项目根目录运行：

  ant clean release-bundles

  打完后产物在：

  - packages/SimpleGenomeHub-windows-x64.zip
  - packages/SimpleGenomeHub-macos-x64.tar.gz

  如果你只想单独打一个：

  ant clean package-windows
  ant clean package-macos