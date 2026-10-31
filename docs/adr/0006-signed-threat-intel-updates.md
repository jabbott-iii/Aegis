# ADR-0006: Signed, versioned threat-intel bundles with delegated signing

- Status: Proposed
- Date: 2026-10-31
- Related: FR-INTEL-01 … 05, FR-DET-07, [ARCHITECTURE § Intel update](../ARCHITECTURE.md#53-intel-update)

## Context

The intel bundle controls what Aegis blocks and flags. Anyone who can forge a bundle can blind
Aegis or make it block legitimate services, so its integrity has to be at least as strong as the
app's own. Bundles should update daily, ideally from automated CI, but a signing key held in CI is
a high-value secret.

## Decision

**Format.**

- `manifest.json`: version (monotonic integer), createdAt, validUntil, minAppVersion, minEngineVersion, and a list of parts (name, SHA-256, size).
- `manifest.sig`: an Ed25519 signature over the exact manifest bytes.
- The parts themselves, in protobuf or binary formats.

The app verifies the signature **before** parsing anything else, then verifies each part's hash.

**Two-tier keys.** This is a small subset of the ideas in The Update Framework.

- An **offline root key** (Ed25519, held on a hardware token by the maintainer). Its public key is **pinned in the app**, with two slots to allow rotation in a later app release.
- The root signs a **delegation record** for an **online signing key**, valid for 90 days or less. The record contains the key ID, the public key, notBefore, notAfter, and the allowed bundle channels.
- CI signs bundles with the online key inside a protected GitHub Actions environment that has required reviewers. Each bundle ships with the current delegation record.
- The app checks two things: that the root signature over the delegation is valid and inside its time window, and that the online signature over the manifest is valid.

**Freshness and rollback.**

- Reject `version ≤ installed`.
- Accept bundles past `validUntil` only if they are newer than the installed one. Expired protection keeps running, with a warning in the UI.

**Swap.** Write to staging, verify, then rename atomically. Keep `previous` for local rollback.

**Revocation.**

- A compromised online key is limited by the delegation expiry. The root issues a new delegation, and the old key ID goes on a `revokedKeyIds` list inside the next root-signed delegation record.
- A compromised root key requires an app update that rotates the pinned slot.

## Consequences

- Daily releases can be fully automated without the root key ever entering CI.
- The bundle host (GitHub Releases or a CDN) is not trusted for integrity, only for availability. HTTPS is still used, for privacy and defense in depth.
- Implementation is more complex than a single key, because Aegis has to handle delegation parsing, expiry, and revocation lists. All of that gets unit tests and fuzzing.

## Alternatives considered

- **A single key in CI:** simplest, but a CI compromise could forge bundles indefinitely.
- **A single offline key with manual signing:** secure, but daily manual signing isn't sustainable for one maintainer.
- **Full TUF or Sigstore:** a stronger, standard model, but heavier than a single-publisher bundle needs today. Revisit if Aegis ever adds more publishers or channels.
