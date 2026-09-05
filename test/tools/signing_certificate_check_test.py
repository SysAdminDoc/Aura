from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.signing_certificate_check import (
    POLICY_PATH,
    SigningCertificateError,
    keystore_certificate_sha256,
    validate_signing_certificate,
)


REPO_ROOT = Path(__file__).resolve().parents[2]


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / POLICY_PATH).read_text(encoding="utf-8"))


class SigningCertificateCheckTest(unittest.TestCase):
    def _fixture(self, digest: str, documents: dict[str, str]) -> tuple[Path, dict]:
        tmpdir = tempfile.TemporaryDirectory()
        self.addCleanup(tmpdir.cleanup)
        root = Path(tmpdir.name)
        policy = copy.deepcopy(live_policy())
        policy["certificateSha256"] = digest
        policy["publishedOn"] = sorted(documents)
        for relative, body in documents.items():
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(body, encoding="utf-8")
        return root, policy

    def test_live_policy_is_published_everywhere_it_claims(self) -> None:
        result = validate_signing_certificate(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual(64, len(str(result["certificateSha256"])))
        # Three surfaces, because release notes are not readable by a gate or a
        # store and that is where the digest used to live alone.
        self.assertEqual(
            [
                "README.md",
                "fastlane/metadata/android/en-US/full_description.txt",
                "docs/distribution/release-signing.md",
            ],
            list(live_policy()["publishedOn"]),
        )

    def test_the_live_digest_matches_the_release_keystore(self) -> None:
        actual = keystore_certificate_sha256(REPO_ROOT, live_policy())
        if actual is None:
            self.skipTest("no readable release keystore here; the keystore half is tri-state")
        self.assertEqual(
            str(live_policy()["certificateSha256"]).replace(":", "").upper(), actual
        )

    def test_rejects_a_document_that_lost_the_digest(self) -> None:
        digest = "A" * 64
        root, policy = self._fixture(
            digest,
            {
                "README.md": f"install this: {digest}\n",
                "docs/notes.md": "no digest here\n",
            },
        )

        with self.assertRaises(SigningCertificateError) as ctx:
            validate_signing_certificate(root, policy)

        message = str(ctx.exception)
        self.assertIn("docs/notes.md", message)
        self.assertNotIn("README.md", message)

    def test_accepts_a_digest_written_with_colons(self) -> None:
        digest = "F28E44BEA32F5B2890C8268B7FBED43C44A4D671A512FB07EBF18FDD41C66E5A"
        colonised = ":".join(digest[i : i + 2] for i in range(0, len(digest), 2))
        root, policy = self._fixture(
            digest, {"README.md": f"SHA-256: {colonised}\n"}
        )

        result = validate_signing_certificate(root, policy)

        self.assertEqual("ok", result["status"])

    def test_rejects_a_malformed_digest(self) -> None:
        root, policy = self._fixture("not-a-digest", {"README.md": "x\n"})

        with self.assertRaises(SigningCertificateError) as ctx:
            validate_signing_certificate(root, policy)

        self.assertIn("64 hex characters", str(ctx.exception))

    def test_rejects_a_publishedon_entry_that_does_not_exist(self) -> None:
        digest = "B" * 64
        root, policy = self._fixture(digest, {"README.md": f"{digest}\n"})
        policy["publishedOn"] = ["README.md", "docs/absent.md"]

        with self.assertRaises(SigningCertificateError) as ctx:
            validate_signing_certificate(root, policy)

        self.assertIn("docs/absent.md", str(ctx.exception))

    def test_rejects_an_empty_publishedon_list(self) -> None:
        digest = "C" * 64
        root, policy = self._fixture(digest, {"README.md": f"{digest}\n"})
        policy["publishedOn"] = []

        with self.assertRaises(SigningCertificateError) as ctx:
            validate_signing_certificate(root, policy)

        self.assertIn("non-empty list", str(ctx.exception))

    def test_a_missing_keystore_is_unknown_rather_than_a_failure(self) -> None:
        # A contributor without the release key must not be blocked.
        digest = "D" * 64
        root, policy = self._fixture(digest, {"README.md": f"{digest}\n"})

        result = validate_signing_certificate(root, policy)

        self.assertEqual("ok", result["status"])
        self.assertEqual("unknown", result["keystore"])

    def test_rejects_a_wrong_policy_kind(self) -> None:
        digest = "E" * 64
        root, policy = self._fixture(digest, {"README.md": f"{digest}\n"})
        policy["policyKind"] = "somethingElse"

        with self.assertRaises(SigningCertificateError) as ctx:
            validate_signing_certificate(root, policy)

        self.assertIn("policyKind", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
