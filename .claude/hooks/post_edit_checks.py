#!/usr/bin/env python3
"""PostToolUse checks for the Aegis repository.

Runs after Edit, Write, or MultiEdit and reports project-rule violations back to Claude.
It exits 2 with the findings on stderr, which Claude sees. The edit has already happened,
so these checks are warnings to fix, not blocks.

Checks:
- Kotlin: Android imports in pure modules, `!!` in engine code, GlobalScope, runBlocking,
  and direct logging outside the core:common facade
- AndroidManifest.xml: permissions missing from docs/PERMISSIONS_AND_POLICY.md, and
  exported components
- Gradle build files: inline dependency versions (the version catalog is required)
- strings.xml: forbidden security claims (ui-and-copy rule)

Standard library only. Any internal error exits 0, so a hook bug never blocks work.
"""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

MAX_BYTES = 2 * 1024 * 1024

PURE_MODULE_PREFIXES = (
    "engine/apk/", "engine/signatures/", "engine/heuristics/",
    "shield/dns/", "core/model/", "core/common/",
)
NO_BANG_BANG_PREFIXES = ("engine/", "shield/", "ai/", "intel/", "core/security/")
LOGGING_FACADE_PREFIX = "core/common/"

ANDROID_IMPORT = re.compile(r"^\s*import\s+(android|androidx)\.", re.MULTILINE)
GLOBAL_SCOPE = re.compile(r"\bGlobalScope\b")
RUN_BLOCKING = re.compile(r"\brunBlocking\b")
DIRECT_LOGGING = re.compile(r"(^\s*import\s+android\.util\.Log\b|\bLog\.[vdiwe]\s*\(|\bprintln\s*\()", re.MULTILINE)
STRING_LITERAL = re.compile(r'"(?:\\.|[^"\\\n])*"')
LINE_COMMENT = re.compile(r"//.*$")

USES_PERMISSION = re.compile(r'<uses-permission(?:-sdk-23)?[^>]*android:name\s*=\s*"([^"]+)"')
EXPORTED_TRUE = re.compile(r'android:exported\s*=\s*"true"')

INLINE_DEPENDENCY = re.compile(
    r'\b(implementation|api|ksp|kapt|compileOnly|runtimeOnly|testImplementation|androidTestImplementation|'
    r'debugImplementation|releaseImplementation|classpath)\s*\(\s*"[^"$]+:[^"$]+:[^"$]+"\s*\)'
)

FORBIDDEN_CLAIMS = [
    "100% protection", "100% safe", "guaranteed", "unhackable", "invisible to ai",
    "ai can't access", "ai cannot access", "blocks inbound", "inbound protection",
    "real-time file protection", "completely safe", "fully protected",
]


def rel_path(file_path: str, project_dir: str) -> str:
    try:
        return Path(file_path).resolve().relative_to(Path(project_dir).resolve()).as_posix()
    except Exception:  # noqa: BLE001
        return Path(file_path).as_posix()


def is_test(rel: str) -> bool:
    return bool(re.search(r"(^|/)(src/test|src/androidTest|src/jvmTest|testing)/", rel))


def code_lines(text: str) -> list[tuple[int, str]]:
    """Lines with string literals and line comments removed (approximate, good enough for linting)."""
    out = []
    in_block = False
    for i, line in enumerate(text.splitlines(), start=1):
        if in_block:
            if "*/" in line:
                line = line.split("*/", 1)[1]
                in_block = False
            else:
                continue
        if "/*" in line and "*/" not in line.split("/*", 1)[1]:
            line = line.split("/*", 1)[0]
            in_block = True
        line = STRING_LITERAL.sub('""', line)
        line = LINE_COMMENT.sub("", line)
        out.append((i, line))
    return out


def lines_matching(text: str, rx: re.Pattern[str], limit: int = 5) -> list[int]:
    hits = [n for n, line in code_lines(text) if rx.search(line)]
    return hits[:limit]


def check_kotlin(rel: str, text: str) -> list[str]:
    if is_test(rel) or "/src/main/" not in f"/{rel}":
        return []
    issues: list[str] = []
    if rel.startswith(PURE_MODULE_PREFIXES) and ANDROID_IMPORT.search(text):
        issues.append("Android/AndroidX import in a pure-Kotlin module. Move platform code behind an interface "
                      "with an adapter in engine:scanner, shield:vpn, or app (ARCHITECTURE §3).")
    if rel.startswith(NO_BANG_BANG_PREFIXES):
        lines = lines_matching(text, re.compile(r"!!"))
        if lines:
            issues.append(f"`!!` used at line(s) {lines}. Engine code must handle absence explicitly (kotlin-engine rule).")
    lines = lines_matching(text, GLOBAL_SCOPE)
    if lines:
        issues.append(f"GlobalScope at line(s) {lines}. Use structured concurrency with an injected scope.")
    lines = lines_matching(text, RUN_BLOCKING)
    if lines:
        issues.append(f"runBlocking in main sources at line(s) {lines}.")
    if not rel.startswith(LOGGING_FACADE_PREFIX):
        lines = lines_matching(text, DIRECT_LOGGING)
        if lines:
            issues.append(f"Direct logging (android.util.Log/println) at line(s) {lines}. Use the core:common "
                          "logging facade and keep PII out of logs (PRIVACY.md § Logging).")
    return issues


def check_manifest(rel: str, text: str, project_dir: str) -> list[str]:
    if rel.startswith("testing/fixtures/"):
        return []
    issues: list[str] = []
    policy = Path(project_dir) / "docs" / "PERMISSIONS_AND_POLICY.md"
    policy_text = policy.read_text(encoding="utf-8", errors="replace") if policy.is_file() else ""
    undocumented = sorted({
        name for name in USES_PERMISSION.findall(text)
        if name.rsplit(".", 1)[-1] not in policy_text
    })
    if undocumented:
        issues.append(f"Permission(s) not in docs/PERMISSIONS_AND_POLICY.md: {', '.join(undocumented)}. "
                      "Run /add-permission (disclosure, fallback, Play declaration, PRIVACY update).")
    exported = len(EXPORTED_TRUE.findall(text))
    if exported:
        issues.append(f"{exported} component(s) marked android:exported=\"true\". Confirm each is in the allowed "
                      "exported set, protected or input-validated, and reflected in THREAT_MODEL.md B3.")
    return issues


def check_gradle(rel: str, text: str) -> list[str]:
    if rel.endswith("settings.gradle.kts"):
        return []
    lines = lines_matching_raw(text, INLINE_DEPENDENCY)
    if lines:
        return [f"Inline dependency coordinates at line(s) {lines}. Declare them in gradle/libs.versions.toml "
                "and reference the catalog alias (build-and-dependencies rule)."]
    return []


def lines_matching_raw(text: str, rx: re.Pattern[str], limit: int = 5) -> list[int]:
    hits = [i for i, line in enumerate(text.splitlines(), start=1) if rx.search(line)]
    return hits[:limit]


def check_strings(text: str) -> list[str]:
    lower = text.lower()
    found = [p for p in FORBIDDEN_CLAIMS if p in lower]
    if found:
        return [f"Forbidden or unverified security claim(s): {', '.join(repr(p) for p in found)}. "
                "Use the approved wording in .claude/rules/ui-and-copy.md and the feature specs."]
    return []


def main() -> int:
    try:
        data = json.load(sys.stdin)
        ti = data.get("tool_input") or {}
        file_path = str(ti.get("file_path", ""))
        if not file_path:
            return 0
        project_dir = os.environ.get("CLAUDE_PROJECT_DIR") or data.get("cwd") or os.getcwd()
        path = Path(file_path)
        if not path.is_file() or path.stat().st_size > MAX_BYTES:
            return 0
        text = path.read_text(encoding="utf-8", errors="replace")
        rel = rel_path(file_path, project_dir)
        name = path.name

        issues: list[str] = []
        if name.endswith(".kt"):
            issues = check_kotlin(rel, text)
        elif name == "AndroidManifest.xml":
            issues = check_manifest(rel, text, project_dir)
        elif name.endswith(".gradle.kts"):
            issues = check_gradle(rel, text)
        elif name == "strings.xml" and "/res/values" in f"/{rel}":
            issues = check_strings(text)

        if issues:
            sys.stderr.write(f"Aegis post-edit checks for {rel}:\n" + "\n".join(f"- {i}" for i in issues) + "\n")
            return 2
        return 0
    except Exception:  # noqa: BLE001 - never block work because of a hook bug
        return 0


if __name__ == "__main__":
    sys.exit(main())
