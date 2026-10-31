# Security Policy

Aegis is a security product, so vulnerabilities in it can directly harm users. Reports are welcome.

> **Owner action needed:** enable **Private vulnerability reporting** in the GitHub repository
> settings (*Settings › Code security*) so that the reporting link below works, and confirm the
> response targets.

## Reporting a vulnerability

- Use GitHub's **"Report a vulnerability"** button on the repository's *Security* tab (private advisory).
- **Do not** open public issues for security problems.
- Include the affected version or commit, the device and Android version, reproduction steps, and the impact you observed.

## Scope

In scope:

- The Aegis Android app (all modules)
- The threat-intel bundle format, signature verification, and update client
- `tools/intel-pipeline` and the CI workflows that build and sign bundles
- Bypasses of documented protections, for example, a crafted APK that crashes or evades the scanner because of a parser bug

Out of scope:

- Limits already documented in [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md), such as DoH apps bypassing DNS filtering
- Vulnerabilities in Android itself or in third-party apps (report those to their vendors)
- Missing detection of a specific malware sample. Report those as an ordinary issue (with no sample attached) or through the in-app report.

## Response targets (proposed; owner to confirm)

| Stage | Target |
|---|---|
| Acknowledge the report | Within 7 days |
| Initial assessment | Within 14 days |
| Fix for critical issues (for example, a signature-verification bypass) | As soon as possible, with an out-of-band release |

## Please don't

- Send live malware samples by email or attach them to issues.
- Test against other people's devices or accounts.
