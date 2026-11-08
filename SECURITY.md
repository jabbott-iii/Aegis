# Security Policy

Aegis is a security product, so vulnerabilities in it can directly harm
users. Reports are welcome.

## Supported versions

Aegis is in Phase 0 and has no releases. Until the first release, only
`main` is supported.

| Version | Supported |
| :- | :- |
| `main` | :white_check_mark: |
| Releases | None yet |

## Reporting a vulnerability

Report vulnerabilities privately, through GitHub's private vulnerability
reporting:

1. On the repository's **Security** tab, select **Report a
   vulnerability**, or go to
   <https://github.com/jabbott-iii/Aegis/security/advisories/new>. This
   opens a private advisory.
2. Fill in the form.

> **Owner action needed:** enable **Private vulnerability reporting** in the
> GitHub repository settings (*Settings › Code security*) so that this
> reporting link works, and confirm the response targets in "What to
> expect".

Don't open a public issue for a security problem.

Include:

- the affected version or commit;
- the device and its Android version;
- the steps to reproduce it;
- the impact you observed.

Please don't:

- send live malware samples by email or attach them to issues;
- test against other people's devices or accounts.

## What to expect

These response targets are proposed; the owner has yet to confirm them.

| Stage | Target |
| :- | :- |
| Acknowledge the report | Within 7 days |
| Initial assessment | Within 14 days |
| Fix for critical issues (for example, a signature-verification bypass) | As soon as possible, with an out-of-band release |

## Scope

In scope:

- The Aegis Android app (all modules).
- The threat-intel bundle format, signature verification, and the update
  client.
- `tools/intel-pipeline` and the CI workflows that build and sign bundles.
- Bypasses of documented protections: for example, a crafted APK that
  crashes or evades the scanner because of a parser bug.

Out of scope:

- Limits already documented in
  [`docs/threat-model.md`](docs/threat-model.md), such as DoH apps
  bypassing DNS filtering.
- Vulnerabilities in Android itself or in third-party apps. Report those to
  their vendors.
- Missing detection of a specific malware sample. Report it as an ordinary
  issue (with no sample attached) or through the in-app report.

## How the code is kept safe

In place now:

- **Encrypted storage:** a SQLCipher database factory whose per-database
  secrets are wrapped by an Android Keystore key (StrongBox when
  available). Only `core:security` touches the Keystore and Tink
  ([`docs/01-architecture.md`](docs/01-architecture.md), "Data model").
- **Supply chain:** strict Gradle dependency verification on every build, a
  dependency license allowlist, and a ban on analytics, ads, and attribution
  SDKs ([`docs/02-dependency-policy.md`](docs/02-dependency-policy.md)).
- **CI:** CodeQL analysis for Java and Kotlin, and lint checks for TLS,
  Network Security Config, exported components, WebView, dynamic code
  loading, and world-readable files promoted to errors
  ([`docs/04-quality.md`](docs/04-quality.md)). The latest CI and CodeQL
  runs on `main` fail on dependency verification
  ([`README.md`](README.md), "Status").
- **Workflow hardening:** third-party actions are pinned by commit SHA, the
  default `GITHUB_TOKEN` permissions are read-only, and workflows triggered
  by forks never get secrets.

Designed, and built with the phase that needs it:

- **Signed intel:** bundles use two-tier Ed25519 signing, and the signature
  is verified before any content is parsed. The root key stays offline, and
  no private key lives in the repository (ADR-0006).
- **Isolated parsing:** APKs, archives, DEX, DNS packets, QR codes, media,
  and shared text are parsed with size, depth, and time limits. APK, DEX,
  and media parsing runs in the `:parser` isolated process, which has no
  permissions (NFR-SEC-02). Every parser gets a fuzz harness.
- **No TLS interception:** Aegis never installs a CA certificate or
  decrypts traffic (ADR-0002).
- **Nothing leaves the device** except signed bundle downloads and
  forwarded DNS (NFR-PRIV-01).
