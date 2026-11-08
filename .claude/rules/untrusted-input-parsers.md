---
paths:
  - "engine/apk/**"
  - "shield/dns/**"
  - "ai/guard/**"
  - "intel/client/**"
  - "app/src/main/**/share/**"
---

# Rules for untrusted input and parsers

Everything parsed here is attacker-controlled: APKs, ZIPs, binary XML, DEX,
ELF, DNS packets, QR payloads, shared text and media, and bundle manifests
before their signature is verified. See `docs/threat-model.md` B2 and B3.

- **Hard limits, enforced before allocating.** Validate every length field
  against the remaining input before you allocate or read.
  - **ZIP:** at most 20k entries, at most 2 GB uncompressed in total,
    compression ratio of 100:1 or less, and a 30 s wall clock per APK.
  - **DNS:** names of 255 bytes or less, labels of 63 bytes or less, and a
    compression-pointer hop cap with loop detection.
- **No unbounded recursion.** Every nested structure (archives in archives,
  compression pointers, DEX type chains) needs a depth cap.
- **Reject, don't repair.** Return `ParseResult.Rejected(reason)` for
  anything malformed. Never fall back to a lenient parse of hostile input.
- **Isolation.** APK, DEX, archive, and media parsing runs in the `:parser`
  service (`android:isolatedProcess="true"`). The parser receives
  `ParcelFileDescriptor`s and returns plain feature structs over AIDL, never
  raw bytes or file paths.
- **Intel bundles:** verify the signature, then check part hashes, then
  parse. Nothing in a bundle is parsed until `core:security` has verified
  `manifest.sig` against the pinned keys and delegation record (ADR-0006).
- **Text inputs** (shared text, notifications, QR codes) are analyzed only
  in memory.
  - Never write them to disk, logs, or the database (NFR-PRIV-03).
  - Normalize Unicode for matching, but keep the original string for
    hidden-character detection.
- **Never execute or load analyzed content.**
  - No `DexClassLoader` on scanned files.
  - No `WebView` rendering of scanned URLs.
  - No network fetch of a URL being checked.
- **Every parser gets a Jazzer fuzz harness** in the same change, plus
  regression fixtures for every bug found (`docs/04-quality.md`, "Test
  layers").
