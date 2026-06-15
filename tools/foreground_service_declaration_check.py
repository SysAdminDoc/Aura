#!/usr/bin/env python3
"""Verify foreground-service declaration packet against AndroidManifest.xml.

Checks:
  - Every manifest foreground-service permission has a reviewed declaration row.
  - Every manifest <service> with a foregroundServiceType has a declaration row.
  - Notification channels referenced in declarations exist in NotificationChannels.kt.
  - No BOOT_COMPLETED media-playback launch path exists.
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET


def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    packet_path = os.path.join(repo_root, "docs", "distribution", "foreground-service-declaration.json")
    manifest_path = os.path.join(repo_root, "app", "src", "main", "AndroidManifest.xml")
    channels_path = os.path.join(repo_root, "app", "src", "main", "java", "com", "freevibe", "service", "NotificationChannels.kt")

    errors = []

    if not os.path.isfile(packet_path):
        errors.append(f"Missing declaration packet: {packet_path}")
        _report(errors)
        return

    with open(packet_path, "r", encoding="utf-8") as f:
        packet = json.load(f)

    if packet.get("status") != "checked":
        errors.append("Declaration packet status is not 'checked'")

    # --- Parse manifest for FGS permissions and service types ---
    manifest_fgs_permissions = set()
    manifest_fgs_services = {}

    if os.path.isfile(manifest_path):
        with open(manifest_path, "r", encoding="utf-8") as f:
            manifest_text = f.read()

        fgs_perm_pattern = re.compile(r'android\.permission\.FOREGROUND_SERVICE(?:_[A-Z_]+)?')
        for m in fgs_perm_pattern.finditer(manifest_text):
            manifest_fgs_permissions.add(m.group(0).replace("android.permission.", ""))

        fgs_type_pattern = re.compile(r'android:foregroundServiceType="([^"]+)"')
        service_name_pattern = re.compile(r'<service[^>]*android:name="([^"]+)"[^>]*>')
        for svc_match in service_name_pattern.finditer(manifest_text):
            svc_name = svc_match.group(1)
            svc_block_start = svc_match.start()
            svc_block_end = manifest_text.find("</service>", svc_block_start)
            if svc_block_end < 0:
                svc_block_end = manifest_text.find("/>", svc_block_start)
            svc_block = manifest_text[svc_block_start:svc_block_end + 10 if svc_block_end > 0 else svc_block_start + 500]
            type_match = fgs_type_pattern.search(svc_block)
            if type_match:
                manifest_fgs_services[svc_name] = type_match.group(1)

        boot_fgs = re.search(r'BOOT_COMPLETED.*mediaPlayback|mediaPlayback.*BOOT_COMPLETED', manifest_text, re.DOTALL)
        if boot_fgs:
            errors.append("BOOT_COMPLETED receiver appears linked to mediaPlayback service — Play policy risk")
    else:
        errors.append(f"Missing AndroidManifest.xml: {manifest_path}")

    # --- Parse notification channels from source ---
    declared_channels = set()
    if os.path.isfile(channels_path):
        with open(channels_path, "r", encoding="utf-8") as f:
            channels_text = f.read()
        channel_pattern = re.compile(r'const val \w+ = "([^"]+)"')
        for m in channel_pattern.finditer(channels_text):
            declared_channels.add(m.group(1))
    else:
        errors.append(f"Missing NotificationChannels.kt: {channels_path}")

    # --- Validate packet services ---
    packet_services = {s["class"]: s for s in packet.get("services", [])}
    packet_permissions = set(packet.get("permissions", {}).keys())

    for perm in manifest_fgs_permissions:
        if perm not in packet_permissions:
            errors.append(f"Manifest permission {perm} has no declaration packet row")

    for svc_name, svc_type in manifest_fgs_services.items():
        full_name = svc_name if not svc_name.startswith(".") else f"com.freevibe{svc_name}"
        if full_name not in packet_services:
            errors.append(f"Manifest service {full_name} (type={svc_type}) has no declaration packet row")
        else:
            svc_row = packet_services[full_name]
            if svc_row.get("foregroundServiceType") != svc_type:
                errors.append(f"Service {full_name} type mismatch: manifest={svc_type}, packet={svc_row.get('foregroundServiceType')}")

    for svc in packet.get("services", []):
        channel = svc.get("notificationChannel", "")
        if channel and channel not in declared_channels:
            errors.append(f"Service {svc['class']} references channel '{channel}' not found in NotificationChannels.kt")
        if not svc.get("trigger"):
            errors.append(f"Service {svc['class']} is missing trigger description")
        if not svc.get("whyNotDeferred"):
            errors.append(f"Service {svc['class']} is missing whyNotDeferred justification")
        if not svc.get("demoVideoSteps"):
            errors.append(f"Service {svc['class']} is missing demoVideoSteps")

    for ch in packet.get("notificationChannels", []):
        if ch["id"] not in declared_channels:
            errors.append(f"Packet channel '{ch['id']}' not found in NotificationChannels.kt")

    _report(errors)


def _report(errors):
    if errors:
        print(f"FAIL: {len(errors)} issue(s)")
        for e in errors:
            print(f"  - {e}")
        sys.exit(1)
    else:
        print("OK: foreground-service declaration packet is consistent")
        sys.exit(0)


if __name__ == "__main__":
    main()
