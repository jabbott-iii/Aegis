# Privacy and Data Handling

*Last updated: 2026-11-01 · Status: Draft*

This is the engineering specification for how Aegis handles data. It is **not** the public
privacy policy. Write that from this document before submission, and keep the two consistent.

---

## Principles

1. **Process on the device.** Verdicts, audits, and analysis happen locally ([ADR-0001](adr/0001-offline-first-on-device-verdicts.md)).
2. **Collect nothing by default.** There are no accounts, analytics, ads SDKs, or automatic sample uploads.
3. **Keep the minimum, for the shortest time.** Store aggregates instead of logs, and verdicts instead of content.
4. **Use explicit, revocable consent** for every sensitive capability, with a working fallback when it is denied.

## Data inventory

| Data | Source | Stored? | Where | Retention | Leaves device? |
|---|---|---|---|---|---|
| Installed app metadata (package, version, certificate digest, permissions) | PackageManager | Yes | `app.db` (encrypted) | While installed | No |
| APK hashes and static features | Scanner | Hashes and fingerprints only | `app.db` | While installed | No |
| Findings and user decisions | Scanner, UI | Yes | `app.db` | 90 days after resolution | Only in a report the user exports |
| DNS lookups | Shield | **No** (in memory only) | — | — | Forwarded to the upstream resolver (see below) |
| Block counters (day, app, category, operator, count) | Shield | Yes | `shield.db` | 7 days | No |
| Notification text | Notification listener (opt-in) | **No** | In memory only | — | No |
| Scam verdict metadata (app, time, verdict, rule IDs) | Scam detector | Yes | `app.db` | 30 days | No |
| Shared text, links, files, media | Share sheet | Copied to a temporary sandbox file for analysis | Cache directory | Deleted after analysis | No |
| Quarantined files | Scanner | Encrypted (Tink AEAD) | App-private storage | Until the user deletes or restores them | No |
| Safe word | AI-7 | Encrypted | `app.db` | Until the user deletes it | No |
| Settings | UI | Yes, not encrypted: choices only (booleans, numbers, enum names). Package names, domains, URLs, and free text go in `app.db`. | DataStore (`files/datastore/settings.preferences_pb`) | Until uninstall | No |
| Database keys | `core:security` | Yes, one per database, all wrapped by one non-exportable Android Keystore key | `noBackupFilesDir/aegis-secrets/` | Until uninstall | No |

All app data is excluded from Auto Backup and device-to-device transfer (`allowBackup=false` plus `dataExtractionRules`).

## What leaves the device

| Flow | Destination | Contents | Identifiers |
|---|---|---|---|
| Intel bundle download | Static host (GitHub Releases or a CDN) | HTTP GET for public files | None beyond what any HTTPS request reveals (IP address, TLS metadata). The user agent is the generic `Aegis/<major>`. |
| DNS forwarding (Shield on) | The upstream the user chose (DoH or DoT), or the network resolver | The DNS queries apps were already making | The upstream sees the device IP and the queried names, exactly as it would without Aegis. This is disclosed in the VPN disclosure and in settings. |
| User-initiated export or report | Wherever the user sends it through the share sheet | The report contents, shown before sending | The user chooses |

**Future features that would change this table** (each needs a PRIVACY update and new Data
safety answers): online URL reputation, Play Integrity verdicts, crash reporting, cloud hash
lookups.

## Logging

- A `core:common` logging facade is the only allowed logger. The `./gradlew checkSourceRules` CI gate fails the build on the direct logging forms it recognizes: `android.util.Log`, `print`/`println` (including `::println` references), `System.out`/`err`/`console`/`getLogger`, `printStackTrace`, `Thread.dumpStack`, and `java.util.logging`. Its one exception is the facade's logcat adapter (`app/…/logging/LogcatSink.kt`). It's a pattern scan over code with comments and strings removed, so code review still has to catch indirect routes such as reflection. detekt also bans the `android.util.Log` import.
- Release builds log nothing at INFO level or below. Messages above that level carry no package names, domains, URLs, file names, or message content. They carry only an event identifier built from enum names (`<area>.<event>`), numeric counts, sizes, or durations, and for errors the exception's class name, never its message. Numbers are never identifiers such as UIDs, IP addresses, ports, or hashes.
- Debug builds may log more, but only to logcat and never to files.
- Libraries that log on their own go through the facade where they allow it. SQLCipher's Java
  logger and leak reports become debug text, which release builds drop. SQLCipher's native layer
  still sends SQLite's error log to logcat, and that can't be redirected. A line can carry an
  error code, the path of one of Aegis's own database files, and the text of a failing SQL
  statement. SQL never contains values (they're always bound arguments), so no user data appears
  there. On an emulator, a wrong key logged only an error code, SQLCipher's own key-check
  statement, and page numbers ([ARCHITECTURE §6](ARCHITECTURE.md#6-data-model-main-appdb)).

## User controls

- Each protection module can be turned off independently, and turning a module off stops its data processing.
- *Settings › Privacy › Delete local data* clears findings, counters, quarantine (after confirmation), and the scan history.
- Uninstalling Aegis removes everything, because nothing is stored outside the app sandbox.

## Special categories

- **Stalkerware victims:** neutral notification wording, no lock-screen detail, and evidence export only when the user asks for it ([safety flow](features/device-security-audit.md#4-stalkerware-safety-flow)).
- **Children:** Aegis is not directed at children and doesn't knowingly collect anything from them. Because it collects nothing, there is nothing to exempt.
