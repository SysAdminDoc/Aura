import json
import tempfile
import unittest
from pathlib import Path

from tools import target37_compatibility_check


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_repo(repo_root: Path, manifest_extra: str = "", extra_source: str = "") -> None:
    write_text(
        repo_root / "app/src/main/AndroidManifest.xml",
        f"""
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
          <uses-permission android:name="android.permission.INTERNET" />
          <uses-permission android:name="android.permission.WRITE_CONTACTS" />
          {manifest_extra}
          <application android:usesCleartextTraffic="false" />
        </manifest>
        """,
    )
    write_text(repo_root / "app/src/main/res/xml/network_security_config.xml", "<network-security-config />")
    write_text(
        repo_root / "app/src/main/java/com/freevibe/service/PhotoPickerCustomization.kt",
        """
        fun bridge() {
          val cls = Class.forName("android.provider.MediaStore")
          cls.getDeclaredConstructor().newInstance()
          cls.getMethod("build")
          cls.getField("EXTRA_PHOTO_PICKER_UI_CUSTOMIZATION_PARAMS")
        }
        """,
    )
    write_text(
        repo_root / "app/src/main/java/com/freevibe/service/ContactRingtoneService.kt",
        "fun selected() = ContactsContract.Contacts.CONTENT_URI",
    )
    write_text(
        repo_root / "app/src/main/java/com/freevibe/ui/screens/sounds/ContactPickerScreen.kt",
        "fun pick() = ContactsContract.Contacts.CONTENT_URI",
    )
    write_text(repo_root / "app/src/main/java/com/freevibe/Other.kt", extra_source)


def policy() -> dict:
    return {
        "schemaVersion": 1,
        "policyKind": "target37PrivacySecurityCompatibilityPreflight",
        "androidVersion": "Android 17 / API 37",
        "scan": {
            "sourceRoots": ["app/src/main/java"],
            "manifestPath": "app/src/main/AndroidManifest.xml",
            "networkSecurityConfigPath": "app/src/main/res/xml/network_security_config.xml",
        },
        "localNetwork": {
            "decision": "No local network APIs.",
            "forbiddenPermissions": ["android.permission.ACCESS_LOCAL_NETWORK"],
            "forbiddenTerms": ["NsdManager", "WifiManager", "ServerSocket("],
            "stringLiteralHostPattern": "\\.local",
        },
        "contacts": {
            "decision": "Picker only.",
            "forbiddenManifestPermissions": ["android.permission.READ_CONTACTS"],
            "reviewedContactFiles": [
                "app/src/main/java/com/freevibe/service/ContactRingtoneService.kt",
                "app/src/main/java/com/freevibe/ui/screens/sounds/ContactPickerScreen.kt",
            ],
        },
        "reflection": {
            "decision": "Reviewed bridge.",
            "terms": ["Class.forName(", "getDeclaredConstructor(", "getMethod(", "getField("],
            "reviewedOccurrences": [
                {
                    "path": "app/src/main/java/com/freevibe/service/PhotoPickerCustomization.kt",
                    "terms": [
                        "Class.forName(",
                        "getDeclaredConstructor(",
                        "getMethod(",
                        "getField(",
                    ],
                    "reason": "API bridge",
                }
            ],
        },
        "networkSecurity": {
            "decision": "No cleartext.",
            "cleartextPermitted": False,
            "ccmixterCleartextException": "removed",
        },
        "dynamicCodeLoading": {
            "decision": "No DCL.",
            "forbiddenTerms": ["DexClassLoader(", "System.load("],
        },
        "tlsPlatformReview": {
            "certificateTransparency": "Platform default.",
            "encryptedClientHello": "Platform/provider default.",
        },
    }


class Target37CompatibilityCheckTest(unittest.TestCase):
    def test_accepts_reviewed_target37_surface(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_repo(repo_root)

            result = target37_compatibility_check.validate_policy(repo_root, policy())

            self.assertEqual("ok", result["status"])
            self.assertEqual(2, result["contactsContractFindings"])

    def test_rejects_local_network_permission_and_api(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_repo(
                repo_root,
                manifest_extra='<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />',
                extra_source='fun lan(manager: NsdManager) = "printer.local"',
            )

            with self.assertRaisesRegex(
                target37_compatibility_check.Target37CompatibilityError,
                "forbidden manifest permissions",
            ):
                target37_compatibility_check.validate_policy(repo_root, policy())

    def test_rejects_unreviewed_reflection(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_repo(repo_root, extra_source='fun reflect() = Class.forName("x.y.Z")')

            with self.assertRaisesRegex(
                target37_compatibility_check.Target37CompatibilityError,
                "unreviewed reflection usage",
            ):
                target37_compatibility_check.validate_policy(repo_root, policy())

    def test_rejects_unreviewed_contacts_contract_usage(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_repo(repo_root, extra_source="fun broad() = ContactsContract.Data.CONTENT_URI")

            with self.assertRaisesRegex(
                target37_compatibility_check.Target37CompatibilityError,
                "unreviewed ContactsContract usage",
            ):
                target37_compatibility_check.validate_policy(repo_root, policy())

    def test_rejects_cleartext_network_config(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_repo(repo_root)
            write_text(
                repo_root / "app/src/main/res/xml/network_security_config.xml",
                '<network-security-config><domain-config cleartextTrafficPermitted="true" /></network-security-config>',
            )

            with self.assertRaisesRegex(
                target37_compatibility_check.Target37CompatibilityError,
                "allows cleartext",
            ):
                target37_compatibility_check.validate_policy(repo_root, policy())


if __name__ == "__main__":
    unittest.main()
