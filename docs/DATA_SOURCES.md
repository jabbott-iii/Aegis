# Data Sources and Licensing

*Last updated: 2026-10-31 · Status: Draft. Re-verify terms before each milestone and before any commercial release.*

Every external data source that feeds the intel bundle must be listed here, with its terms,
before the pipeline ingests it. The "Verified" column records what was checked and when. A
source marked *To verify* must not ship until it has been checked.

> This is not legal advice. Treat it as an engineering record of published terms, and get a
> professional review before commercial distribution.

---

## AI crawler ranges

All of these are published by the operators themselves so that site owners can verify crawler
traffic. They are used for the [AI Crawler Shield](features/ai-crawler-shield.md#4-data-crawler-range-sources).

| Source | URL | Terms | Verified |
|---|---|---|---|
| OpenAI: GPTBot, OAI-SearchBot, ChatGPT-User, OAI-AdsBot | `openai.com/{gptbot,searchbot,chatgpt-user,adsbot}.json` (listed on OpenAI's crawler overview page) | Published for verification. There are no explicit redistribution terms, so ship derived prefixes only, never the operator's raw files. | 2026-10-31 (URLs) |
| Perplexity: PerplexityBot, Perplexity-User | `www.perplexity.ai/{perplexitybot,perplexity-user}.json` | Same as above. Perplexity notes that the ranges change regularly. | 2026-10-31 (URLs) |
| Anthropic: ClaudeBot, Claude-User, Claude-SearchBot | `claude.com/crawling/bots.json` (linked from Anthropic's crawler support article) | Same as above | 2026-10-31 (URL) |
| Common Crawl: CCBot | `index.commoncrawl.org/ccbot.json` | Same as above | 2026-10-31 (URL) |
| Apple: Applebot | `search.developer.apple.com/applebot.json` | Same as above | 2026-10-31 (URL) |
| Google: crawlers and fetchers | `developers.google.com/static/crawling/ipranges/*.json` (common-crawlers, special-crawlers, user-triggered-fetchers, user-triggered-fetchers-google, user-triggered-agents) | Same as above | 2026-10-31 (URLs) |
| ai.robots.txt: AI agent names and metadata | `github.com/ai-robots-txt/ai.robots.txt` (`robots.json`) | **MIT**. Used to cross-reference operator and agent names only. | 2026-10-31 |

## Malware and threat IOCs

| Source | Data | Terms (as published) | Verified |
|---|---|---|---|
| **stalkerware-indicators** (Echap) | Package names, certificates, C2 domains and IPs, sample hashes (`ioc.yaml`, `samples.csv`, `generated/*`) | **CC-BY 4.0**. Attribution is required, and is shown in *About › Data sources*. | 2026-10-31 |
| **MalwareBazaar** (abuse.ch) | Malware sample hashes, filterable to Android APKs | The API requires a free `Auth-Key` and is free "under the fair use principles". Commercial or for-profit use "may require a paid subscription". **Decision needed** (open decision #2). | 2026-10-31 |
| URLhaus / ThreatFox (abuse.ch) | Malicious URLs and domains, IOCs | Likely the same fair-use and commercial split as abuse.ch's other services. **To verify** before ingesting. | To verify |
| Phishing feeds (for example, OpenPhish community or PhishTank) | Phishing URLs | **To verify**, per feed | To verify |
| Google Safe Browsing | URL reputation | **Non-commercial use only**, per Google. For commercial use, Google directs developers to **Web Risk**. Also requires an API key, and therefore a backend proxy (open decision #4). | 2026-10-31 |
| AMTSO Feature Settings Checks (Android) | Industry test artifacts for feature validation | For testing only. Not shipped as detection content beyond their test hashes. **Check the participation terms.** | To verify |
| Aegis research | Rules, fingerprints, registry entries written by the maintainers | Owned by the project | n/a |

## Curated configs (maintained in `tools/intel-pipeline/sources/`)

| Config | Contents | Evidence rule |
|---|---|---|
| `ai-services.yaml` | AI inference and API endpoints for egress control | Each host must cite vendor documentation |
| `ai-registry.yaml` | Official AI app packages and certificate digests | Each entry must cite the store listing and a certificate observed on a clean install |
| `ai-sdk-fingerprints.yaml` | DEX prefixes and native library names | Each entry must cite the SDK artifact it was verified against |
| `canary-allowlist.yaml` | Domains and ranges that must never be blocked | Reviewed each release |
| `support-resources.yaml` | Localized victim-support links for the stalkerware safety flow | Reviewed each release |

## ML training data

| Dataset | Use | Terms |
|---|---|---|
| Research APK datasets (for example, AndroZoo) | Training and evaluating the APK classifier | Access is typically granted by agreement for research use. **Verify the terms for any non-academic or commercial use** before training a shipped model. |
| Benign corpus (top apps installed on lab devices) | False-positive evaluation | Use on-device metadata and feature extraction only. Don't redistribute APKs. |

Raw training data and samples **never** go into this repository ([TESTING § sample handling](TESTING.md#2-malware-sample-handling)).

## Attribution in the app

*About › Data sources* lists every shipped source with its license and a link, generated from
this table by the pipeline (`attribution.json` in the bundle). Any CC-BY source **must** appear there.
