# Crash diagnostics

Aura does not use automatic crash analytics. If a release-only crash, freeze, or ANR happens, users can create a local diagnostics bundle from:

`Settings` > `Diagnostics` > `Crash diagnostics bundle`

The bundle includes:

- Aura version and build type.
- Android version, security patch, device model, and ABI.
- Active source/provider context from the current session.
- Auto-wallpaper and scheduler source settings.
- Last local crash timestamp and sanitized `crash.log` tail.
- Reproduction fields for crash or ANR reports.

The bundle is local until the user taps `Copy` or `Share`. Aura does not upload it automatically.

## Redaction

The generated bundle redacts:

- Bearer tokens and authorization headers.
- Provider query-string values named `apikey`, `key`, `token`, and `client_id`.
- API keys, tokens, passwords, secrets, client IDs, and dotted provider
  property names in assignment form, including `local.properties` entries.
- App-private Android paths under `com.freevibe`.
- `file://` paths.

The same request redactor is used before in-app source diagnostics store the
last provider error detail for the current session.

Before opening a GitHub issue, paste the bundle into the `Crash or ANR report` issue template and fill in the reproduction fields.
