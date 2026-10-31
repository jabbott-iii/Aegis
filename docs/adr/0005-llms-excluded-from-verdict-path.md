# ADR-0005: LLMs excluded from the verdict path

- Status: Proposed
- Date: 2026-10-31
- Related: FR-AI-*, FR-DET-*, [THREAT_MODEL § B3](../THREAT_MODEL.md#b3-threats-and-mitigations)

## Context

It is tempting to use an LLM (on-device or cloud) to judge whether a message is a scam, a page
is malicious, or text contains a prompt injection. But the content Aegis analyzes is
**attacker-controlled by definition**. An LLM that reads it can be prompt-injected into
returning "safe". LLM outputs are also non-deterministic, hard to test against fixed gates, and
costly to run on the device.

## Decision

Verdicts come only from **deterministic components**: IOC matching, declarative rules, and ML
classifiers with fixed, versioned thresholds. An LLM may be used **only** to produce
user-facing explanations, and only under these constraints:

1. Its input is limited to Aegis-generated structured evidence, such as rule IDs, field names, and categories. Raw untrusted content is never part of the input.
2. Its output is display-only and never parsed for decisions or actions.
3. A deterministic template is always available as the fallback.

## Consequences

- Verdicts are reproducible and testable against the [detection gates](../TESTING.md#3-detection-quality).
- Aegis can't be talked out of a detection by the content it is scanning.
- Explanations may be less fluent when no on-device model is available.

## Alternatives considered

- **An LLM as a secondary signal:** possible later as a *weak* signal that cannot reduce severity, only raise a finding to human review. Not in 1.0.
- **Cloud LLM classification:** rejected. It is also a privacy issue under [ADR-0001](0001-offline-first-on-device-verdicts.md).
