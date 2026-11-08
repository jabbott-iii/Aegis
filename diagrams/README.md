# Diagrams

Pictures of how Aegis is built and how it will work, drawn at commit
`ff54f4b` (Phase 0) from the design in `docs/01-architecture.md`. Each file
is Markdown with Mermaid blocks, which GitHub draws. At that commit only
the Phase 0 foundation exists: the modules `app`, `core:common`,
`core:model`, `core:ui`, `core:data`, `core:security`, and
`testing:common`. Everything else is drawn as planned.

| File | Shows |
| :- | :- |
| `c4-architecture.md` | C4 views: the system and what it deals with, its three processes and the data between them, the components inside each process, the Phase 0 foundation that is built, and the Gradle modules with their dependency rules |
| `scan-sequence.md` | Sequence diagrams of scanning: how a package change is noticed, how one app is scanned across the main and `:parser` processes, and the scheduled and user-started full scans (all planned) |
| `dns-lookup-sequence.md` | Sequence diagrams of the Shield: how it comes up in the `:shield` process, one DNS lookup from an app to the encrypted upstream, and the block counters (all planned) |
| `intel-update-sequence.md` | Sequence diagrams of threat-intel updates: building and signing a bundle in CI, checking a new bundle on the device, and downloading and swapping it in (all planned) |

The design is in `docs/`: `docs/01-architecture.md` for the processes,
modules, components and flows, and `docs/subsystems/` for each module
group. When the code and a diagram disagree, stop and ask which one is
right (`AGENTS.md` rule 17). A change that alters a process, a module's
dependencies, or a flow drawn here updates the diagram in the same change.
As planned parts get built, redraw them solid and update the commit above.
