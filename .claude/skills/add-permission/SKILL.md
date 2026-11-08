---
name: add-permission
description: Use before adding any uses-permission, special access, sensitive API, exported component, or foreground service type to Aegis. A checklist that keeps the manifest, the Play policy docs, the privacy docs, the disclosures and the fallbacks in step.
argument-hint: "<permission or component, e.g. android.permission.CAMERA>"
---

# Add a permission or exported component

Request: `$ARGUMENTS`

Every new permission or exported component updates
`docs/permissions-and-policy.md` in the same change, and comes with a
disclosure and a fallback (`AGENTS.md` rule 11). Complete each step before
the manifest change counts as done.

## 1. Justify it

- Which requirement ID needs it? If none, stop.
- Is it on the "Deliberately not requested" list in
  `docs/permissions-and-policy.md`? If so, stop and tell the user that it
  needs an ADR change (`/record-decision`) and their approval.
- Is there a less-privileged alternative: the system picker, the share
  sheet, or a platform API that needs no permission? Prefer it. If you
  don't use it, record why it isn't enough.

## 2. Check the policy

Read the current Google Play policy for this permission or API. Use
WebFetch on `support.google.com/googleplay/android-developer` pages, and
cite the URL and the date you checked it. Record:

- whether it needs a Permissions Declaration Form, a special declaration
  (VpnService, FGS `specialUse`) or a video
- whether the permitted use covers Aegis (antivirus or device security)
- the prominent-disclosure requirements

## 3. Update the docs in the same change

- `docs/permissions-and-policy.md`:
  - "Permission inventory": a row with the permission or API, its
    feature, when it is requested, the Play requirement, and the fallback
    if it is unavailable. Name the permission in backticks, as the table
    does (`CAMERA`): the post-edit hook warns about a manifest permission
    whose short name is missing from this file.
  - "Exported components and merged manifest entries", for an exported
    component.
  - "Prominent disclosures", if user data is accessed.
  - "Data safety form", if the data flow changes.
- `docs/privacy.md` ("Data inventory"), if anything new is read, stored
  or sent.
- `docs/threat-model.md` ("B3. Threats and mitigations"), for a new
  exported component or a new attack surface.
- The feature spec: the flow when the permission is granted, and the flow
  when it is denied.

## 4. Implement

- Keep the manifest entry minimal. Use `android:maxSdkVersion` where it
  applies.
- Request the permission **in context**, right after its own disclosure
  screen (FR-UX-01). Never request it at first launch without context.
- Make the fallback path work when the permission is denied or revoked,
  including revocation while the app is running.
- An exported component must be protected by a permission, or must
  validate every intent extra (type, size and MIME checks) before copying
  content into the sandbox.

## 5. Test

- Instrumented tests for the granted path, the denied path, and the
  revoked-later path.
- Check that the disclosure appears before the system prompt.

## 6. Review

Delegate to the `policy-privacy-reviewer` agent, and to
`security-reviewer` for exported components.

## 7. Report

- The requirement ID, and the Play policy pages you read, with their URLs
  and the dates you checked them.
- The doc sections you updated.
- Each test command with its real result. Instrumented tests need an
  emulator or a device: if none was attached, say they were not run.
- The reviewers' findings, and how each was handled.
