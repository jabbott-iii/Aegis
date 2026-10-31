#!/usr/bin/env python3
"""Self-tests for the Aegis Claude Code hooks. Run: python3 .claude/hooks/test_hooks.py

Each case feeds a hook payload to a hook script and checks the decision or exit code.
Standard library only, and it creates its temporary files in a fresh temp directory.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
GUARD = HERE / "pre_tool_guard.py"
POST = HERE / "post_edit_checks.py"


def run(script: Path, payload: dict, env_extra: dict | None = None) -> tuple[int, str, str]:
    env = dict(os.environ)
    env.update(env_extra or {})
    p = subprocess.run([sys.executable, str(script)], input=json.dumps(payload), text=True,
                       capture_output=True, env=env, timeout=20)
    return p.returncode, p.stdout, p.stderr


def decision(stdout: str) -> str:
    if not stdout.strip():
        return "none"
    return json.loads(stdout)["hookSpecificOutput"]["permissionDecision"]


def guard(tool: str, tool_input: dict) -> str:
    code, out, _ = run(GUARD, {"hook_event_name": "PreToolUse", "tool_name": tool, "tool_input": tool_input})
    assert code == 0, f"guard exited {code}"
    return decision(out)


FAILURES: list[str] = []


def expect(name: str, actual, expected) -> None:
    status = "ok " if actual == expected else "FAIL"
    print(f"[{status}] {name}: {actual!r}" + ("" if actual == expected else f" (expected {expected!r})"))
    if actual != expected:
        FAILURES.append(name)


def guard_cases() -> None:
    expect("bash: plain gradle", guard("Bash", {"command": "./gradlew test"}), "none")
    expect("bash: MalwareBazaar sample download",
           guard("Bash", {"command": "curl -X POST https://mb-api.abuse.ch/api/v1/ -d query=get_file -d sha256_hash=abc -o s.zip"}), "deny")
    expect("bash: MalwareBazaar metadata query allowed",
           guard("Bash", {"command": "curl -X POST https://mb-api.abuse.ch/api/v1/ -d query=get_taginfo -d tag=android"}), "none")
    expect("bash: unzip with sample password",
           guard("Bash", {"command": "unzip -P infected sample.zip"}), "deny")
    expect("bash: grep docs mentioning host",
           guard("Bash", {"command": "grep -rn bazaar.abuse.ch docs/"}), "none")

    # Built by concatenation so this file never contains a literal key marker (the guard would block edits to it).
    pem = "-----BEGIN " + "PRIVATE KEY-----"
    expect("write: private key in main source",
           guard("Write", {"file_path": "/r/core/security/src/main/kotlin/Keys.kt",
                           "content": f"val k = \"{pem}\""}), "deny")
    expect("write: private key in test source allowed",
           guard("Write", {"file_path": "/r/core/security/src/test/kotlin/KeysTest.kt",
                           "content": f"val k = \"{pem}\""}), "none")
    expect("write: hook test file itself is not blocked",
           guard("Write", {"file_path": "/r/.claude/hooks/test_hooks.py",
                           "content": open(__file__, encoding="utf-8").read()}), "none")
    expect("edit: manifest cleartext",
           guard("Edit", {"file_path": "/r/app/src/main/AndroidManifest.xml", "old_string": "x",
                          "new_string": '<application android:usesCleartextTraffic="true">'}), "deny")
    expect("edit: manifest allowBackup false ok",
           guard("Edit", {"file_path": "/r/app/src/main/AndroidManifest.xml", "old_string": "x",
                          "new_string": '<application android:allowBackup="false">'}), "none")
    expect("edit: manifest READ_SMS asks",
           guard("Edit", {"file_path": "/r/app/src/main/AndroidManifest.xml", "old_string": "x",
                          "new_string": '<uses-permission android:name="android.permission.READ_SMS"/>'}), "ask")
    expect("edit: fixture manifest READ_SMS allowed",
           guard("Edit", {"file_path": "/r/testing/fixtures/banker/src/main/AndroidManifest.xml", "old_string": "x",
                          "new_string": '<uses-permission android:name="android.permission.READ_SMS"/>'}), "none")
    expect("edit: accessibility service asks",
           guard("Edit", {"file_path": "/r/app/src/main/AndroidManifest.xml", "old_string": "x",
                          "new_string": '<service android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">'}), "ask")
    expect("write: network config user CAs",
           guard("Write", {"file_path": "/r/app/src/main/res/xml/network_security_config.xml",
                           "content": '<trust-anchors><certificates src="user"/></trust-anchors>'}), "deny")
    expect("write: trust-all hostname verifier",
           guard("Write", {"file_path": "/r/intel/client/src/main/kotlin/Http.kt",
                           "content": "builder.hostnameVerifier { _, _ -> true }"}), "deny")
    expect("edit: analytics SDK in catalog",
           guard("Edit", {"file_path": "/r/gradle/libs.versions.toml", "old_string": "x",
                          "new_string": 'analytics = { module = "com.google.firebase:firebase-analytics" }'}), "deny")
    expect("edit: crash reporting asks",
           guard("Edit", {"file_path": "/r/gradle/libs.versions.toml", "old_string": "x",
                          "new_string": 'sentry = { module = "io.sentry:sentry-android" }'}), "ask")
    expect("edit: macrobenchmark not mistaken for acra",
           guard("Edit", {"file_path": "/r/gradle/libs.versions.toml", "old_string": "x",
                          "new_string": 'macrobenchmark = { module = "androidx.benchmark:benchmark-macro-junit4" }'}), "none")
    code, out, _ = run(GUARD, {"tool_name": "Bash"})  # missing tool_input
    expect("guard: tolerates missing tool_input", (code, decision(out)), (0, "none"))
    p = subprocess.run([sys.executable, str(GUARD)], input="not json", text=True, capture_output=True)
    expect("guard: fails open on malformed input", (p.returncode, p.stdout), (0, ""))


def post_cases() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / "docs").mkdir()
        (root / "docs" / "PERMISSIONS_AND_POLICY.md").write_text("| `INTERNET` | ... | `CAMERA` |", encoding="utf-8")

        def post(rel: str, content: str) -> tuple[int, str]:
            f = root / rel
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_text(content, encoding="utf-8")
            code, _, err = run(POST, {"hook_event_name": "PostToolUse", "tool_name": "Write",
                                      "tool_input": {"file_path": str(f)}, "cwd": str(root)},
                               {"CLAUDE_PROJECT_DIR": str(root)})
            return code, err

        code, err = post("engine/apk/src/main/kotlin/Zip.kt", "import android.util.Log\nval x = y!!\n")
        expect("post: pure module android import + !! + Log", (code, "pure-Kotlin" in err and "`!!`" in err), (2, True))
        code, _ = post("engine/apk/src/main/kotlin/Ok.kt", 'val s = "wow!!"\n// note!!\nfun f(a: Int?) = a ?: 0\n')
        expect("post: !! inside string/comment ignored", code, 0)
        code, _ = post("engine/apk/src/test/kotlin/ZipTest.kt", "val x = y!!\nprintln(x)\n")
        expect("post: tests exempt", code, 0)
        code, err = post("feature/scan/src/main/kotlin/Vm.kt", "GlobalScope.launch { }\n")
        expect("post: GlobalScope flagged", (code, "GlobalScope" in err), (2, True))
        code, err = post("app/src/main/AndroidManifest.xml",
                         '<uses-permission android:name="android.permission.INTERNET"/>\n'
                         '<uses-permission android:name="android.permission.NFC"/>\n'
                         '<activity android:exported="true"/>')
        expect("post: undocumented permission + exported", (code, "NFC" in err and "INTERNET" not in err.split("docs")[0]), (2, True))
        code, err = post("engine/scanner/build.gradle.kts", 'dependencies { implementation("com.squareup.okhttp3:okhttp:4.12.0") }\n')
        expect("post: inline dependency version", (code, "Inline dependency" in err), (2, True))
        code, _ = post("engine/scanner/build.gradle.kts", "dependencies { implementation(libs.okhttp) }\n")
        expect("post: catalog dependency ok", code, 0)
        code, err = post("feature/shield/src/main/res/values/strings.xml",
                         '<string name="x">Your phone is invisible to AI</string>')
        expect("post: forbidden claim", (code, "invisible to ai" in err), (2, True))
        code, _ = post("feature/shield/src/main/res/values/strings.xml",
                       '<string name="x">Blocks connections to known AI crawler networks</string>')
        expect("post: approved claim ok", code, 0)


if __name__ == "__main__":
    guard_cases()
    post_cases()
    print(f"\n{len(FAILURES)} failure(s)")
    sys.exit(1 if FAILURES else 0)
