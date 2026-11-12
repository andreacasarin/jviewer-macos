# Contributing

Bug reports, small fixes, and compatibility reports for AMI JViewer boards are
welcome. This fork focuses on launching legacy consoles without Java Web Start,
with a targeted macOS compatibility workaround. Keep changes dependency-free
where practical and preserve the original license notices.

## Report a problem or a working board

Include:

- Motherboard/server model and BMC firmware version.
- Client OS and architecture, Python version, and Java version/architecture.
- Direct Python or Docker launch, command with credentials removed, and redacted
  terminal error output.
- For a working setup: whether video, keyboard/mouse, and virtual media were
  actually tested. Distinguish a successful launch from a usable console.

Never post passwords, cookies, tokens, downloaded JNLP files, private hostnames,
or vendor JARs. Report vulnerabilities using [SECURITY.md](SECURITY.md).

## Run the checks

Python 3.10+ is required. The Python tests use the standard library and need no
private BMC, credentials, network access, or downloaded vendor files:

```bash
python3 -W error -B -m unittest discover -v
bash -n jviewer-docker.sh
```

To run the bytecode compatibility tests too, select an x86 Java 8 JDK:

```bash
JVIEWER_TEST_JAVA_HOME="/path/to/java8-jdk" python3 -W error -B -m unittest discover -v
```

`JAVA_HOME` is also accepted for the integration tests. On macOS, the JDK home
typically ends with `Contents/Home`. These tests compile a synthetic viewer,
reproduce the missing-native-library failure, verify the patched callbacks with
Java bytecode verification, and check cache invalidation and rejected layouts.
The compatibility build runs on Linux/macOS; Windows runs the Python tests.

For container changes, also run:

```bash
docker compose config --quiet
docker compose build
docker compose run --rm jviewer --check
```

CI covers Python 3.10 and 3.13 on Linux and Windows, Java integration on Linux,
and a Docker image build and CLI check. Mock network access in tests; use
synthetic, credential-free fixtures.
A live KVM session is a separate check: state the board and firmware tested,
or clearly say when live hardware/X11 validation was unavailable.
