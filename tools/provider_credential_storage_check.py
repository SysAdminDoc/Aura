#!/usr/bin/env python3
"""Validate Aura's provider credential storage policy."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


REQUIRED_CREDENTIAL_FIELDS = {
    "id",
    "provider",
    "classification",
    "storage",
    "preferenceKey",
    "buildConfigField",
    "gradleProperty",
    "settingsLabel",
    "releaseDefault",
    "userControl",
    "redactionTerms",
}
SUPPORTED_CLASSIFICATIONS = {"optionalQuotaKey", "publicClientId", "paidSensitiveSecret"}
SUPPORTED_STORAGE = {"dataStore", "buildConfigOnly"}


class ProviderCredentialStorageError(ValueError):
    """Raised when the provider credential storage policy is stale."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura provider credential storage policy.")
    parser.add_argument("--policy", default="docs/security/provider-credential-storage.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ProviderCredentialStorageError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ProviderCredentialStorageError(f"{label} must be a non-empty string")
    return value.strip()


def require_nullable_string(value: Any, label: str) -> str | None:
    if value is None:
        return None
    return require_string(value, label)


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise ProviderCredentialStorageError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise ProviderCredentialStorageError(f"{label} contains duplicate values")
    return values


def read_text(repo_root: Path, relative_path: str) -> str:
    path = repo_root / relative_path
    if not path.is_file():
        raise ProviderCredentialStorageError(f"required file is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def validate_backup_exclusion(xml_text: str, data_store_path: str, label: str) -> None:
    compact = re.sub(r"\s+", " ", xml_text)
    required = f'domain="file" path="{data_store_path}"'
    if required not in compact:
        raise ProviderCredentialStorageError(f"{label} must exclude {data_store_path}")


def validate_gradle_default(app_gradle_text: str, credential: dict[str, Any]) -> None:
    build_config = credential["buildConfigField"]
    gradle_property = credential["gradleProperty"]
    if not build_config:
        return
    if f'buildConfigField("String", "{build_config}"' not in app_gradle_text:
        raise ProviderCredentialStorageError(f"app Gradle is missing BuildConfig field {build_config}")
    expected = f'localProps.getProperty("{gradle_property}", "")'
    if expected not in app_gradle_text:
        raise ProviderCredentialStorageError(f"app Gradle must default {gradle_property} to blank")


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, Any]:
    if policy.get("schemaVersion") != 1:
        raise ProviderCredentialStorageError("provider credential storage schemaVersion must be 1")
    if policy.get("policyKind") != "providerCredentialStorage":
        raise ProviderCredentialStorageError("provider credential storage policyKind is invalid")

    docs_path = require_string(policy.get("docsPath"), "docsPath")
    preferences_manager_path = require_string(policy.get("preferencesManager"), "preferencesManager")
    settings_screen_path = require_string(policy.get("settingsScreen"), "settingsScreen")
    app_gradle_path = require_string(policy.get("appGradle"), "appGradle")
    backup_rules_path = require_string(policy.get("backupRules"), "backupRules")
    data_extraction_rules_path = require_string(policy.get("dataExtractionRules"), "dataExtractionRules")
    diagnostics_doc_path = require_string(policy.get("diagnosticsDoc"), "diagnosticsDoc")
    privacy_policy_path = require_string(policy.get("privacyPolicy"), "privacyPolicy")
    data_store = require_object(policy.get("dataStore"), "dataStore")
    data_store_file = require_string(data_store.get("filePath"), "dataStore.filePath")
    at_rest = require_string(data_store.get("atRestProtection"), "dataStore.atRestProtection")
    keystore_decision = require_string(data_store.get("keystoreDecision"), "dataStore.keystoreDecision")
    if at_rest != "appPrivateDataStoreNoKeystore":
        raise ProviderCredentialStorageError("dataStore.atRestProtection must document the no-Keystore posture")
    if "noKeystore" not in keystore_decision:
        raise ProviderCredentialStorageError("dataStore.keystoreDecision must be explicit")

    docs_text = read_text(repo_root, docs_path)
    preferences_manager_text = read_text(repo_root, preferences_manager_path)
    settings_screen_text = read_text(repo_root, settings_screen_path)
    app_gradle_text = read_text(repo_root, app_gradle_path)
    backup_rules_text = read_text(repo_root, backup_rules_path)
    data_extraction_rules_text = read_text(repo_root, data_extraction_rules_path)
    diagnostics_doc_text = read_text(repo_root, diagnostics_doc_path).lower()
    privacy_policy_text = read_text(repo_root, privacy_policy_path).lower()

    validate_backup_exclusion(backup_rules_text, data_store_file, backup_rules_path)
    validate_backup_exclusion(data_extraction_rules_text, data_store_file, data_extraction_rules_path)
    if "<cloud-backup>" not in data_extraction_rules_text or "<device-transfer>" not in data_extraction_rules_text:
        raise ProviderCredentialStorageError(f"{data_extraction_rules_path} must cover cloud backup and device transfer")
    if "api keys entered by the user" not in privacy_policy_text:
        raise ProviderCredentialStorageError("privacy policy must disclose user-entered API key storage")

    credentials_raw = policy.get("credentials")
    if not isinstance(credentials_raw, list) or not credentials_raw:
        raise ProviderCredentialStorageError("credentials must be a non-empty list")

    seen_ids: set[str] = set()
    data_store_count = 0
    build_config_count = 0
    paid_sensitive_count = 0
    for index, raw_credential in enumerate(credentials_raw):
        credential = require_object(raw_credential, f"credentials[{index}]")
        missing = sorted(REQUIRED_CREDENTIAL_FIELDS - set(credential))
        if missing:
            raise ProviderCredentialStorageError(f"credentials[{index}] missing fields: {', '.join(missing)}")
        credential_id = require_string(credential["id"], f"credentials[{index}].id")
        if credential_id in seen_ids:
            raise ProviderCredentialStorageError(f"duplicate credential id: {credential_id}")
        seen_ids.add(credential_id)
        provider = require_string(credential["provider"], f"{credential_id}.provider")
        classification = require_string(credential["classification"], f"{credential_id}.classification")
        if classification not in SUPPORTED_CLASSIFICATIONS:
            raise ProviderCredentialStorageError(f"{credential_id}.classification is unsupported")
        storage = require_string(credential["storage"], f"{credential_id}.storage")
        if storage not in SUPPORTED_STORAGE:
            raise ProviderCredentialStorageError(f"{credential_id}.storage is unsupported")
        preference_key = require_nullable_string(credential["preferenceKey"], f"{credential_id}.preferenceKey")
        credential["buildConfigField"] = require_nullable_string(credential["buildConfigField"], f"{credential_id}.buildConfigField")
        credential["gradleProperty"] = require_nullable_string(credential["gradleProperty"], f"{credential_id}.gradleProperty")
        settings_label = require_nullable_string(credential["settingsLabel"], f"{credential_id}.settingsLabel")
        release_default = require_string(credential["releaseDefault"], f"{credential_id}.releaseDefault")
        require_string(credential["userControl"], f"{credential_id}.userControl")
        redaction_terms = require_string_list(credential["redactionTerms"], f"{credential_id}.redactionTerms")

        for term in (credential_id, provider, classification):
            if term not in docs_text:
                raise ProviderCredentialStorageError(f"{docs_path} is missing {term}")

        if storage == "dataStore":
            data_store_count += 1
            if not preference_key:
                raise ProviderCredentialStorageError(f"{credential_id} dataStore rows require preferenceKey")
            if f'stringPreferencesKey("{preference_key}")' not in preferences_manager_text:
                raise ProviderCredentialStorageError(f"PreferencesManager missing {preference_key}")
            if not settings_label or settings_label not in settings_screen_text:
                raise ProviderCredentialStorageError(f"Settings screen missing label for {credential_id}")
        elif preference_key:
            raise ProviderCredentialStorageError(f"{credential_id} buildConfigOnly rows must not set preferenceKey")

        if credential["buildConfigField"]:
            build_config_count += 1
            if not credential["gradleProperty"]:
                raise ProviderCredentialStorageError(f"{credential_id} BuildConfig rows require gradleProperty")
            if release_default != "blank":
                raise ProviderCredentialStorageError(f"{credential_id} releaseDefault must be blank")
            validate_gradle_default(app_gradle_text, credential)

        if classification == "paidSensitiveSecret":
            paid_sensitive_count += 1
            if "not strong at-rest protection" not in docs_text:
                raise ProviderCredentialStorageError(f"{docs_path} must disclose no strong at-rest protection")

        for term in redaction_terms:
            if term.lower() not in diagnostics_doc_text:
                raise ProviderCredentialStorageError(f"{diagnostics_doc_path} is missing redaction term {term}")

    return {
        "policyKind": policy["policyKind"],
        "schemaVersion": policy["schemaVersion"],
        "credentialCount": len(credentials_raw),
        "dataStoreCredentialCount": data_store_count,
        "buildConfigCredentialCount": build_config_count,
        "paidSensitiveCredentialCount": paid_sensitive_count,
        "dataStoreFile": data_store_file,
        "status": "ok",
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root)
    try:
        policy = require_object(read_json(repo_root / args.policy), "provider credential storage policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
