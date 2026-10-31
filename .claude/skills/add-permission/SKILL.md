---
name: add-permission
description: Checklist for adding an Android permission, special access, sensitive API, or exported component to Aegis, keeping manifest, Play policy docs, privacy docs, disclosures, and fallbacks in sync. Use before adding any uses-permission, exported component, or foreground service type.
argument-hint: "[permission or component, e.g. android.permission.CAMERA]"
---

# Add permission or component: $ARGUMENTS

Complete each step before the manifest change is considered done.

## 1. Justify it

- Which requirement ID needs it? If none, stop.
- Is it on the "Deliberately **not** requested" list in `docs/PERMISSIONS_AND_POLICY.md`? If so, stop and tell the user that it needs an ADR change (`/new-adr`) and their approval.
- Is there a less-privileged alternative (the system picker, the share sheet, or a platform API that needs no permission)? Prefer it, and record why it isn't enough if you don't use it.

## 2. Check policy

Read the current Google Play policy for this permission or API. Use WebFetch on `support.google.com/googleplay/android-developer` pages, and cite the URL and the date you checked it. Record:
- whether a Permissions Declaration Form, a special declaration (VpnService, FGS `specialUse`), or a video is required
- whether the permitted use covers Aegis (antivirus or device security)
- the prominent-disclosure requirements

## 3. Update docs (same change)

- `docs/PERMISSIONS_AND_POLICY.md` §1 inventory row: the permission or API, its feature, when it is requested, the Play requirement, and the fallback if unavailable. Update §2 disclosures if user data is accessed, and §6 Data safety if the data flow changes.
- `docs/PRIVACY.md` data inventory, if anything new is read, stored, or sent.
- `docs/THREAT_MODEL.md` B3, for a new exported component or a new attack surface.
- The feature spec: the flow for when the permission is granted and the flow for when it is denied.

## 4. Implement

- The manifest entry is minimal. Use `android:maxSdkVersion` where it applies.
- Request the permission **in context**, right after its own disclosure screen (FR-UX-01). Never request it at first launch without context.
- Make the fallback path work when the permission is denied or revoked, including revocation while the app is running.
- An exported component must be protected by a permission, or must validate every intent extra with type, size, and MIME checks before copying content into the sandbox.

## 5. Test

- Instrumented tests for the granted path, the denied path, and the revoked-later path.
- Check that the disclosure appears before the system prompt.

## 6. Review

Delegate to the `policy-privacy-reviewer` agent, and to `security-reviewer` for exported components.
