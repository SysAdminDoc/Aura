from __future__ import annotations

import copy
import json
from pathlib import Path
import unittest

from tools.community_account_deletion_review import ReviewError, sha256_json
from tools.community_callable_rollout_receipt import (
    build_callable_rollout_receipt,
    dump_callable_rollout_receipt,
    validate_callable_rollout_receipt,
)


REPO_ROOT = Path(__file__).resolve().parents[2]


def read_json(relative_path: str) -> dict[str, object]:
    return json.loads((REPO_ROOT / relative_path).read_text(encoding="utf-8"))


def live_contract() -> dict[str, object]:
    return read_json("docs/community-callable-contract.json")


def live_protocol() -> dict[str, object]:
    return read_json("docs/community-callable-wire-protocol.json")


def execution_evidence(
    contract: dict[str, object] | None = None,
    protocol: dict[str, object] | None = None,
) -> dict[str, object]:
    contract = contract or live_contract()
    protocol = protocol or live_protocol()
    invocations: list[dict[str, object]] = []
    for index, surface in enumerate(protocol["surfaces"], start=1):  # type: ignore[index]
        token_mode = "limitedUse" if surface["consumeLimitedUseAppCheckToken"] else "standard"
        prefix = surface["operationPrefixes"][0]  # type: ignore[index]
        invocations.append(
            {
                "surfaceKey": surface["surfaceKey"],
                "functionName": surface["functionName"],
                "invocationStatus": "accepted" if index % 2 else "duplicate",
                "appCheckTokenMode": token_mode,
                "operationId": f"{prefix}_operation_{index}",
                "resourceIdField": surface["resultResourceIdField"],
                "resourceIdValue": f"private-resource-{index}",
                "authUidHash": "a" * 64,
                "privateEvidenceReference": f"private-rollout/callable-{index}.json",
                "privateEvidenceHash": f"{index}" * 64,
            }
        )
    return {
        "schemaVersion": 1,
        "evidenceKind": "communityCallableRolloutExecution",
        "executionStatus": "completed",
        "supportReference": "rollout-123",
        "projectId": "aura-production",
        "executedBy": "owner-ops",
        "executedAt": "2026-06-07T20:00:00Z",
        "ownerApprovalReference": "approval-callables-123",
        "privateEvidenceReference": "private-rollout/raw-callables.json",
        "privateEvidenceHash": "f" * 64,
        "contractHash": sha256_json(contract),
        "wireProtocolHash": sha256_json(protocol),
        "appCheckState": {"functions": "monitoring"},
        "invocations": invocations,
    }


class CommunityCallableRolloutReceiptTest(unittest.TestCase):
    def test_callable_rollout_receipt_redacts_project_and_invocation_ids(self) -> None:
        receipt = build_callable_rollout_receipt(
            live_contract(),
            live_protocol(),
            execution_evidence(),
            support_reference="rollout-123",
            receipted_at="2026-06-07T20:05:00Z",
        )
        receipt_text = dump_callable_rollout_receipt(receipt)

        self.assertEqual("callablesInvoked", receipt["executionStatus"])
        self.assertEqual(7, receipt["callableSurfaceCount"])
        self.assertEqual("monitoring", receipt["appCheckFunctionsState"])
        self.assertIn("contractHash", receipt)
        self.assertIn("wireProtocolHash", receipt)
        self.assertNotIn("aura-production", receipt_text)
        self.assertNotIn("private-resource-1", receipt_text)
        self.assertNotIn("report_operation_1", receipt_text)
        self.assertNotIn("private-rollout/raw-callables.json", receipt_text)
        validate_callable_rollout_receipt(receipt, support_reference="rollout-123")

    def test_callable_rollout_receipt_rejects_missing_surface(self) -> None:
        evidence = execution_evidence()
        evidence["invocations"] = evidence["invocations"][:-1]  # type: ignore[index]

        with self.assertRaises(ReviewError):
            build_callable_rollout_receipt(
                live_contract(),
                live_protocol(),
                evidence,
                support_reference="rollout-123",
            )

    def test_callable_rollout_receipt_rejects_token_mode_drift(self) -> None:
        evidence = execution_evidence()
        evidence["invocations"][0]["appCheckTokenMode"] = "standard"  # type: ignore[index]

        with self.assertRaises(ReviewError):
            build_callable_rollout_receipt(
                live_contract(),
                live_protocol(),
                evidence,
                support_reference="rollout-123",
            )

    def test_callable_rollout_receipt_rejects_operation_prefix_drift(self) -> None:
        evidence = execution_evidence()
        evidence["invocations"][0]["operationId"] = "wrong_operation_1"  # type: ignore[index]

        with self.assertRaises(ReviewError):
            build_callable_rollout_receipt(
                live_contract(),
                live_protocol(),
                evidence,
                support_reference="rollout-123",
            )

    def test_callable_rollout_receipt_rejects_manifest_hash_drift(self) -> None:
        evidence = execution_evidence()
        evidence["wireProtocolHash"] = "0" * 64

        with self.assertRaises(ReviewError):
            build_callable_rollout_receipt(
                live_contract(),
                live_protocol(),
                evidence,
                support_reference="rollout-123",
            )

    def test_callable_rollout_receipt_rejects_unknown_app_check_state(self) -> None:
        evidence = execution_evidence()
        evidence["appCheckState"] = {"functions": "disabled"}

        with self.assertRaises(ReviewError):
            build_callable_rollout_receipt(
                live_contract(),
                live_protocol(),
                evidence,
                support_reference="rollout-123",
            )

    def test_callable_rollout_receipt_rejects_duplicate_receipt_surface(self) -> None:
        receipt = build_callable_rollout_receipt(
            live_contract(),
            live_protocol(),
            execution_evidence(),
            support_reference="rollout-123",
        )
        bad_receipt = copy.deepcopy(receipt)
        bad_receipt["invocations"][1]["surfaceKey"] = bad_receipt["invocations"][0]["surfaceKey"]  # type: ignore[index]

        with self.assertRaises(ReviewError):
            validate_callable_rollout_receipt(bad_receipt, support_reference="rollout-123")


if __name__ == "__main__":
    unittest.main()
