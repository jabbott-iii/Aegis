---
paths:
  - "tools/intel-pipeline/**"
  - "intel/**"
  - "core/security/**"
---

# Threat-intel pipeline, bundles, and signing

Specs: ADR-0006, `docs/DATA_SOURCES.md`, ai-crawler-shield §4–5.

- **No unlisted sources.** A source must be listed in DATA_SOURCES.md, with its terms recorded, before an adapter for it is merged. *To verify* sources must not ship. Use `/add-intel-source`.
- **Fetching happens in CI only.** The app never downloads raw upstream feeds, only signed bundles.
- **Validation gates** (fail the build, don't warn):
  - schema checks
  - IPv4 prefixes no shorter than /16, and IPv6 no shorter than /32
  - no overlap with `canary-allowlist.yaml`
  - churn above 30% against the previous release needs maintainer approval
  - curated configs need cited evidence for every entry
- **Provenance:** every compiled entry records its source ID, operator, and fetch time. Attribution (`attribution.json`) is generated from DATA_SOURCES. CC-BY sources must appear in it.
- **Signing (two-tier Ed25519):**
  - An offline root key signs delegation records. The root key never enters CI or the repo.
  - An online key, valid for 90 days or less, signs bundle manifests inside the protected environment.
  - The app pins the root public keys, with two slots.
- **Client checks, in order:**
  1. the root signature over the delegation record, its validity window, and that the key ID isn't revoked
  2. the online signature over the exact manifest bytes
  3. `version > installed`
  4. `minAppVersion` and `minEngineVersion`
  5. each part's SHA-256

  Then stage, rename atomically, and keep `previous`. An expired bundle is still used, with a warning. It is never discarded.
- **Crypto:** use Tink primitives through `core:security` only. Never hand-roll Ed25519 or AEAD, never compare MACs or signatures with `==`, and never log key material or signatures.
- **Tests:** tampered signature, wrong key ID, revoked key, expired delegation, rollback attempt, truncated part, and power loss during the swap. Test keys are generated per test run or live under `src/test` with names containing `test`.
