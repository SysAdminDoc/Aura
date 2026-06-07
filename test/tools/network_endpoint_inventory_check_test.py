from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.network_endpoint_inventory_check import NetworkInventoryError, validate_inventory


REPO_ROOT = Path(__file__).resolve().parents[2]


def live_inventory() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/security/network-endpoints.json").read_text(encoding="utf-8"))


class NetworkEndpointInventoryCheckTest(unittest.TestCase):
    def test_live_inventory_matches_network_source_literals(self) -> None:
        result = validate_inventory(REPO_ROOT, live_inventory())

        self.assertEqual("networkEndpointInventory", result["policyKind"])
        self.assertEqual(1, result["schemaVersion"])
        self.assertEqual(15, result["endpointCount"])
        self.assertIn("wallhaven.cc", result["scannedLiteralHosts"])
        self.assertIn("api-v2.soundcloud.com", result["scannedLiteralHosts"])

    def test_rejects_unreviewed_literal_host(self) -> None:
        inventory = copy.deepcopy(live_inventory())
        inventory["scan"] = {"sourceRoots": ["src"]}  # type: ignore[index]

        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            (repo_root / "src").mkdir()
            (repo_root / "src" / "Example.kt").write_text(
                'const val BASE_URL = "https://new-provider.example/api/"\n',
                encoding="utf-8",
            )
            docs_path = repo_root / "docs/security/network-endpoints.md"
            docs_path.parent.mkdir(parents=True)
            docs_path.write_text(
                "\n".join(
                    [endpoint["id"] for endpoint in inventory["endpoints"]]  # type: ignore[index]
                    + [
                        host
                        for endpoint in inventory["endpoints"]  # type: ignore[index]
                        for host in endpoint["hosts"]
                    ]
                ),
                encoding="utf-8",
            )

            with self.assertRaises(NetworkInventoryError):
                validate_inventory(repo_root, inventory)

    def test_rejects_missing_endpoint_field(self) -> None:
        inventory = copy.deepcopy(live_inventory())
        del inventory["endpoints"][0]["authLocation"]  # type: ignore[index]

        with self.assertRaises(NetworkInventoryError):
            validate_inventory(REPO_ROOT, inventory)

    def test_rejects_docs_missing_reviewed_host(self) -> None:
        inventory = copy.deepcopy(live_inventory())
        inventory["docsPath"] = "docs/security/network-endpoints-missing.md"

        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            docs_path = repo_root / "docs/security/network-endpoints-missing.md"
            docs_path.parent.mkdir(parents=True)
            docs_path.write_text("wallhaven-api\n", encoding="utf-8")
            for root in inventory["scan"]["sourceRoots"]:  # type: ignore[index]
                (repo_root / root).mkdir(parents=True, exist_ok=True)

            with self.assertRaises(NetworkInventoryError):
                validate_inventory(repo_root, inventory)


if __name__ == "__main__":
    unittest.main()
