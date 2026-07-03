# Toolchain

Do not commit bundled Java runtimes to this repository.

Before building or packaging, download the matching JDK 11 archive and extract it into one of these directories:

- `toolchain/windows-x64/`
- `toolchain/macos-x64/`
- `toolchain/linux-x64/`

The extracted runtime must place `java` or `java.exe` directly under the `bin/` folder inside the target directory.
