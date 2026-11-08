#!/usr/bin/env python3
"""PreToolUse guard for the Aegis repository.

Enforces project non-negotiables (see AGENTS.md) that permission rules can't express:

- deny: downloading malware samples, or unpacking password-protected sample archives
        (docs/04-quality.md, Malware sample handling)
- deny: writing secrets (private keys, API tokens) into non-test files
- deny: security-weakening Android config (cleartext, user CAs, backup, debuggable)
- deny: trust-all TLS code
- deny: analytics, ads, or attribution SDK dependencies (NFR-PRIV-02)
- ask:  restricted permissions or an AccessibilityService in production manifests (ADR-0004)
- ask:  crash-reporting SDKs, which need a docs/privacy.md update and opt-in

Input: hook JSON on stdin. Output: a PreToolUse permissionDecision JSON on stdout.
The guard fails open if the input can't be parsed, so a hook bug never blocks all work.
Standard library only.
"""
from __future__ import annotations

import json
import re
import sys
from typing import Iterable

# --- Bash: malware sample handling ------------------------------------------------

MALWARE_HOSTS = re.compile(
    r"(bazaar\.abuse\.ch|mb-api\.abuse\.ch|datalake\.abuse\.ch|androzoo\.uni\.lu|"
    r"virusshare\.com|malshare\.com|vx-underground\.org|virussign\.com)",
    re.IGNORECASE,
)
FETCHERS = re.compile(r"\b(curl|wget|aria2c|python3?|node|fetch|Invoke-WebRequest)\b", re.IGNORECASE)
# Metadata and hash-list queries (for example, MalwareBazaar get_taginfo) are fine. Sample downloads are not.
SAMPLE_DOWNLOAD = re.compile(r"(get_?file|getfile|/download|\.apk\b|\.apks\b|\.xapk\b|\.dex\b|\.zip\b|\.7z\b)", re.IGNORECASE)
SAMPLE_ARCHIVE_PASSWORD = re.compile(
    r"\b(unzip|7z|7za|7zz)\b[^\n]*(-P\s*['\"]?infected|-pinfected|--password[= ]['\"]?infected)",
    re.IGNORECASE,
)

# --- Content: secrets ----------------------------------------------------------------

SECRET_PATTERNS = [
    (re.compile(r"-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"), "a private key block"),
    (re.compile(r"\bAIza[0-9A-Za-z_\-]{35}\b"), "a Google API key"),
    (re.compile(r"\bAKIA[0-9A-Z]{16}\b"), "an AWS access key ID"),
    (re.compile(r"\bgh[pousr]_[A-Za-z0-9]{36,}\b"), "a GitHub token"),
]

# --- Content: Android config ---------------------------------------------------------------

MANIFEST_DENY = [
    (re.compile(r'usesCleartextTraffic\s*=\s*"true"'), "enables cleartext traffic (NFR-SEC-04)"),
    (re.compile(r'android:allowBackup\s*=\s*"true"'), "enables backup of app data (NFR-PRIV-04)"),
    (re.compile(r'android:debuggable\s*=\s*"true"'), "makes the app debuggable (NFR-SEC-05)"),
]
NETWORK_CONFIG_DENY = [
    (re.compile(r'cleartextTrafficPermitted\s*=\s*"true"'), "permits cleartext traffic (NFR-SEC-04)"),
    (re.compile(r'<certificates\s+[^>]*src\s*=\s*"user"'), "trusts user-installed CAs (NFR-SEC-04, ADR-0002)"),
]
RESTRICTED_PERMISSIONS = (
    "READ_SMS|RECEIVE_SMS|SEND_SMS|RECEIVE_MMS|RECEIVE_WAP_PUSH|WRITE_SMS|READ_CALL_LOG|WRITE_CALL_LOG|"
    "PROCESS_OUTGOING_CALLS|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION|ACCESS_BACKGROUND_LOCATION|"
    "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS|PACKAGE_USAGE_STATS|SYSTEM_ALERT_WINDOW|READ_EXTERNAL_STORAGE|"
    "READ_MEDIA_IMAGES|READ_MEDIA_VIDEO|READ_MEDIA_AUDIO|RECORD_AUDIO|READ_CONTACTS|READ_PHONE_STATE"
)
USES_RESTRICTED_PERMISSION = re.compile(
    r'<uses-permission(?:-sdk-23)?[^>]*android:name\s*=\s*"android\.permission\.(' + RESTRICTED_PERMISSIONS + r')"'
)
ACCESSIBILITY_SERVICE = re.compile(
    r'android\.permission\.BIND_ACCESSIBILITY_SERVICE|android\.accessibilityservice\.AccessibilityService'
)

# --- Content: Kotlin/Java TLS ---------------------------------------------------------------

TRUST_ALL = [
    (re.compile(r"hostnameVerifier\s*\{\s*_?\w*\s*,\s*_?\w*\s*->\s*true\s*\}", re.IGNORECASE), "a trust-all HostnameVerifier"),
    (re.compile(r"ALLOW_ALL_HOSTNAME_VERIFIER"), "ALLOW_ALL_HOSTNAME_VERIFIER"),
    (re.compile(r"override\s+fun\s+checkServerTrusted\s*\([^)]*\)\s*(\{\s*\}|=\s*Unit)"), "an empty checkServerTrusted (trust-all TrustManager)"),
]

# --- Content: dependencies -------------------------------------------------------------------

FORBIDDEN_SDKS = re.compile(
    r"(firebase-analytics|firebase-perf|play-services-ads|play-services-analytics|play-services-measurement|"
    r"com\.appsflyer|com\.adjust\.sdk|io\.branch\.sdk|com\.facebook\.android|com\.mixpanel|com\.amplitude|"
    r"com\.segment\.analytics|com\.flurry|com\.applovin|com\.unity3d\.ads|com\.ironsource|com\.chartboost|"
    r"com\.google\.android\.ump)",
    re.IGNORECASE,
)
CRASH_REPORTING_SDKS = re.compile(r"(firebase-crashlytics|io\.sentry|com\.bugsnag|com\.instabug|ch\.acra)", re.IGNORECASE)


def is_test_path(path: str) -> bool:
    p = path.replace("\\", "/")
    return bool(re.search(r"(^|/)(src/test|src/androidTest|src/jvmTest|testing)/", p))


def is_fixture_path(path: str) -> bool:
    return bool(re.search(r"(^|/)testing/fixtures/", path.replace("\\", "/")))


def find(patterns: Iterable[tuple[re.Pattern[str], str]], text: str) -> list[str]:
    return [label for rx, label in patterns if rx.search(text)]


def check_bash(command: str) -> tuple[list[str], list[str]]:
    deny: list[str] = []
    if MALWARE_HOSTS.search(command) and FETCHERS.search(command) and SAMPLE_DOWNLOAD.search(command):
        deny.append(
            "This command downloads a sample from a malware-sample host. Live samples must never enter this working "
            "tree; evaluate real samples only in the isolated lab (docs/04-quality.md, Malware sample handling). Hash or metadata queries "
            "are fine."
        )
    if SAMPLE_ARCHIVE_PASSWORD.search(command):
        deny.append(
            "This command unpacks a password-protected malware sample archive. That isn't allowed in this repo "
            "(docs/04-quality.md, Malware sample handling)."
        )
    return deny, []


def check_content(path: str, text: str) -> tuple[list[str], list[str]]:
    deny: list[str] = []
    ask: list[str] = []
    p = path.replace("\\", "/")
    name = p.rsplit("/", 1)[-1]

    if not is_test_path(p):
        for label in find(SECRET_PATTERNS, text):
            deny.append(f"The new content contains {label}. Secrets must never be written to the repo (docs/05-release.md, Signing and secrets).")

    if name == "AndroidManifest.xml" and not is_fixture_path(p):
        for label in find(MANIFEST_DENY, text):
            deny.append(f"This manifest change {label}.")
        for m in USES_RESTRICTED_PERMISSION.finditer(text):
            ask.append(
                f"This change adds restricted permission {m.group(1)}, which docs/permissions-and-policy.md lists as "
                "deliberately not requested. It needs an ADR or doc change and the user's approval."
            )
        if ACCESSIBILITY_SERVICE.search(text):
            ask.append("This change declares an AccessibilityService. ADR-0004 forbids that in the Aegis app.")

    if "/res/xml/" in p and not is_fixture_path(p):
        for label in find(NETWORK_CONFIG_DENY, text):
            deny.append(f"This network security config change {label}.")

    if name.endswith((".kt", ".java")) and not is_test_path(p):
        for label in find(TRUST_ALL, text):
            deny.append(f"The new code contains {label}. That disables TLS verification (NFR-SEC-04).")

    if name.endswith((".gradle.kts", ".gradle", ".toml")):
        m = FORBIDDEN_SDKS.search(text)
        if m:
            deny.append(f"The dependency '{m.group(1)}' is an analytics, ads, or attribution SDK, which NFR-PRIV-02 forbids.")
        m = CRASH_REPORTING_SDKS.search(text)
        if m:
            ask.append(
                f"The dependency '{m.group(1)}' is a crash-reporting SDK. It requires opt-in, and updates to "
                "docs/privacy.md and the Data safety mapping."
            )

    return deny, ask


def emit(decision: str, reasons: list[str]) -> None:
    out = {
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": decision,
            "permissionDecisionReason": "Aegis guard: " + " | ".join(reasons),
        }
    }
    sys.stdout.write(json.dumps(out))


def main() -> int:
    try:
        data = json.load(sys.stdin)
    except Exception:  # noqa: BLE001 - fail open on malformed input
        return 0
    tool = data.get("tool_name", "")
    ti = data.get("tool_input") or {}

    if tool == "Bash":
        deny, ask = check_bash(str(ti.get("command", "")))
    elif tool in ("Edit", "Write", "MultiEdit"):
        path = str(ti.get("file_path", ""))
        if tool == "Write":
            text = str(ti.get("content", ""))
        elif tool == "Edit":
            text = str(ti.get("new_string", ""))
        else:
            text = "\n".join(str(e.get("new_string", "")) for e in ti.get("edits", []) or [])
        deny, ask = check_content(path, text)
    else:
        return 0

    if deny:
        emit("deny", deny)
    elif ask:
        emit("ask", ask)
    return 0


if __name__ == "__main__":
    sys.exit(main())
