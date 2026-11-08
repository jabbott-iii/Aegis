# C4 component and architecture diagrams

These diagrams show Aegis in the C4 model's views: the system and the
people and systems around it, its containers (the three processes and the
data between them), the components inside the processes, and the Gradle
modules. They were drawn at commit `ff54f4b` (Phase 0) from the design in
`docs/01-architecture.md`. At that commit only the Phase 0 foundation is
built, so most of what is drawn is planned: it is drawn dashed and
labelled "planned". View 4 draws the part that is built.

## Legend

| Style | Means |
| :- | :- |
| Dark blue, rounded | A person |
| Blue box | Part of Aegis that exists at `ff54f4b`: the system, a container or a component |
| Grey box | A system or library outside Aegis |
| Cylinder | Data on the device: a database, a file or a folder |
| White box with a dashed border | Planned: not built yet |
| Solid arrow | Uses, reads, writes or depends on, as labelled |
| Dotted arrow | Planned: not built yet |
| "pure" | Pure Kotlin/JVM: one of the six modules that `checkModuleGraph` keeps free of Android |

## 1. System context

Aegis on the device, with the user, the OS, the other apps, and the
services it will reach. At `ff54f4b` the app is the Phase 0 shell, which
offers no protection yet. Of the connections drawn, only the user's and the
Keystore's exist.

```mermaid
flowchart TB
    user(["User"])

    subgraph device["Android device"]
        aegis["Aegis app<br/>today the Phase 0 shell, with no protection yet"]
        apps["Other installed apps"]
        os["Android OS<br/>PackageManager, VpnService, NotificationListener, Settings"]
        keystore["Android Keystore<br/>StrongBox where the device has it, otherwise the default Keystore, which is the TEE on most devices"]
    end

    bundles["Signed intel bundles<br/>static hosting on GitHub Releases or a CDN, no user data: planned"]
    upstream["Encrypted DNS upstream<br/>DoH or DoT, chosen by the user"]
    gha["GitHub Actions<br/>runs the intel pipeline"]
    sources["Public sources<br/>IOC feeds, crawler IP ranges"]

    user -->|"uses"| aegis
    aegis -->|"wraps each database secret with a non-exportable AES-256-GCM key"| keystore
    aegis -.->|"PackageManager, VpnService, NotificationListener, Settings: planned"| os
    apps -->|"run on"| os
    apps -.->|"DNS queries, through the Shield's TUN: planned"| aegis
    aegis -.->|"HTTPS GET, no identifiers: planned"| bundles
    aegis -.->|"forwarded DNS queries: planned"| upstream
    sources -.->|"fetched by tools/intel-pipeline: planned"| gha
    gha -.->|"publishes signed bundles: planned"| bundles

    classDef person fill:#08427b,stroke:#052e56,color:#ffffff
    classDef system fill:#1168bd,stroke:#0b4884,color:#ffffff
    classDef external fill:#8a8a8a,stroke:#6b6b6b,color:#ffffff
    classDef planned fill:#ffffff,stroke:#888888,stroke-dasharray: 5 5,color:#555555
    class user person
    class aegis system
    class apps,os,keystore,upstream,gha,sources external
    class bundles planned
```

- **Key property:** no user data leaves the device. Aegis makes two kinds
  of outbound connection: it downloads signed bundles, and it forwards DNS
  queries that apps would have sent anyway, to an encrypted upstream that
  the user chooses (`docs/privacy.md`, "What leaves the device").
- The bundle host is static hosting and holds no user data.

## 2. Containers

The three processes and the data they keep on the device. Only the main
process exists at `ff54f4b`. The settings DataStore and the database
secrets are built in `core:data` and `core:security`.

```mermaid
flowchart LR
    user(["User"])
    apps["Other installed apps"]
    os["Android OS<br/>binds the VPN service, PackageManager, getConnectionOwnerUid"]
    keystore["Android Keystore"]
    upstream["Encrypted DNS upstream<br/>DoH or DoT"]
    bundles["Intel bundle host<br/>GitHub Releases or a CDN: planned"]

    subgraph dev["Aegis on the device"]
        main["Main process<br/>Kotlin and Compose. Today the Phase 0 shell. Planned: UI, scan orchestration, WorkManager workers, intel client, audit"]
        shield[":shield process<br/>planned: AegisVpnService, DNS engine, route builder, event aggregation"]
        parser[":parser process, isolatedProcess<br/>planned: APK, ZIP, DEX, manifest and media parsers, feature extraction. No permissions, no network access, no file-system access"]
        settings[("Settings DataStore<br/>files/datastore/settings.preferences_pb, multi-process, choices only")]
        secrets[("Database secrets<br/>noBackupFilesDir/aegis-secrets/, one wrapped secret per database")]
        appdb[("app.db<br/>Room on SQLCipher: planned")]
        shielddb[("shield.db<br/>BlockCounter aggregates, kept 7 days: planned")]
        intel[("Intel bundle files<br/>intel/current, intel/staging, intel/previous: planned")]
    end

    user -->|"uses"| main
    main -->|"core:data opens it"| settings
    main -->|"core:security creates and reads them, under a file lock"| secrets
    main -->|"core:security wraps and unwraps them through Tink"| keystore
    shield -.->|"reads settings"| settings
    shield -.->|"its own database's secret"| secrets
    main -.->|"reads and writes"| appdb
    shield -.->|"writes aggregated counters only"| shielddb
    main -.->|"reads the counters"| shielddb
    main -.->|"ParcelFileDescriptors over AIDL"| parser
    parser -.->|"feature structs, never raw content"| main
    main -.->|"getChangedPackages"| os
    os -.->|"binds it through BIND_VPN_SERVICE"| shield
    shield -.->|"getConnectionOwnerUid"| os
    apps -.->|"DNS queries to the virtual resolver, through the TUN"| shield
    shield -.->|"forwards allowed queries over a protected socket"| upstream
    main -.->|"HTTPS GET: manifest.json, manifest.sig, parts"| bundles
    main -.->|"writes staging, swaps it in, memory-maps parts read-only"| intel
    shield -.->|"memory-maps parts read-only"| intel
    main -.->|"tells it to reload after a swap"| shield

    classDef person fill:#08427b,stroke:#052e56,color:#ffffff
    classDef container fill:#438dd5,stroke:#2e6295,color:#ffffff
    classDef external fill:#8a8a8a,stroke:#6b6b6b,color:#ffffff
    classDef planned fill:#ffffff,stroke:#888888,stroke-dasharray: 5 5,color:#555555
    class user person
    class main,settings,secrets container
    class apps,os,keystore,upstream external
    class shield,parser,appdb,shielddb,intel,bundles planned
```

- The main process and `:shield` share configuration through the settings
  DataStore and keep separate Room database files. `:shield` writes only
  aggregated counters, which the main process reads.
- Both processes memory-map intel bundle parts read-only.
- `:shield` is its own process so that the VPN survives UI crashes and
  stays small. `:parser` is isolated so that a crash or exploit while
  parsing hostile input is contained.
- Secrets are created under a file lock, because the main process and
  `:shield` can both start first.
- Settings aren't encrypted, so they hold only choices: booleans, numbers,
  and enum names. Anything that identifies the user's apps, sites, or
  content goes in an encrypted database.
- At `ff54f4b` the settings DataStore, the secret store and the database
  factory are built and tested, but no feature uses them yet.

## 3. Components of the processes

The planned components, by process, as `docs/01-architecture.md`
("Components") lists them. None is built yet.

```mermaid
flowchart LR
    subgraph main["main process"]
        ui["feature:* UI<br/>Compose and ViewModels"]
        so["ScanOrchestrator<br/>turns triggers into scan jobs, deduplicates and caches by (path, size, lastUpdateTime)"]
        pipe["DetectorPipeline<br/>detectors in order of cost: IOC, metadata heuristics, AI guard, static features, ML"]
        guard["AI Guard detectors"]
        agg["VerdictAggregator<br/>precedence rules: ML alone never produces Malicious"]
        aud["AuditService<br/>device posture and privacy checks"]
        ic["IntelClient<br/>downloads, verifies, swaps in atomically"]
        db[("app.db<br/>encrypted")]
    end
    subgraph parser[":parser, isolated"]
        apk["ApkAnalyzer<br/>manifest, DEX, native code, archives, within size, entry and time limits"]
    end
    subgraph shield[":shield process"]
        vpn["AegisVpnService<br/>split-routed TUN, packet loop, revocation, coexistence"]
        rb["RouteBuilder<br/>routes for the virtual DNS address and the enabled blocked prefixes, within the route cap"]
        dns["DnsEngine<br/>DNS parser, matcher, upstream"]
        attr["UidAttributor<br/>getConnectionOwnerUid, with a short-lived cache"]
        sdb[("shield.db")]
    end
    intel[("Intel bundle<br/>memory-mapped, read-only")]

    ui -.->|"user actions"| so
    so -.->|"scan jobs"| pipe
    pipe -.->|"PFD over AIDL"| apk
    apk -.->|"feature structs, never raw content"| pipe
    pipe -.-> guard
    pipe -.->|"signals"| agg
    agg -.->|"writes"| db
    ui -.-> aud
    ic -.->|"installs bundles"| intel
    pipe -.->|"reads"| intel
    vpn -.-> rb
    vpn -.-> dns
    dns -.-> attr
    dns -.->|"reads"| intel
    rb -.->|"reads"| intel
    dns -.->|"writes counters"| sdb
    ui -.->|"reads counters"| sdb

    classDef planned fill:#ffffff,stroke:#888888,stroke-dasharray: 5 5,color:#555555
    class ui,so,pipe,guard,agg,aud,ic,db,apk,vpn,rb,dns,attr,sdb,intel planned
```

- Detectors implement `Detector.evaluate(target, ctx)`, which returns a
  list of `Signal`s, so the AI guard and future detectors plug in without
  changing the orchestrator.
- `diagrams/scan-sequence.md`, `diagrams/dns-lookup-sequence.md` and
  `diagrams/intel-update-sequence.md` follow these components through
  their flows.

## 4. The Phase 0 foundation

What is built at `ff54f4b`: the secret store, the encrypted database
factory, and the settings DataStore, with the logging facade they report
through. `docs/01-architecture.md` ("Encryption") has every rule they
follow.

```mermaid
flowchart TB
    subgraph m_security["core:security"]
        store["WrappedSecretStore<br/>one 32-byte random secret per database, created under a file lock"]
    end
    subgraph m_data["core:data"]
        factory["EncryptedDatabaseFactory<br/>checks the key on one connection before Room sees the database, keeps unreadable files, takes migrations and nothing else"]
        settingsds["Settings DataStore<br/>one multi-process Preferences DataStore, reset to defaults with a marker if corrupt"]
    end
    subgraph m_common["core:common"]
        facade["Logging facade<br/>PII-safe, release builds drop debug text"]
    end

    secretsdir[("noBackupFilesDir/aegis-secrets/<br/>64-byte secret files, format 1")]
    dbfiles[("Encrypted database files<br/>a test-only database until Phase 1")]
    settingsfile[("files/datastore/settings.preferences_pb")]
    tink["Tink<br/>AndroidKeystore AEAD, the secret's ID as associated data"]
    keystore["Android Keystore<br/>one non-exportable AES-256-GCM key, in StrongBox when the device has it"]
    sqlcipher["SQLCipher for Android and Room 3<br/>SQLCipherDriver"]
    logcat["logcat"]

    factory -->|"gets the database's secret"| store
    store -->|"encrypts and decrypts each secret"| tink
    tink -->|"with the wrapping key"| keystore
    store -->|"writes a temporary file, renames it into place, syncs the directory"| secretsdir
    factory -->|"passes the secret as a raw key, x'…'"| sqlcipher
    sqlcipher -->|"reads and writes"| dbfiles
    sqlcipher -->|"Java logger and CloseGuard reports, as debug text"| facade
    sqlcipher -->|"native SQLite error log, which Java can't redirect"| logcat
    settingsds -->|"reads and writes"| settingsfile

    classDef component fill:#85bbf0,stroke:#5d82a8,color:#000000
    classDef external fill:#8a8a8a,stroke:#6b6b6b,color:#ffffff
    class store,factory,settingsds,facade,secretsdir,dbfiles,settingsfile component
    class tink,keystore,sqlcipher,logcat external
```

- The wrapping key needs no user authentication, because scheduled scans
  and the Shield open their databases while the screen is locked.
- A new key must pass an encrypt/decrypt round trip, or it's deleted. The
  key is created only while no secret is stored, because before Android 12
  the Keystore can report a key as missing when it's only failing.
- Nothing is deleted automatically. A secret that can't be read is
  reported as `KEY_LOST` or `UNREADABLE`, and a database that a key can't
  open is kept and reported as `Unreadable`. What the app does then is open
  decision #9 (`docs/00-overview.md`, "Open decisions").
- SQLite's native error log can include an error code, a database path,
  and the text of a failing SQL statement, so SQL never contains values:
  every value is a bound argument.

## 5. Modules

The Gradle modules by group, with the dependency rules drawn as arrows.
The seven modules of Phase 0 are built. The rest are added with
`/new-module` when their phase starts. `docs/01-architecture.md`
("Dependency rules") has every rule that `checkModuleGraph` enforces.

```mermaid
flowchart TB
    subgraph g_app["app"]
        n_app["app<br/>Application, nav graph, DI root, manifest merge point. Android adapters"]
    end
    subgraph g_feature["feature: planned"]
        n_feature["feature:dashboard, scan, shield, ai, audit, onboarding, settings<br/>Compose UI and ViewModels. Never depend on each other"]
    end
    subgraph g_engine["engine: planned"]
        n_scanner["engine:scanner<br/>Android adapter: scan orchestration, detector pipeline, verdict aggregation"]
        n_apk["engine:apk<br/>pure: APK, ZIP, binary XML and DEX parsing, run in :parser"]
        n_sig["engine:signatures<br/>pure: IOC matching by hash, by package and certificate, or by certificate"]
        n_heur["engine:heuristics<br/>pure: declarative rules engine, the rules ship in the intel bundle"]
        n_ml["engine:ml<br/>LiteRT model wrapper and feature vectors"]
    end
    subgraph g_shield["shield: planned"]
        n_vpn["shield:vpn<br/>Android adapter: VpnService, TUN I/O, route builder, coexistence detection"]
        n_dns["shield:dns<br/>pure: DNS wire parser, domain matcher, DoH and DoT upstream, UID attribution"]
    end
    subgraph g_ai["ai: planned"]
        n_guard["ai:guard<br/>pure wherever possible, still open: impersonation, AI SDK fingerprints, agent guard, scam and injection detectors, C2PA"]
    end
    subgraph g_intel["intel: planned"]
        n_client["intel:client<br/>bundle download, verification, rollback protection, atomic swap"]
    end
    subgraph g_core["core: built in Phase 0"]
        n_common["core:common<br/>pure: result types, dispatchers, clock, PII-safe logging facade"]
        n_model["core:model<br/>pure: domain models"]
        n_data["core:data<br/>encrypted Room databases, DataStore, repositories"]
        n_security["core:security<br/>Keystore, Tink wrappers, signature verification, integrity self-check"]
        n_ui["core:ui<br/>design system: theme, status components, icons"]
    end
    subgraph g_other["tools and tests"]
        n_pipeline["tools:intel-pipeline<br/>JVM CLI run in CI: fetch, normalize, validate, compile, sign"]
        n_tcommon["testing:common<br/>fakes, test dispatchers, fixtures loader"]
        n_fixtures["testing:fixtures<br/>benign fixture APKs that emulate risky profiles, never real malware"]
    end
    tink["Tink<br/>com.google.crypto.tink"]
    okhttp["OkHttp<br/>com.squareup.okhttp3"]

    g_feature -.->|"core:*"| g_core
    g_feature -.->|"the engine APIs it needs"| g_engine
    g_feature -.->|"the shield APIs it needs"| g_shield
    n_security -->|"the only module that touches Tink"| tink
    n_client -.->|"allowed: bundle downloads"| okhttp
    n_dns -.->|"allowed: the DoH upstream"| okhttp

    classDef component fill:#85bbf0,stroke:#5d82a8,color:#000000
    classDef external fill:#8a8a8a,stroke:#6b6b6b,color:#ffffff
    classDef planned fill:#ffffff,stroke:#888888,stroke-dasharray: 5 5,color:#555555
    class n_app,n_common,n_model,n_data,n_security,n_ui,n_tcommon component
    class n_feature,n_scanner,n_apk,n_sig,n_heur,n_ml,n_vpn,n_dns,n_guard,n_client,n_pipeline,n_fixtures planned
    class tink,okhttp external
```

- Only `core:security` touches the Keystore and Tink. Only `intel:client`
  performs network I/O for bundles. `checkModuleGraph` fails any other
  module that reaches Tink or OkHttp except through an allowed module, or
  sees either on its compile classpath. The Keystore rule and the bundle
  network rule are reviewed by hand.
- A pure module never applies an Android plugin and never reaches an
  Android module or library on a production classpath.
- A built module may still hold only part of what its box lists.

## The modules

Every module in the layout, whether it exists at `ff54f4b`, and what kind
of module it is.

| Module | Path | Status | Kind | Holds |
| :- | :- | :- | :- | :- |
| `app` | `app` | Built (Phase 0) | Android application (`aegis.android.application`), an Android adapter | Application, nav graph, DI root, manifest merge point |
| `core:common` | `core/common` | Built (Phase 0) | Pure Kotlin/JVM (`aegis.jvm.engine`) | Result types, dispatchers, clock, PII-safe logging facade |
| `core:model` | `core/model` | Built (Phase 0) | Pure Kotlin/JVM (`aegis.jvm.engine`) | Domain models: `AppRecord`, `Finding`, `Verdict`, `Severity`, `IntelVersion`… |
| `core:data` | `core/data` | Built (Phase 0) | Android library | Room databases (encrypted), DataStore, repositories |
| `core:security` | `core/security` | Built (Phase 0) | Android library | Keystore, Tink wrappers, signature verification, integrity self-check |
| `core:ui` | `core/ui` | Built (Phase 0) | Android library with Compose | Design system: theme, status components, icons |
| `testing:common` | `testing/common` | Built (Phase 0) | Plain Kotlin/JVM (`aegis.jvm.library`) | Fakes, test dispatchers, fixtures loader |
| `engine:apk` | `engine/apk` | Planned | Pure Kotlin/JVM | APK, ZIP, binary XML and DEX parsing (runs in `:parser`) |
| `engine:signatures` | `engine/signatures` | Planned | Pure Kotlin/JVM | IOC matching by hash, by package and certificate, or by certificate |
| `engine:heuristics` | `engine/heuristics` | Planned | Pure Kotlin/JVM | Declarative rules engine (rules ship in the intel bundle) |
| `engine:ml` | `engine/ml` | Planned | Pure Kotlin/JVM wherever possible, not one of the six checked | LiteRT model wrapper and feature vectors |
| `engine:scanner` | `engine/scanner` | Planned | Android adapter | Scan orchestration, detector pipeline, verdict aggregation |
| `intel:client` | `intel/client` | Planned | Not stated yet | Bundle download, verification, rollback protection, atomic swap |
| `shield:vpn` | `shield/vpn` | Planned | Android adapter | `VpnService`, TUN I/O, route builder, coexistence detection |
| `shield:dns` | `shield/dns` | Planned | Pure Kotlin/JVM | DNS wire parser, domain matcher, upstream (DoH/DoT), UID attribution |
| `ai:guard` | `ai/guard` | Planned | Pure Kotlin/JVM wherever possible; whether it can be pure is still open | Impersonation, AI SDK fingerprints, agent guard, scam and injection detectors, C2PA |
| `feature:dashboard`, `feature:scan`, `feature:shield`, `feature:ai`, `feature:audit`, `feature:onboarding`, `feature:settings` | `feature/*` | Planned | Compose UI with ViewModels | The screens. Feature modules never depend on each other |
| `tools:intel-pipeline` | `tools/intel-pipeline` | Planned | JVM CLI run in CI | Fetch → normalize → validate → compile → sign |
| `testing:fixtures` | `testing/fixtures` | Planned | Fixture APKs, which will need their own convention plugin | Benign fixture APKs that emulate risky profiles (never real malware) |

## Sources

- `docs/01-architecture.md`: every section
- `settings.gradle.kts`, for the modules that exist
- `build-logic/convention/src/main/kotlin/io/github/jabbottiii/aegis/buildlogic/ModuleGraph.kt`,
  for the pure modules and the libraries only some modules may use
- the `build.gradle.kts` of each existing module, for its convention
  plugins
- `core/data/src/main/kotlin/io/github/jabbottiii/aegis/core/data/database/EncryptedDatabaseFactory.kt`,
  for the factory's use of the secret store
- `app/src/main/AndroidManifest.xml`, for the app shell's one process
