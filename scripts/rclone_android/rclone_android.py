#!/usr/bin/env python3
"""Checks for the trimmed rclone executable shipped as lib/<abi>/librclone.so (registry rows 886-887).

Used by scripts/build-rclone-android.sh and by the CI workflows; host-side only, standard library only.

  gradle <build.gradle.kts> <key>          print a quoted or numeric value (ndkVersion, minSdk)
  origin <version> <commit>                stdin = `go mod download -json` of rclone; the proxy's tag -> commit
  binary <abi> <file>                      the file is an Android PIE executable for that ABI, 16 KB aligned
  apk <apk> <abi>...                       the APK carries lib/<abi>/librclone.so for each ABI, and each passes `binary`
  notices <gomodcache> <goroot>            stdin = `go version -m librclone.so`; print the licence notices of
                                           everything linked into it (see NOTICES_PATH)
  notices-check <notices file>             stdin = `go version -m librclone.so`; every linked module and the Go
                                           release are in the notices file at exactly that version

Each check exits 1 with one line per problem.
"""

from __future__ import annotations

import json
import os
import re
import struct
import sys
import zipfile

LIB_NAME = "librclone.so"
# Shipped inside the APK next to the executable: MIT, BSD and Apache-2.0 all ask for their notices to travel with
# every copy (registry row 887). Regenerated with `notices`; the build fails while it is out of date.
NOTICES_PATH = "app/src/main/assets/licenses/rclone-android-NOTICES.txt"
MIN_ALIGN = 16384  # Android 15+ devices with 16 KB pages refuse LOAD segments aligned to less.

ET_DYN = 3
PT_LOAD = 1
PT_INTERP = 3

# abi -> (ELF class: 1 = 32-bit, 2 = 64-bit; e_machine; Android's dynamic linker)
ABIS = {
    "arm64-v8a": (2, 183, "/system/bin/linker64"),  # EM_AARCH64
    "armeabi-v7a": (1, 40, "/system/bin/linker"),  # EM_ARM
    "x86_64": (2, 62, "/system/bin/linker64"),  # EM_X86_64
    "x86": (1, 3, "/system/bin/linker"),  # EM_386
}


def parse_elf(data: bytes) -> dict:
    """Header fields and program headers of a little-endian ELF file. Raises ValueError if it is not one."""
    if len(data) < 52 or data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    ei_class, ei_data = data[4], data[5]
    if ei_class not in (1, 2):
        raise ValueError(f"unknown ELF class {ei_class}")
    if ei_data != 1:
        raise ValueError("not little-endian")
    if ei_class == 2:
        if len(data) < 64:
            raise ValueError("truncated ELF header")
        e_type, e_machine = struct.unpack_from("<HH", data, 16)
        (e_phoff,) = struct.unpack_from("<Q", data, 32)
        e_phentsize, e_phnum = struct.unpack_from("<HH", data, 54)
    else:
        e_type, e_machine = struct.unpack_from("<HH", data, 16)
        (e_phoff,) = struct.unpack_from("<I", data, 28)
        e_phentsize, e_phnum = struct.unpack_from("<HH", data, 42)
    phdrs = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        if off + e_phentsize > len(data):
            raise ValueError("truncated program header table")
        if ei_class == 2:
            p_type, _flags, p_offset, _vaddr, _paddr, p_filesz, _memsz, p_align = struct.unpack_from(
                "<IIQQQQQQ", data, off
            )
        else:
            p_type, p_offset, _vaddr, _paddr, p_filesz, _memsz, _flags, p_align = struct.unpack_from(
                "<IIIIIIII", data, off
            )
        phdrs.append({"type": p_type, "offset": p_offset, "filesz": p_filesz, "align": p_align})
    interp = None
    for ph in phdrs:
        if ph["type"] == PT_INTERP:
            raw = data[ph["offset"] : ph["offset"] + ph["filesz"]]
            interp = raw.split(b"\0", 1)[0].decode("ascii", "replace")
    return {"class": ei_class, "type": e_type, "machine": e_machine, "phdrs": phdrs, "interp": interp}


def check_binary(data: bytes, abi: str) -> list[str]:
    """Problems that would stop Android from exec'ing this file as rclone on that ABI ([] = fine)."""
    if abi not in ABIS:
        return [f"unknown ABI {abi}"]
    want_class, want_machine, want_interp = ABIS[abi]
    try:
        elf = parse_elf(data)
    except (ValueError, struct.error) as e:
        return [f"{abi}: {e}"]
    problems = []
    if elf["class"] != want_class or elf["machine"] != want_machine:
        problems.append(f"{abi}: built for another architecture (class {elf['class']}, machine {elf['machine']})")
    if elf["type"] != ET_DYN:
        problems.append(f"{abi}: not a position-independent executable (e_type {elf['type']})")
    if elf["interp"] != want_interp:
        problems.append(f"{abi}: interpreter is {elf['interp']!r}, expected {want_interp} (not linked for Android)")
    loads = [ph for ph in elf["phdrs"] if ph["type"] == PT_LOAD]
    if not loads:
        problems.append(f"{abi}: no LOAD segments")
    for ph in loads:
        if ph["align"] < MIN_ALIGN:
            problems.append(f"{abi}: LOAD segment aligned to {ph['align']} bytes, needs {MIN_ALIGN} (16 KB pages)")
            break
    return problems


def check_origin(download_json: str, version: str, commit: str) -> list[str]:
    """`go mod download -json` reports which commit the proxy resolved the tag to; it must be the pinned one."""
    try:
        info = json.loads(download_json)
    except json.JSONDecodeError:
        return ["go mod download -json printed no JSON"]
    problems = []
    if info.get("Error"):
        problems.append(f"go mod download failed: {info['Error']}")
    if info.get("Path") != "github.com/rclone/rclone":
        problems.append(f"unexpected module {info.get('Path')!r}")
    if info.get("Version") != version:
        problems.append(f"module version is {info.get('Version')!r}, pinned {version}")
    origin = info.get("Origin") or {}
    if origin.get("Hash") != commit:
        problems.append(f"tag {version} resolves to commit {origin.get('Hash')!r}, pinned {commit}")
    if origin.get("Ref") not in (None, f"refs/tags/{version}"):
        problems.append(f"origin ref is {origin.get('Ref')!r}, expected refs/tags/{version}")
    return problems


def check_apk(apk_path: str, abis: list[str]) -> list[str]:
    problems = []
    with zipfile.ZipFile(apk_path) as apk:
        names = set(apk.namelist())
        for abi in abis:
            entry = f"lib/{abi}/{LIB_NAME}"
            if entry not in names:
                problems.append(f"{entry} is missing from the APK")
                continue
            problems.extend(check_binary(apk.read(entry), abi))
    return problems


def gradle_value(text: str, key: str) -> str:
    """First `key = "value"` or `key = 123` assignment in a Gradle Kotlin script."""
    m = re.search(rf'^\s*{re.escape(key)}\s*=\s*(?:"([^"]+)"|(\d+))\s*$', text, re.MULTILINE)
    if not m:
        raise ValueError(f"{key} not found")
    return m.group(1) or m.group(2)


def parse_buildinfo(text: str) -> tuple[str, list[tuple[str, str]]]:
    """Go release and linked modules from `go version -m` output."""
    lines = text.strip().splitlines()
    m = re.search(r":\s+(go\S+)\s*$", lines[0]) if lines else None
    if not m:
        raise ValueError("not `go version -m` output")
    mods = []
    for line in lines[1:]:
        parts = line.split()
        if parts and parts[0] == "=>":
            raise ValueError("replaced modules are not expected in rclone's build")
        if len(parts) >= 3 and parts[0] == "dep":
            mods.append((parts[1], parts[2]))
    return m.group(1), mods


def _module_dir(modcache: str, path: str, version: str) -> str:
    escaped = re.sub(r"[A-Z]", lambda c: "!" + c.group(0).lower(), path)
    return os.path.join(modcache, f"{escaped}@{version}")


_LICENSE_FILE = re.compile(r"(?i)^(licen[cs]e|copying|notice)([-._][\w.-]*)?$")


def licence_names(text: str) -> list[str]:
    """Licences recognised in one licence file (a file may carry two)."""
    flat = " ".join(text.split())
    found = []
    if "Apache License" in flat and "Version 2.0" in flat:
        found.append("Apache-2.0")
    if "Permission is hereby granted, free of charge" in flat:
        found.append("MIT")
    if "Redistribution and use in source and binary forms" in flat:
        bsd3 = any(k in flat for k in ("Neither the name", "names of its contributors", "name of the copyright holder"))
        found.append("BSD-3-Clause" if bsd3 else "BSD-2-Clause")
    if "Permission to use, copy, modify, and/or distribute" in flat:
        found.append("ISC")
    if "public domain with CC0 1.0" in flat:
        found.append("CC0-1.0")
    return found


def notices(buildinfo: str, modcache: str, goroot: str) -> str:
    """Every licence (and Apache NOTICE) of what `go version -m` says is linked, rclone's own first."""
    go, mods = parse_buildinfo(buildinfo)
    entries = [(f"Go standard library and runtime {go}", [os.path.join(goroot, "LICENSE")])]
    for path, version in sorted(mods, key=lambda m: (m[0] != "github.com/rclone/rclone", m[0])):
        d = _module_dir(modcache, path, version)
        files = sorted(f for f in os.listdir(d) if _LICENSE_FILE.match(f)) if os.path.isdir(d) else []
        if not files:
            raise ValueError(f"no licence file for {path} {version}")
        entries.append((f"{path} {version}", [os.path.join(d, f) for f in files]))
    index, sections = [], []
    for title, files in entries:
        names, texts = [], []
        for f in files:
            with open(f, encoding="utf-8", errors="replace") as fh:
                body = fh.read().strip()
            if not os.path.basename(f).upper().startswith("NOTICE"):
                found = licence_names(body)
                if not found:
                    raise ValueError(f"unrecognised licence in {title}: {os.path.basename(f)}")
                names.extend(n for n in found if n not in names)
            texts.append(f"---- {os.path.basename(f)} ----\n{body}\n")
        index.append(f"  {title} ({', '.join(names)})")
        sections.append(f"=== {title} ===\n" + "\n".join(texts))
    header = (
        "Third-party notices for librclone.so, the private-cloud helper inside Aura Hi-Res\n"
        "\n"
        "librclone.so is rclone (https://rclone.org, MIT licence) trimmed to the OneDrive, Dropbox and local\n"
        "backends and built from source by scripts/build-rclone-android.sh. It contains the Go modules listed\n"
        "below. It links Android's own C library dynamically; the NDK compiler runtime it may contain is\n"
        "Apache-2.0 WITH LLVM-exception, which asks for no notice in binary form.\n"
        "\n"
        "Generated by scripts/rclone_android/rclone_android.py notices; do not edit by hand.\n"
        "\n"
        "Components:\n"
    )
    return header + "\n".join(index) + "\n\n" + "\n".join(sections)


def check_notices(buildinfo: str, notices_text: str) -> list[str]:
    go, mods = parse_buildinfo(buildinfo)
    titles = [f"Go standard library and runtime {go}"] + [f"{p} {v}" for p, v in mods]
    return [
        f"{NOTICES_PATH} lacks {t}: regenerate it (rclone_android.py notices)"
        for t in titles
        if f"=== {t} ===\n" not in notices_text
    ]


def _report(problems: list[str], ok: str) -> int:
    for p in problems:
        print(p, file=sys.stderr)
    if problems:
        return 1
    print(ok)
    return 0


def main(argv: list[str]) -> int:
    if len(argv) >= 3 and argv[0] == "gradle":
        with open(argv[1], encoding="utf-8") as f:
            print(gradle_value(f.read(), argv[2]))
        return 0
    if len(argv) == 3 and argv[0] == "origin":
        return _report(check_origin(sys.stdin.read(), argv[1], argv[2]), f"origin ok: {argv[1]} = {argv[2]}")
    if len(argv) == 3 and argv[0] == "binary":
        with open(argv[2], "rb") as f:
            data = f.read()
        return _report(check_binary(data, argv[1]), f"{argv[1]}: ok, {len(data)} bytes")
    if len(argv) == 3 and argv[0] == "notices":
        sys.stdout.write(notices(sys.stdin.read(), argv[1], argv[2]))
        return 0
    if len(argv) == 2 and argv[0] == "notices-check":
        with open(argv[1], encoding="utf-8") as f:
            text = f.read()
        return _report(check_notices(sys.stdin.read(), text), "notices ok")
    if len(argv) >= 3 and argv[0] == "apk":
        return _report(check_apk(argv[1], argv[2:]), f"apk ok: {LIB_NAME} for {' '.join(argv[2:])}")
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
