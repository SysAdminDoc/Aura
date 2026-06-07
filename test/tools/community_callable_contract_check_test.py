from __future__ import annotations

import copy
import unittest

from tools.community_callable_contract_check import (
    CallableContractError,
    EXPECTED_SURFACES,
    expected_dedupe_path,
    expected_quota_path,
    validate_contract,
)


def valid_contract() -> dict[str, object]:
    surfaces = []
    for surface_key, expected in EXPECTED_SURFACES.items():
        surfaces.append(
            {
                "surfaceKey": surface_key,
                "dailyLimit": expected["dailyLimit"],
                "minIntervalMillis": expected["minIntervalMillis"],
                "dedupeKey": expected["dedupeKey"],
                "enforcement": list(expected["enforcement"]),
                "quotaLedgerPath": expected_quota_path(surface_key),
                "dedupeLedgerPath": expected_dedupe_path(surface_key),
                "callable": {
                    "functionName": expected["functionName"],
                    "payloadSchema": expected["payloadSchema"],
                    "finalWritePaths": list(expected["finalWritePaths"]),
                    "consumeLimitedUseAppCheckToken": expected["consumeLimitedUseAppCheckToken"],
                    "requiresAuth": True,
                    "requiresAppCheck": True,
                },
            }
        )
    return {
        "schemaVersion": 1,
        "contractKind": "communityCallableQuotaContract",
        "quotaDayBoundary": "UTC",
        "surfaces": surfaces,
    }


class CommunityCallableContractCheckTest(unittest.TestCase):
    def test_valid_contract_returns_function_summary(self) -> None:
        result = validate_contract(valid_contract())

        self.assertEqual(7, result["surfaceCount"])
        self.assertIn("submitCommunityReport", result["functionNames"])
        self.assertEqual("UTC", result["quotaDayBoundary"])

    def test_contract_requires_all_surfaces(self) -> None:
        contract = valid_contract()
        contract["surfaces"] = contract["surfaces"][:-1]  # type: ignore[index]

        with self.assertRaises(CallableContractError):
            validate_contract(contract)

    def test_contract_rejects_duplicate_function_names(self) -> None:
        contract = valid_contract()
        surfaces = contract["surfaces"]  # type: ignore[assignment]
        surfaces[1]["callable"]["functionName"] = surfaces[0]["callable"]["functionName"]  # type: ignore[index]

        with self.assertRaises(CallableContractError):
            validate_contract(contract)

    def test_contract_rejects_limited_use_app_check_drift(self) -> None:
        contract = valid_contract()
        surfaces = contract["surfaces"]  # type: ignore[assignment]
        surfaces[0]["callable"]["consumeLimitedUseAppCheckToken"] = False  # type: ignore[index]

        with self.assertRaises(CallableContractError):
            validate_contract(contract)

    def test_contract_rejects_ledger_namespace_drift(self) -> None:
        contract = copy.deepcopy(valid_contract())
        surfaces = contract["surfaces"]  # type: ignore[assignment]
        surfaces[0]["quotaLedgerPath"] = "/public_quota/reports"  # type: ignore[index]

        with self.assertRaises(CallableContractError):
            validate_contract(contract)


if __name__ == "__main__":
    unittest.main()
