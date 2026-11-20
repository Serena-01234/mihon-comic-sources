#!/usr/bin/env python3
"""从 Gradle 构建产物生成 Mihon 扩展仓库索引。

用法:
    python3 scripts/build_repo.py --upstream /path/to/extensions-source --out repo

读取每个模块 build/ 下的：
  * merged manifest  -> applicationId / versionCode / versionName
  * KSP Generated.kt -> source id / name / lang / baseUrl
  * release APK      -> 文件名
然后写出 index.min.json（legacy 格式）与 repo.json（指向 legacy 索引）。
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def parse_manifest(path: Path) -> dict:
    root = ET.parse(path).getroot()
    pkg = root.get("package", "")
    version_code = int(root.get(f"{ANDROID_NS}versionCode", "0"))
    version_name = root.get(f"{ANDROID_NS}versionName", "")
    nsfw = 0
    for meta in root.iter("meta-data"):
        if meta.get(f"{ANDROID_NS}name") == "tachiyomi.extension.nsfw" and meta.get(f"{ANDROID_NS}value") == "1":
            nsfw = 1
    return {"pkg": pkg, "code": version_code, "version": version_name, "nsfw": nsfw}


def parse_generated(path: Path) -> list[dict]:
    text = path.read_text(encoding="utf-8")
    sources = []
    # 单 source 模块: Generated.kt 直接 override val id/name/lang/baseUrl
    single = re.search(
        r'override val name:\s*String\s*get\(\)\s*=\s*"([^"]+)".*?'
        r'override val lang:\s*String\s*get\(\)\s*=\s*"([^"]+)".*?'
        r'override val id:\s*Long\s*get\(\)\s*=\s*([0-9_]+)L.*?'
        r'override val baseUrl:\s*String\s*get\(\)\s*=\s*"([^"]*)"',
        text,
        re.S,
    )
    if single:
        sources.append(
            {
                "name": single.group(1),
                "lang": single.group(2),
                "id": int(single.group(3).replace("_", "")),
                "baseUrl": single.group(4),
            }
        )
    return sources


def find_apk(module_dir: Path) -> Path | None:
    apks = sorted((module_dir / "build" / "outputs" / "apk" / "release").glob("*.apk"))
    return apks[0] if apks else None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--upstream", required=True, help="extensions-source checkout")
    ap.add_argument("--out", default="repo")
    ap.add_argument("--fingerprint", default=os.environ.get("SIGNING_FINGERPRINT", ""))
    ap.add_argument("--store-name", default="Serena Comic Sources")
    ap.add_argument("--store-short", default="SCS")
    ap.add_argument("--website", default="https://github.com/Serena-01234/mihon-comic-sources")
    args = ap.parse_args()

    root = Path(args.upstream)
    out = Path(args.out)
    (out / "apk").mkdir(parents=True, exist_ok=True)
    (out / "icon").mkdir(parents=True, exist_ok=True)

    entries = []
    for module in sorted((root / "src").glob("*/*")):
        apk = find_apk(module)
        if apk is None:
            continue
        manifests = list(module.glob("build/intermediates/merged_manifests/release/**/AndroidManifest.xml"))
        if not manifests:
            manifests = list(module.glob("build/intermediates/merged_manifest/release/**/AndroidManifest.xml"))
        if not manifests:
            print(f"skip {module.name}: no merged manifest", file=sys.stderr)
            continue
        meta = parse_manifest(manifests[0])

        generated = list(module.glob("build/generated/ksp/release/**/Generated.kt"))
        sources = parse_generated(generated[0]) if generated else []
        if not sources:
            print(f"skip {module.name}: no generated sources", file=sys.stderr)
            continue

        shutil.copy2(apk, out / "apk" / apk.name)
        icon = next(iter(sorted(module.glob("res/mipmap-xxhdpi/ic_launcher.png"))), None)
        if icon:
            shutil.copy2(icon, out / "icon" / f"{meta['pkg']}.png")

        entries.append(
            {
                "name": sources[0]["name"],
                "pkg": meta["pkg"],
                "apk": apk.name,
                "lang": sources[0]["lang"],
                "code": meta["code"],
                "version": meta["version"],
                "nsfw": meta["nsfw"],
                "sources": sources,
            }
        )

    if not entries:
        print("no extensions found", file=sys.stderr)
        return 1

    (out / "index.min.json").write_text(json.dumps(entries, ensure_ascii=False, indent=1), encoding="utf-8")
    (out / "repo.json").write_text(
        json.dumps(
            {
                "index_v2": None,
                "meta": {
                    "name": args.store_name,
                    "shortName": args.store_short,
                    "website": args.website,
                    "signingKeyFingerprint": args.fingerprint,
                },
            },
            ensure_ascii=False,
            indent=1,
        ),
        encoding="utf-8",
    )
    print(f"wrote {len(entries)} extension(s) to {out}")
    for e in entries:
        print(f"  {e['name']:20s} {e['pkg']}  v{e['version']} ({e['code']})  {len(e['sources'])} source(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
