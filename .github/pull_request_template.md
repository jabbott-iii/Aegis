## What and why

<!-- What changed, and why. -->

**Requirement IDs:** <!-- FR-*, NFR-*, or spike S<n>. Every change maps to at least one. -->

## Checklist

- [ ] **Permissions or policy:** no new permission, exported component, or sensitive API, *or* `docs/PERMISSIONS_AND_POLICY.md` is updated with a disclosure and a fallback.
- [ ] **Privacy:** the data inventory in `docs/PRIVACY.md` is still accurate. Nothing new leaves the device. Logs carry no PII, and logger `values` are counts, sizes, or durations, never UIDs, addresses, ports, hashes, or timestamps.
- [ ] **Threat model:** no review trigger applies (THREAT_MODEL §B4), *or* `docs/THREAT_MODEL.md` is updated.
- [ ] **Docs:** the feature spec and any ADR are updated in this PR.
- [ ] **Supply chain:** any new dependency is in the version catalog, has an allowed license, and `gradle/verification-metadata.xml` is regenerated.
- [ ] **Honest claims:** new UI copy states only verified protection.

## Screenshots

<!-- For UI changes: light, dark, and 200% font scale. -->
