# Security

For vulnerabilities, use this repository's
[private vulnerability reporting](https://github.com/andreacasarin/jviewer-macos/security/advisories/new).
Include affected code, reproduction steps using synthetic data, and the impact.
Do not report credentials or exploitable details in a public issue.

Only the current `main` branch receives fixes. There is no guaranteed response
time or support commitment for a particular board or firmware version.

## Trust and credentials

This launcher downloads and executes Java code and native libraries supplied by
the BMC. Use it only with devices you trust. HTTPS certificate verification is
enabled by default; `--insecure` disables it for the web requests of that run.
The viewer's own KVM/media encryption depends on firmware and its launch options.

Passwords use a hidden prompt by default. Passwords supplied with `--password`
can appear in shell history and process listings. The vendor viewer also requires
session tokens in its process arguments. Downloaded JNLP files contain session
credentials: do not publish them, screenshots of them, or unredacted logs.

Vendor binaries are downloaded from your BMC at runtime, not distributed in
this repository. The macOS compatibility copy changes bytecode and removes the
vendor's signature metadata; the original cached JAR is preserved.
