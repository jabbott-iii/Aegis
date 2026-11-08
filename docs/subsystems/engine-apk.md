# Subsystem: `engine:apk` — APK, ZIP, binary XML and DEX parsing

Parses untrusted APKs and archives, and returns feature structs, never raw
content. It runs in the `:parser` process (`android:isolatedProcess="true"`),
which has no permissions, no network access and no file-system access.
Input arrives as `ParcelFileDescriptor`s over AIDL, so a crash or an exploit
there is contained (`AGENTS.md` rule 6, NFR-SEC-02). It's one of the six pure
Kotlin/JVM modules: no `android.*` or `androidx.*` imports, and it must
apply `aegis.jvm.engine`. Its behavior is specified in
`docs/features/malware-detection.md` ("Static features (`engine:apk`, in
`:parser`)"). Nothing is built yet. Spike S4 chooses the parser and blocks
Phases 1 and 6, and Phase 6 builds the DEX static features (FR-DET-03).

## Contents

| Module or component | Provides |
| :- | :- |
| `ApkAnalyzer` | Parses an untrusted APK (the base APK and its splits) inside `:parser`, with size, entry and time limits. Returns feature structs, never raw content (`docs/01-architecture.md`, "Components") |
| ZIP layer | Entry count, compression ratios, nested APK, DEX or JAR files in `assets/` or `res/raw`, and unusual entry names |
| Binary manifest | A full parse, including what the `PackageManager` view omits, such as `<queries>`, aliases and exported flags |
| DEX | The string table plus method and type references, with no full decompilation. Flags cover dynamic code loading, reflection density, `Runtime.exec`, accessibility gesture APIs, overlay window types, SMS APIs, crypto-mining strings and known packer stubs |
| Native | ELF headers and the imported and exported symbols of `.so` files, plus known native packer names |

The `:parser` process holds the APK, ZIP, DEX, manifest and media parsers
and the feature extraction. Media parsing for the synthetic-media check
runs there too (`ai-guard.md`).

## Output

- **A sparse feature vector plus human-readable evidence strings.** Evidence
  is bounded (at most 256 characters per string) and never includes raw
  file content.
- **Plain structs over AIDL.** The parser returns plain feature structs,
  never raw bytes or file paths.
- **Who uses it:**
  - the ML classifier's features (`engine-detection.md`);
  - heuristic rules that need the static stage, such as H-DROP-01's
    embedded APK or DEX in assets (`engine-detection.md`);
  - the AI SDK fingerprints, which are DEX type prefixes and native library
    names detected in the static stage (AI-2, `ai-guard.md`).
- **A rejection becomes a verdict.** When a limit is exceeded, the parser
  returns `ParseResult.Rejected(reason)` (`core-common-and-model.md`), and
  the verdict is *Unscannable*: informational on its own, *Suspicious* if
  the app is also sideloaded (`docs/features/malware-detection.md`,
  "Verdict aggregation").

## Rules

From `.claude/rules/untrusted-input-parsers.md`, which applies to
`engine/apk/**`:

- **Hard limits, enforced before allocating.** Validate every length field
  against the remaining input before allocating or reading.
- **No unbounded recursion.** Every nested structure (archives in
  archives, DEX type chains) has a depth cap.
- **Reject, don't repair.** Anything malformed is `Rejected`. Never fall
  back to a lenient parse of hostile input.
- **Never execute or load analyzed content.** No `DexClassLoader` on
  scanned files.
- **Every parser gets a Jazzer fuzz harness** in the same change, plus
  regression fixtures for every bug found.

From `.claude/rules/kotlin-engine.md`: explicit API mode, no `!!` (which
`checkSourceRules` enforces under `engine/`), injected clock and
dispatchers, and cancellation checks at entry boundaries.

## Limits

| Limit | Value |
| :- | :- |
| ZIP entries | At most 20,000 |
| Uncompressed size | At most 2 GB in total |
| Compression ratio | At most 100:1 |
| Wall clock | 30 s per APK |
| Nesting depth | A cap on every nested structure; the value isn't set yet |
| Evidence string | At most 256 characters |
| Peak memory | At most 150 MB (spike S4's pass criterion, NFR-PERF-04) |

## Performance (spike S4)

- **The question:** APK and DEX analysis throughput with dexlib2 against a
  custom minimal parser, hashing speed, peak memory on APKs of 150 MB or
  more, and running inside an `isolatedProcess` service.
- **Pass criteria:** the p95 scan of a 100 MB APK takes at most 10 s on a
  mid-range device, and peak memory stays at or below 150 MB.
- **The budget it serves:** a verdict within 10 s of Aegis detecting a new
  or updated app (FR-SCAN-02: p95, 100 MB APK, mid-range device).
- **The dependency:** dexlib2 is the candidate. Its license must pass the
  allowlist (`docs/02-dependency-policy.md`, "License allowlist").

## Tests

- **Fuzzing** (Jazzer): the binary-XML parser, the DEX parser and the ZIP
  walker. A short run on every PR and a long run nightly
  (`docs/04-quality.md`, "Test layers").
- **Malformed input:** the scanner's zip-bomb and malformed-DEX corpus. A
  fixture zip bomb produces *Unscannable* within 30 s, with no crash in the
  main process.
- **Coverage:** at least 80% line coverage through `aegis.jvm.engine`'s
  Kover gate (NFR-MAINT-01).
- **Detection quality:** at most 0.2% of the benign corpus may hit parser
  limits (`docs/04-quality.md`, "Detection quality").

## Open questions

- **Parser choice:** dexlib2 or a custom minimal parser, decided in S4.
