# Community Callable Rollout Evidence

Cycle 116 adds a redacted receipt gate for future live callable rollout proof.
It does not replace owner-approved deploy evidence or Firebase Console App
Check evidence. It defines how private live invocation evidence is validated
before a safe receipt is shared in release or support artifacts.

## Private Evidence

The private evidence file must use:

- `schemaVersion: 1`
- `evidenceKind: communityCallableRolloutExecution`
- `executionStatus: completed`
- `supportReference` matching the release or backend evidence packet
- `projectId`, `executedBy`, `executedAt`, and `ownerApprovalReference`
- `contractHash` from the active `docs/community-callable-contract.json`
- `wireProtocolHash` from the active
  `docs/community-callable-wire-protocol.json`
- `appCheckState.functions` as `monitoring` or `enforced`
- one invocation row for every contracted callable surface

Each invocation row must include the surface key, callable function name,
accepted or duplicate invocation status, App Check token mode, operation ID,
response resource-ID field, response resource ID value, caller UID hash,
private evidence reference, and private evidence hash.

## Receipt Command

Run from the repo root after collecting the private evidence:

```powershell
py -3 tools\community_callable_rollout_receipt.py `
  --contract docs\community-callable-contract.json `
  --wire-protocol docs\community-callable-wire-protocol.json `
  --execution-evidence private\community-callable-rollout-evidence.json `
  --support-reference rollout-<ticket-or-release> `
  --output artifacts\community-callable-rollout-receipt.json
```

The receipt omits project ID, raw operation IDs, raw response resource IDs,
RTDB paths, Storage paths, command output, credentials, and tokens. It keeps
hashes for the manifests, private evidence file, project ID, operation IDs,
resource IDs, caller UID hashes, and per-invocation private evidence.

## Acceptance

The receipt is valid only when:

- The evidence references the current callable contract and Android
  wire-protocol manifests by hash.
- Every contracted surface has one invocation row.
- Function names, App Check token mode, response resource-ID fields, and
  operation-ID prefixes match the manifests.
- Functions App Check state is recorded as monitoring or enforced.
- The receipt validates with
  `validate_callable_rollout_receipt()` or the backend tool unittest suite.

## Remaining Production Gates

Do not claim production callable enforcement from this receipt alone. Production
enforcement still needs owner-approved deploy evidence, Firebase Console App
Check evidence, direct RTDB rule tightening, and rollback notes in
`docs/community-backend-runbook.md`.
