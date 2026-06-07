from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.on_device_ai_decision_check import OnDeviceAiDecisionError, validate_policy


REPO_ROOT = Path(__file__).resolve().parents[2]


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/ai/on-device-wallpaper-decision.json").read_text(encoding="utf-8"))


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def minimal_policy() -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "policyKind": "onDeviceAiWallpaperDecision",
        "decision": "hold",
        "summary": "Do not implement on-device wallpaper generation yet.",
        "hostedFallback": "Stability BYO-key generation remains supported.",
        "decisionDoc": "docs/ai/on-device-wallpaper-decision.md",
        "criteria": [
            {"id": "device-baseline", "status": "needsEvidence", "requiredEvidence": ["device matrix"]},
            {"id": "model-size-storage", "status": "needsEvidence", "requiredEvidence": ["artifact size"]},
            {"id": "latency-battery-thermal", "status": "needsEvidence", "requiredEvidence": ["profile evidence"]},
            {"id": "license-redistribution", "status": "needsEvidence", "requiredEvidence": ["license review"]},
            {"id": "moderation-reporting", "status": "needsEvidence", "requiredEvidence": ["safety plan"]},
            {"id": "fallback-user-choice", "status": "needsEvidence", "requiredEvidence": ["mode choice"]},
            {"id": "foss-channel-impact", "status": "needsEvidence", "requiredEvidence": ["FOSS review"]},
        ],
        "forbiddenImplementationSignals": ["com.google.ai.edge.litert", "onnxruntime"],
        "sourceUrls": ["https://example.com/source"],
    }


def seed_repo(repo: Path) -> Path:
    write(repo / "docs/ai/on-device-wallpaper-decision.md", "# Decision\n\nStatus: hold.\n")
    write(repo / "app/src/main/AndroidManifest.xml", "<manifest />\n")
    write(repo / "app/build.gradle.kts", "plugins { id(\"com.android.application\") }\n")
    write(repo / "gradle/libs.versions.toml", "[versions]\n")
    write(repo / "settings.gradle.kts", "pluginManagement {}\n")
    return repo


class OnDeviceAiDecisionCheckTest(unittest.TestCase):
    def test_live_on_device_ai_decision_policy_passes(self) -> None:
        result = validate_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual("hold", result["decision"])
        self.assertEqual(7, result["criteriaCount"])
        self.assertEqual("ok", result["scan"]["status"])

    def test_rejects_missing_required_criterion(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            policy["criteria"] = policy["criteria"][:-1]  # type: ignore[index]

            with self.assertRaises(OnDeviceAiDecisionError):
                validate_policy(repo, policy)

    def test_rejects_non_https_source_url(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            policy["sourceUrls"] = ["http://example.com/source"]

            with self.assertRaises(OnDeviceAiDecisionError):
                validate_policy(repo, policy)

    def test_rejects_approved_decision_with_unmet_criteria(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            write(repo / "docs/ai/on-device-wallpaper-decision.md", "# Decision\n\nStatus: approved.\n")
            policy = minimal_policy()
            policy["decision"] = "approved"

            with self.assertRaises(OnDeviceAiDecisionError):
                validate_policy(repo, policy)

    def test_approved_decision_requires_evidence_refs(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            write(repo / "docs/ai/on-device-wallpaper-decision.md", "# Decision\n\nStatus: approved.\n")
            policy = minimal_policy()
            policy["decision"] = "approved"
            for criterion in policy["criteria"]:  # type: ignore[union-attr]
                criterion["status"] = "met"
                criterion.pop("requiredEvidence", None)

            with self.assertRaises(OnDeviceAiDecisionError):
                validate_policy(repo, policy)

    def test_rejects_forbidden_production_dependency_before_approval(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            write(
                repo / "app/build.gradle.kts",
                'dependencies { implementation("com.google.ai.edge.litert:litert:2.1.5") }\n',
            )

            with self.assertRaises(OnDeviceAiDecisionError):
                validate_policy(repo, policy)

    def test_rejects_model_artifact_before_approval(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            artifact = repo / "app/src/main/assets/model.tflite"
            artifact.parent.mkdir(parents=True, exist_ok=True)
            artifact.write_bytes(b"fake model")

            with self.assertRaises(OnDeviceAiDecisionError):
                validate_policy(repo, policy)

    def test_rejects_hosted_fallback_drift(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = copy.deepcopy(minimal_policy())
            policy["hostedFallback"] = "Local generation is always used."

            with self.assertRaises(OnDeviceAiDecisionError):
                validate_policy(repo, policy)


if __name__ == "__main__":
    unittest.main()
