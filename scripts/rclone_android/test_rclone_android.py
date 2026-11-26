"""Host-side tests for rclone_android.py (registry rows 886-887). Run: cd scripts/rclone_android && python3 -m unittest -v"""

import io
import json
import os
import struct
import tempfile
import unittest
import zipfile

import rclone_android as ra

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def make_elf(bits=64, machine=183, e_type=3, interp="/system/bin/linker64", align=16384, loads=2):
    """A minimal little-endian ELF: header, program headers (INTERP + LOADs), then the interpreter string."""
    interp_bytes = interp.encode() + b"\0" if interp is not None else b""
    if bits == 64:
        ehsize, phentsize = 64, 56
    else:
        ehsize, phentsize = 52, 32
    phnum = loads + (1 if interp is not None else 0)
    interp_off = ehsize + phnum * phentsize
    phdrs = b""
    if interp is not None:
        phdrs += _phdr(bits, ra.PT_INTERP, interp_off, len(interp_bytes), 1)
    for _ in range(loads):
        phdrs += _phdr(bits, ra.PT_LOAD, 0, 0, align)
    ident = b"\x7fELF" + bytes([2 if bits == 64 else 1, 1, 1]) + b"\0" * 9
    if bits == 64:
        header = ident + struct.pack("<HHIQQQIHHHHHH", e_type, machine, 1, 0, ehsize, 0, 0, ehsize, phentsize, phnum, 0, 0, 0)
    else:
        header = ident + struct.pack("<HHIIIIIHHHHHH", e_type, machine, 1, 0, ehsize, 0, 0, ehsize, phentsize, phnum, 0, 0, 0)
    assert len(header) == ehsize
    return header + phdrs + interp_bytes


def _phdr(bits, p_type, offset, filesz, align):
    if bits == 64:
        return struct.pack("<IIQQQQQQ", p_type, 4, offset, 0, 0, filesz, filesz, align)
    return struct.pack("<IIIIIIII", p_type, offset, 0, 0, filesz, filesz, 4, align)


class BinaryCheckTest(unittest.TestCase):
    def test_each_abi_accepts_its_own_android_executable(self):
        for abi, (cls, machine, interp) in ra.ABIS.items():
            with self.subTest(abi=abi):
                data = make_elf(bits=64 if cls == 2 else 32, machine=machine, interp=interp)
                self.assertEqual(ra.check_binary(data, abi), [])

    def test_four_kb_alignment_is_refused(self):
        problems = ra.check_binary(make_elf(align=4096), "arm64-v8a")
        self.assertEqual(len(problems), 1)
        self.assertIn("16 KB", problems[0])

    def test_one_short_segment_is_enough_to_refuse(self):
        data = make_elf(align=16384, loads=3)
        # Lower the alignment of the last LOAD only.
        last = len(data) - len("/system/bin/linker64\0") - 56
        data = data[:last] + _phdr(64, ra.PT_LOAD, 0, 0, 4096) + data[last + 56 :]
        self.assertTrue(any("16 KB" in p for p in ra.check_binary(data, "arm64-v8a")))

    def test_sixty_four_kb_alignment_is_fine(self):
        self.assertEqual(ra.check_binary(make_elf(align=65536), "arm64-v8a"), [])

    def test_wrong_architecture_is_refused(self):
        data = make_elf(machine=62)  # x86_64 binary offered as arm64
        self.assertTrue(any("another architecture" in p for p in ra.check_binary(data, "arm64-v8a")))

    def test_thirty_two_bit_binary_is_refused_for_a_64_bit_abi(self):
        data = make_elf(bits=32, machine=183, interp="/system/bin/linker")
        self.assertTrue(ra.check_binary(data, "arm64-v8a"))

    def test_non_pie_executable_is_refused(self):
        self.assertTrue(any("position-independent" in p for p in ra.check_binary(make_elf(e_type=2), "arm64-v8a")))

    def test_glibc_linked_binary_is_refused(self):
        data = make_elf(machine=62, interp="/lib64/ld-linux-x86-64.so.2")
        self.assertTrue(any("not linked for Android" in p for p in ra.check_binary(data, "x86_64")))

    def test_static_binary_without_interpreter_is_refused(self):
        self.assertTrue(any("interpreter" in p for p in ra.check_binary(make_elf(interp=None), "arm64-v8a")))

    def test_garbage_and_truncated_files_are_refused_not_crashing(self):
        self.assertTrue(ra.check_binary(b"#!/bin/sh\necho hi\n" + b"\0" * 64, "x86"))
        self.assertTrue(ra.check_binary(make_elf()[:70], "arm64-v8a"))
        self.assertTrue(ra.check_binary(b"", "arm64-v8a"))

    def test_unknown_abi_is_refused(self):
        self.assertEqual(ra.check_binary(make_elf(), "mips"), ["unknown ABI mips"])


class OriginCheckTest(unittest.TestCase):
    VERSION = "v1.76.0"
    COMMIT = "e982ff36668f5130d94b4f70409babb3d964df42"

    def payload(self, **over):
        info = {
            "Path": "github.com/rclone/rclone",
            "Version": self.VERSION,
            "Origin": {"VCS": "git", "URL": "https://github.com/rclone/rclone", "Hash": self.COMMIT,
                       "Ref": f"refs/tags/{self.VERSION}"},
        }
        info.update(over)
        return json.dumps(info)

    def test_pinned_commit_passes(self):
        self.assertEqual(ra.check_origin(self.payload(), self.VERSION, self.COMMIT), [])

    def test_moved_tag_is_refused(self):
        bad = self.payload(Origin={"Hash": "0" * 40, "Ref": f"refs/tags/{self.VERSION}"})
        self.assertTrue(any("resolves to commit" in p for p in ra.check_origin(bad, self.VERSION, self.COMMIT)))

    def test_missing_origin_is_refused(self):
        bad = self.payload(Origin=None)
        self.assertTrue(ra.check_origin(bad, self.VERSION, self.COMMIT))

    def test_other_version_or_module_is_refused(self):
        self.assertTrue(ra.check_origin(self.payload(Version="v1.75.2"), self.VERSION, self.COMMIT))
        self.assertTrue(ra.check_origin(self.payload(Path="github.com/evil/rclone"), self.VERSION, self.COMMIT))

    def test_download_error_and_non_json_are_refused(self):
        self.assertTrue(ra.check_origin(self.payload(Error="not found"), self.VERSION, self.COMMIT))
        self.assertEqual(ra.check_origin("", self.VERSION, self.COMMIT), ["go mod download -json printed no JSON"])


class ApkCheckTest(unittest.TestCase):
    def write_apk(self, entries):
        fd, path = tempfile.mkstemp(suffix=".apk")
        os.close(fd)
        self.addCleanup(os.remove, path)
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
            for name, data in entries.items():
                z.writestr(name, data)
        return path

    def test_apk_with_every_abi_passes(self):
        entries = {}
        for abi, (cls, machine, interp) in ra.ABIS.items():
            entries[f"lib/{abi}/librclone.so"] = make_elf(bits=64 if cls == 2 else 32, machine=machine, interp=interp)
        self.assertEqual(ra.check_apk(self.write_apk(entries), list(ra.ABIS)), [])

    def test_missing_abi_is_reported(self):
        apk = self.write_apk({"lib/arm64-v8a/librclone.so": make_elf()})
        self.assertEqual(ra.check_apk(apk, ["arm64-v8a", "x86_64"]), ["lib/x86_64/librclone.so is missing from the APK"])

    def test_packaged_binary_is_checked_too(self):
        apk = self.write_apk({"lib/arm64-v8a/librclone.so": make_elf(align=4096)})
        self.assertTrue(ra.check_apk(apk, ["arm64-v8a"]))


class GradleValueTest(unittest.TestCase):
    def test_quoted_and_numeric_values(self):
        text = 'android {\n    ndkVersion = "27.3.13750724"\n    defaultConfig {\n        minSdk = 26\n    }\n}\n'
        self.assertEqual(ra.gradle_value(text, "ndkVersion"), "27.3.13750724")
        self.assertEqual(ra.gradle_value(text, "minSdk"), "26")

    def test_missing_key_raises(self):
        with self.assertRaises(ValueError):
            ra.gradle_value("android {}\n", "ndkVersion")

    def test_the_real_build_script_still_declares_both(self):
        # The build script reads these two from app/build.gradle.kts; a rename there must fail here, not in CI.
        with open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8") as f:
            text = f.read()
        self.assertRegex(ra.gradle_value(text, "ndkVersion"), r"^\d+\.\d+\.\d+$")
        self.assertRegex(ra.gradle_value(text, "minSdk"), r"^\d+$")


class RepositoryWiringTest(unittest.TestCase):
    def read(self, *parts):
        with open(os.path.join(ROOT, *parts), encoding="utf-8") as f:
            return f.read()

    def test_script_pins_match_the_go_module(self):
        script = self.read("scripts", "build-rclone-android.sh")
        gomod = self.read("tools", "rclone-android", "go.mod")
        version = script.split("RCLONE_VERSION=", 1)[1].split()[0]
        toolchain = script.split("GO_TOOLCHAIN=", 1)[1].split()[0]
        self.assertIn(f"require github.com/rclone/rclone {version}\n", gomod)
        self.assertIn(f"toolchain {toolchain}\n", gomod)
        self.assertIn(f"github.com/rclone/rclone {version} h1:", self.read("tools", "rclone-android", "go.sum"))

    def test_built_binaries_are_never_committed(self):
        self.assertIn("tools/rclone-android/out/", self.read(".gitignore").splitlines())

    def test_real_binaries_verify_when_built(self):
        out = os.path.join(ROOT, "tools", "rclone-android", "out", "jniLibs")
        built = [abi for abi in ra.ABIS if os.path.isfile(os.path.join(out, abi, ra.LIB_NAME))]
        if not built:
            self.skipTest("no librclone.so built here (scripts/build-rclone-android.sh)")
        for abi in built:
            with open(os.path.join(out, abi, ra.LIB_NAME), "rb") as f:
                self.assertEqual(ra.check_binary(f.read(), abi), [], abi)


MIT_TEXT = "Copyright (c) 2020 Someone\n\nPermission is hereby granted, free of charge, to any person obtaining a copy"
BSD3_TEXT = (
    "Copyright 2009 The Go Authors.\n\nRedistribution and use in source and binary forms, with or without\n"
    "modification, are permitted... Neither the name of Google LLC nor the names of its contributors"
)
APACHE_TEXT = "                                 Apache License\n                           Version 2.0, January 2004"
BUILDINFO = (
    "/somewhere/librclone.so: go1.27.4\n"
    "\tpath\taura/rclone-android\n"
    "\tmod\taura/rclone-android\t(devel)\t\n"
    "\tdep\tgithub.com/Max-Sum/base32768\tv0.0.1\th1:abc=\n"
    "\tdep\tgithub.com/rclone/rclone\tv1.76.0\th1:def=\n"
    "\tbuild\tGOOS=android\n"
)


class NoticesTest(unittest.TestCase):
    def fake_tree(self):
        root = tempfile.mkdtemp()
        self.addCleanup(__import__("shutil").rmtree, root)
        goroot = os.path.join(root, "goroot")
        os.makedirs(goroot)
        with open(os.path.join(goroot, "LICENSE"), "w") as f:
            f.write(BSD3_TEXT)
        modcache = os.path.join(root, "mod")
        # Upper-case letters are escaped as "!" + lower-case in the module cache.
        base = os.path.join(modcache, "github.com", "!max-!sum", "base32768@v0.0.1")
        os.makedirs(base)
        with open(os.path.join(base, "LICENSE"), "w") as f:
            f.write(MIT_TEXT)
        rclone = os.path.join(modcache, "github.com", "rclone", "rclone@v1.76.0")
        os.makedirs(rclone)
        with open(os.path.join(rclone, "COPYING"), "w") as f:
            f.write(MIT_TEXT)
        with open(os.path.join(rclone, "NOTICE"), "w") as f:
            f.write("rclone notice text")
        return modcache, goroot

    def test_buildinfo_is_parsed(self):
        go, mods = ra.parse_buildinfo(BUILDINFO)
        self.assertEqual(go, "go1.27.4")
        self.assertEqual(mods, [("github.com/Max-Sum/base32768", "v0.0.1"), ("github.com/rclone/rclone", "v1.76.0")])
        with self.assertRaises(ValueError):
            ra.parse_buildinfo("")
        with self.assertRaises(ValueError):
            ra.parse_buildinfo(BUILDINFO + "\t=>\tgithub.com/evil/fork\tv9\th1:x=\n")

    def test_licences_are_recognised(self):
        self.assertEqual(ra.licence_names(MIT_TEXT), ["MIT"])
        self.assertEqual(ra.licence_names(BSD3_TEXT), ["BSD-3-Clause"])
        self.assertEqual(ra.licence_names(APACHE_TEXT), ["Apache-2.0"])
        self.assertEqual(ra.licence_names("This work is released into the public domain with CC0 1.0."), ["CC0-1.0"])
        self.assertEqual(ra.licence_names("All rights reserved. No permission."), [])

    def test_notices_carry_every_text_rclone_first(self):
        modcache, goroot = self.fake_tree()
        text = ra.notices(BUILDINFO, modcache, goroot)
        self.assertIn("  github.com/rclone/rclone v1.76.0 (MIT)", text)
        self.assertLess(text.index("=== github.com/rclone/rclone v1.76.0 ==="),
                        text.index("=== github.com/Max-Sum/base32768 v0.0.1 ==="))
        self.assertIn("rclone notice text", text)
        self.assertIn("=== Go standard library and runtime go1.27.4 ===", text)
        self.assertEqual(ra.check_notices(BUILDINFO, text), [])

    def test_a_module_without_a_recognised_licence_stops_the_generator(self):
        modcache, goroot = self.fake_tree()
        with open(os.path.join(modcache, "github.com", "!max-!sum", "base32768@v0.0.1", "LICENSE"), "w") as f:
            f.write("proprietary")
        with self.assertRaises(ValueError):
            ra.notices(BUILDINFO, modcache, goroot)

    def test_a_new_module_or_go_release_is_reported(self):
        modcache, goroot = self.fake_tree()
        text = ra.notices(BUILDINFO, modcache, goroot)
        newer = BUILDINFO.replace("go1.27.4", "go1.27.5").replace("v0.0.1", "v0.0.2")
        problems = ra.check_notices(newer, text)
        self.assertEqual(len(problems), 2)
        self.assertTrue(all(ra.NOTICES_PATH in p for p in problems))

    def test_the_shipped_notices_cover_the_pinned_rclone_and_go(self):
        with open(os.path.join(ROOT, "scripts", "build-rclone-android.sh"), encoding="utf-8") as f:
            script = f.read()
        version = script.split("RCLONE_VERSION=", 1)[1].split()[0]
        toolchain = script.split("GO_TOOLCHAIN=", 1)[1].split()[0]
        with open(os.path.join(ROOT, ra.NOTICES_PATH), encoding="utf-8") as f:
            shipped = f.read()
        self.assertIn(f"=== github.com/rclone/rclone {version} ===\n", shipped)
        self.assertIn(f"=== Go standard library and runtime {toolchain} ===\n", shipped)
        self.assertIn("Copyright (C) 2012 by Nick Craig-Wood", shipped)
        self.assertIn(f"NOTICES={ra.NOTICES_PATH}\n", script)


class CliTest(unittest.TestCase):
    def test_usage_on_bad_arguments(self):
        import contextlib

        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            self.assertEqual(ra.main(["nonsense"]), 2)
        self.assertIn("librclone.so", err.getvalue())


if __name__ == "__main__":
    unittest.main()
