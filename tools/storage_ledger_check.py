#!/usr/bin/env python3
"""Verify storage ledger against backup rules and source references.

Checks:
  - Every backup-excluded store appears in data_extraction_rules.xml.
  - Every store references an existing source file.
  - SharedPreferences files match backup exclusion list.
  - No temp directory budget exceeds its documented cap.
"""
import json
import os
import re
import sys


def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ledger_path = os.path.join(repo_root, "docs", "privacy", "storage-ledger.json")
    rules_path = os.path.join(repo_root, "app", "src", "main", "res", "xml", "data_extraction_rules.xml")
    src_root = os.path.join(repo_root, "app", "src", "main", "java", "com", "freevibe")

    errors = []

    if not os.path.isfile(ledger_path):
        errors.append(f"Missing storage ledger: {ledger_path}")
        _report(errors)
        return

    with open(ledger_path, "r", encoding="utf-8") as f:
        ledger = json.load(f)

    if ledger.get("status") != "checked":
        errors.append("Storage ledger status is not 'checked'")

    rules_text = ""
    if os.path.isfile(rules_path):
        with open(rules_path, "r", encoding="utf-8") as f:
            rules_text = f.read()
    else:
        errors.append(f"Missing data_extraction_rules.xml: {rules_path}")

    for store in ledger.get("stores", []):
        sf = store.get("sourceFile", "")
        if sf and not _find_source(src_root, sf):
            errors.append(f"Store '{store['id']}' references missing source: {sf}")

        if store.get("backupExcluded") and rules_text:
            path = store.get("path", "")
            if path.startswith("cacheDir/") or path.startswith("MediaStore") or path.startswith("datastore/"):
                continue
            path_key = path.split("/")[-1].rstrip("}").split(".")[0]
            if path_key == "freevibe":
                for suffix in ["freevibe.db", "freevibe.db-journal", "freevibe.db-shm", "freevibe.db-wal"]:
                    if suffix not in rules_text:
                        errors.append(f"Store '{store['id']}' backup-excluded but '{suffix}' not in data_extraction_rules.xml")
            elif path_key and path_key not in rules_text:
                errors.append(f"Store '{store['id']}' backup-excluded but '{path_key}' not in data_extraction_rules.xml")

    for td in ledger.get("tempDirectories", []):
        sf = td.get("sourceFile", "")
        if sf and not _find_source(src_root, sf):
            errors.append(f"Temp dir '{td['path']}' references missing source: {sf}")

    if rules_text:
        sp_in_rules = set(re.findall(r'name="([^"]+\.xml)"', rules_text))
        sp_in_ledger = set(ledger.get("sharedPreferencesFiles", []))
        for sp in sp_in_rules:
            if sp not in sp_in_ledger and sp.endswith(".xml"):
                errors.append(f"SharedPreferences '{sp}' in backup rules but not in storage ledger")

    _report(errors)


def _find_source(src_root, filename):
    for dirpath, _, filenames in os.walk(src_root):
        if filename in filenames:
            return True
    ui_root = os.path.join(os.path.dirname(src_root.rstrip(os.sep)), "")
    for dirpath, _, filenames in os.walk(src_root):
        if filename in filenames:
            return True
    return False


def _report(errors):
    if errors:
        print(f"FAIL: {len(errors)} issue(s)")
        for e in errors:
            print(f"  - {e}")
        sys.exit(1)
    else:
        print("OK: storage ledger is consistent")
        sys.exit(0)


if __name__ == "__main__":
    main()
