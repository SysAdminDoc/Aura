from __future__ import annotations

import copy
import json
import unittest
from pathlib import Path

from tools.community_callable_wire_protocol_check import (
    WireProtocolError,
    validate_android_client,
    validate_protocol_metadata,
)


REPO_ROOT = Path(__file__).resolve().parents[2]


def read_json(relative_path: str) -> dict[str, object]:
    return json.loads((REPO_ROOT / relative_path).read_text(encoding="utf-8"))


def live_contract() -> dict[str, object]:
    return read_json("docs/community-callable-contract.json")


def live_protocol() -> dict[str, object]:
    return read_json("docs/community-callable-wire-protocol.json")


class CommunityCallableWireProtocolCheckTest(unittest.TestCase):
    def test_live_wire_protocol_matches_contract_and_android_client(self) -> None:
        protocol = live_protocol()
        surfaces = validate_protocol_metadata(live_contract(), protocol)

        result = validate_android_client(REPO_ROOT, protocol, surfaces)

        self.assertEqual(7, result["surfaceCount"])
        self.assertIn("updateCreatorProfile", result["functionNames"])

    def test_protocol_rejects_missing_contract_surface(self) -> None:
        protocol = copy.deepcopy(live_protocol())
        protocol["surfaces"] = protocol["surfaces"][:-1]  # type: ignore[index]

        with self.assertRaises(WireProtocolError):
            validate_protocol_metadata(live_contract(), protocol)

    def test_protocol_rejects_limited_use_token_drift(self) -> None:
        protocol = copy.deepcopy(live_protocol())
        protocol["surfaces"][0]["consumeLimitedUseAppCheckToken"] = False  # type: ignore[index]

        with self.assertRaises(WireProtocolError):
            validate_protocol_metadata(live_contract(), protocol)

    def test_android_client_check_rejects_missing_method(self) -> None:
        protocol = copy.deepcopy(live_protocol())
        protocol["surfaces"][0]["androidMethod"] = "missingReportMethod"  # type: ignore[index]
        surfaces = validate_protocol_metadata(live_contract(), protocol)

        with self.assertRaises(WireProtocolError):
            validate_android_client(REPO_ROOT, protocol, surfaces)

    def test_android_client_check_rejects_android_input_type_drift(self) -> None:
        protocol = copy.deepcopy(live_protocol())
        protocol["surfaces"][0]["androidInputType"] = "MissingReportInput"  # type: ignore[index]
        surfaces = validate_protocol_metadata(live_contract(), protocol)

        with self.assertRaises(WireProtocolError):
            validate_android_client(REPO_ROOT, protocol, surfaces)

    def test_android_client_check_rejects_missing_focused_test(self) -> None:
        protocol = copy.deepcopy(live_protocol())
        protocol["surfaces"][0]["focusedTest"] = "missing focused report test"  # type: ignore[index]
        surfaces = validate_protocol_metadata(live_contract(), protocol)

        with self.assertRaises(WireProtocolError):
            validate_android_client(REPO_ROOT, protocol, surfaces)


if __name__ == "__main__":
    unittest.main()
