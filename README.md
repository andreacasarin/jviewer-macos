# JViewer for macOS

[![Launcher checks](https://github.com/andreacasarin/jviewer-macos/actions/workflows/tests.yml/badge.svg)](https://github.com/andreacasarin/jviewer-macos/actions/workflows/tests.yml)
[![License: BSD 3-Clause](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg)](LICENSE)

Open a legacy **AMI MegaRAC / JViewer IPMI console** without Java Web Start.
Especially useful for macOS clients and older server boards whose BMC still
downloads a `.jnlp` file instead of offering an HTML5 console.

- A single Python script, with **no Python package dependencies**.
- Automatic x64 Java 8 selection on macOS, including Apple Silicon with Rosetta.
- A targeted macOS workaround for the native keyboard LED failure that can leave
  JViewer black at **0 fps**.

This is a maintained fork of [dfuchslin/jviewer-starter](https://github.com/dfuchslin/jviewer-starter),
originally based on [arbu/jviewer-starter](https://github.com/arbu/jviewer-starter).
The script keeps its upstream filename, `jviewer-starter.py`.

## Quick start on macOS

1. Install **Python 3.10 or newer**. Check with `python3 --version`.
2. Install a **Java 8 JDK for macOS, x86 64-bit**, such as
   [Azul Zulu](https://www.azul.com/downloads/?version=java-8-lts&os=macos&architecture=x86-64-bit&package=jdk).
   Choose **JDK**, not JRE, and **x86 64-bit**, including on Apple Silicon.
   On Apple Silicon, install Rosetta if needed: `softwareupdate --install-rosetta`.
3. Clone this repository and launch:

   ```bash
   git clone https://github.com/andreacasarin/jviewer-macos.git
   cd jviewer-macos
   python3 jviewer-starter.py --check
   python3 jviewer-starter.py --host https://192.0.2.225 --username admin
   ```

Replace the example address and username with your BMC's details. The password
is requested in a hidden prompt. The launcher logs in, downloads the firmware's
viewer files, and opens the console. Keep `MacJViewerCompat.java` alongside the
Python script; cloning the repository does this for you.

**Self-signed BMC certificate?** For a trusted device, add `--insecure`:

```bash
python3 jviewer-starter.py --host https://192.0.2.225 --username admin --insecure
```

Alternatively, use `--ca-cert /path/to/ca.pem` to verify against your own CA.
Certificate verification is enabled by default.

macOS discovers an installed x64 Java 8 automatically, without changing your
system's default Java. To select a specific installation:

```bash
export JVIEWER_JAVA_HOME="/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home"
```

## Compatibility

**Java 8 is still required.** This launcher replaces Java Web Start; it does not
replace the vendor's Java application. Java 9+ and ARM Java runtimes are rejected
before login.

| Client | Requirements | Validation |
| --- | --- | --- |
| macOS (Intel or Apple Silicon) | x64 Java 8 JDK; Rosetta on Apple Silicon | Original macOS console fix verified live; subsequent changes covered offline |
| Linux / Windows | x86 or x64 Java 8 matching firmware native libraries | Automated regressions; live board compatibility depends on firmware |
| Docker on macOS | Docker Desktop and XQuartz; firmware must supply Linux x64 native libraries | Image, CLI, and offline regressions validated on Apple Silicon; live X11/KVM unverified |

The launcher targets AMI firmware using `/rpc/WEBSES/create.asp` and
`/Java/jviewer.jnlp`, and supports positional and named JViewer arguments.
It is **not a universal IPMI viewer**: Dell iDRAC, HPE iLO, and ATEN/Supermicro
viewers can use different protocols and are outside its scope. Even AMI boards
can have incompatible firmware layouts. Compatibility reports are welcome.

The locally verified runtime is Zulu 8.92.0.21, OpenJDK `1.8.0_482-b08`, x64.
Each new board or firmware version needs a live console check.

## Usage

```bash
python3 jviewer-starter.py --help
```

Omit `--host` or `--username` to be prompted. A bare hostname/IP uses HTTP;
include `https://` for HTTPS. Custom web ports work, for example
`--host https://ipmi.example:8443`.

| Option | Purpose |
| --- | --- |
| `--check` | Verify local Java setup without a BMC connection or credentials |
| `--host HOST` | BMC hostname, IP, or HTTP(S) origin |
| `--username USER` | BMC username |
| `--insecure` | Skip HTTPS certificate verification for a trusted BMC |
| `--ca-cert FILE` | Verify HTTPS using a PEM CA certificate |
| `--refresh` | Download the current firmware's JARs again |
| `--timeout SECONDS` | Per-operation network timeout; default: 15 seconds |
| `--video-port PORT` | Override the video port advertised by the BMC |

Prefer the hidden password prompt. `--password` remains available for upstream
compatibility, but puts the password in shell history and the process arguments.

`JVIEWER_JAVA_HOME` overrides Java selection. On Linux/Windows, Java is otherwise
selected from `PATH`. `JVIEWER_JAVA_OPTIONS` accepts extra JVM options with
shell-style quoting, for example `-Xmx512m -Dexample="value with spaces"`.
You can symlink the script into your `PATH`; keep the original checkout intact.

### Firmware updates and caching

The launcher fetches a fresh JNLP on every connection and downloads only its
declared viewer and native JARs for the client OS and Java architecture. It does
not assume that optional files such as `JViewer-SOC.jar` or `Mac64.jar` exist.

After a BMC firmware update, run your usual command with `--refresh`.
Cached JARs are validated before reuse; corrupt files are downloaded again.
A failed refresh stops the launch and preserves previously cached downloads.
Only the current descriptor's JARs enter the classpath.

Caches are separated by BMC origin, OS, and Java bitness under these locations.
The upstream directory name is retained so existing installations reuse caches:

- macOS: `~/Library/Application Support/jviewer-starter/`
- Linux: `${XDG_DATA_HOME:-~/.local/share}/jviewer-starter/`
- Windows: `%LOCALAPPDATA%\jviewer-starter\`

The video TCP endpoint is checked before downloads and Java startup. A successful
check confirms reachability, not authentication or a completed KVM handshake.
If you know a forwarded or configured video port differs, use
`--video-port 5900` (example only). Normally omit it to follow the firmware.
The launcher does not scan for alternative ports.

## macOS black-screen workaround

Some firmware supplies Windows/Linux native libraries but none for macOS.
JViewer can then fail in its native keyboard LED calls, opening a black console
at 0 fps. When macOS native libraries are absent, the launcher compiles the
included Java helper to create a separate compatibility copy of `JViewer.jar`.

The patch replaces two keyboard LED callbacks, preserving remote LED state in
the soft keyboard. It checks the expected fields, methods, and native calls,
and refuses unsupported layouts. The original downloaded JAR is unchanged;
signature metadata is removed from the modified copy. The copy is rebuilt when
the viewer JAR or helper source changes.

Local keyboard LED synchronization and physical drive redirection are unavailable
without native libraries. Other virtual media behavior has not been verified by
the regression suite. The helper uses Java 8's bundled ASM, so a **Java 8 JDK**
with `javac` is required. Other causes of blank video need separate diagnosis.

## Optional Docker launch on macOS

The direct route above is the simplest option. To keep Java inside a container:

1. Install and start Docker Desktop and [XQuartz](https://www.xquartz.org/).
2. In XQuartz Settings → Security, enable **Allow connections from network
   clients**, then quit and reopen XQuartz.
3. Run:

   ```bash
   ./jviewer-docker.sh --host https://192.0.2.225 --username admin --insecure
   ```

The wrapper builds a Linux x64 Java 8 image, forwards arguments, removes the
stopped container, and retains downloads in a named Docker volume. The BMC must
provide Linux x64 native libraries. It works from another directory or through
a symlink. The image and CLI can be checked without XQuartz using
`docker compose run --rm jviewer --check`. The image build, CLI, and offline
regression suite have been validated with Docker Desktop on Apple Silicon.
A live XQuartz/KVM session still needs validation on your setup.

## Troubleshooting

| Symptom | Next step |
| --- | --- |
| Java missing or unsupported | Install an x86 Java 8 JDK; run `--check`. Use `JVIEWER_JAVA_HOME` to select it explicitly. |
| HTTPS certificate error | Use `--ca-cert`, or `--insecure` for your trusted device. |
| Login failed / invalid JNLP | Check credentials and whether the firmware supports the AMI endpoints listed above. |
| HTTP 404 for a JAR | The firmware references a missing resource. The launcher cannot supply missing vendor libraries. |
| Video connection refused / timed out | Check the BMC's KVM service, advertised port, and firewall. Web login alone does not establish KVM access. |
| Console opens but stays black | Inspect terminal diagnostics. Native-library errors, authentication, host video output, and network access are separate causes. |
| Missing compiler / unsupported workaround layout | Use a Java 8 JDK with `javac`. Unknown firmware needs inspection before a compatible patch can be added. |
| Docker cannot start | Start Docker Desktop; for a GUI, also start XQuartz with network clients enabled. |

If it still fails, [open an issue](https://github.com/andreacasarin/jviewer-macos/issues)
with your board, BMC firmware, OS, Java version, and redacted error output.
Do not attach JNLP files: they contain session credentials.

## Development and license

See [CONTRIBUTING.md](CONTRIBUTING.md) for tests and compatibility reports, and
[SECURITY.md](SECURITY.md) for private vulnerability reporting.

The launcher is distributed under the [BSD 3-Clause license](LICENSE), retaining
the original copyright and license notice. Vendor JARs, native libraries, Java
runtimes, and firmware are not included; their respective licenses still apply.
