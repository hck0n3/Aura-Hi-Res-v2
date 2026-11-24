import json
import os
import unittest

import simpmusic_watch as sw

SIMPMUSIC_TOML = '''
[versions]
# App version
newpipe = "v0.26.1"
brave-newpipe = "991b158fee"
pipepipe = "bba8d84453" # pinned
quickjs = "1.0.15"

[libraries]
pipepipe = "not-a-version"
'''

AURA_TOML = '''
[versions]
pipepipeextractor = "bba8d84453"
bravenewpipeextractor = "0fcaf19ecc"
'''

REGISTRY = json.dumps({
    "schemaVersion": 1,
    "players": {
        "13792696": {"sig": "ST(1,8150,INPUT)", "nClass": "oO", "sts": 20634, "aliases": ["5ff90ebf"]},
        "24299858": {"sig": "a0(72,7798,INPUT)", "nClass": "kq", "sts": 20740, "aliases": ["7B58401D", "x"]},
        "broken": "not an object",
    },
})

S = "service/kotlinYtmusicScraper/src/"
P = "kotlin/com/maxrave/kotlinytmusicscraper/"


class VersionTest(unittest.TestCase):
    def test_reads_only_the_versions_table(self):
        self.assertEqual(sw.simpmusic_pins(SIMPMUSIC_TOML), {"pipepipe": "bba8d84453", "brave": "991b158fee"})
        self.assertEqual(sw.aura_pins(AURA_TOML), {"pipepipe": "bba8d84453", "brave": "0fcaf19ecc"})
        self.assertEqual(sw.simpmusic_pins("[libraries]\npipepipe = \"x\"\n"), {"pipepipe": None, "brave": None})

    def test_abbreviated_and_full_shas_are_the_same_commit(self):
        self.assertTrue(sw.same_commit("bba8d84453", "bba8d84453bb2beab4182e5d16721e25c739b777"))
        self.assertTrue(sw.same_commit("BBA8D84453", "bba8d84453"))
        self.assertFalse(sw.same_commit("bba8d84453", "f8982ca9e7"))
        self.assertFalse(sw.same_commit(None, "f8982ca9e7"))

    def test_pin_changes(self):
        seen = {"pipepipe": "bba8d84453bb2beab4182e5d16721e25c739b777", "brave": "991b158fee64c95f340803d26dc5b938b4b1cd01"}
        self.assertEqual(sw.pin_changes(sw.simpmusic_pins(SIMPMUSIC_TOML), seen), [])
        self.assertEqual(sw.pin_changes(sw.simpmusic_pins(SIMPMUSIC_TOML), sw.aura_pins(AURA_TOML)),
                         [{"key": "brave", "old": "0fcaf19ecc", "new": "991b158fee"}])
        self.assertEqual(sw.pin_changes({"pipepipe": None, "brave": None}, seen), [])


class PathTest(unittest.TestCase):
    def test_only_playback_files_are_watched(self):
        paths = [
            S + "commonMain/" + P + "cipher/ZemerCipherSolver.kt",
            S + "commonMain/" + P + "YouTube.kt",
            S + "commonMain/" + P + "YouTube.kt.bak",
            S + "commonMain/" + P + "models/YouTubeClient.kt",
            S + "commonMain/" + P + "models/response/PlayerResponse.kt",
            S + "androidMain/" + P + "extractor/FaradayJsDecoder.kt",
            S + "commonMain/assets/po_token.html",
            "data/src/commonMain/kotlin/com/maxrave/data/repository/StreamRepositoryImpl.kt",
            "data/src/commonMain/kotlin/com/maxrave/data/repository/LyricsRepositoryImpl.kt",
            "README.md",
        ]
        self.assertEqual(sw.watched(paths), [paths[0], paths[1], paths[3], paths[5], paths[6], paths[7]])

    def test_every_watched_path_maps_to_aura_files(self):
        for p in sw.CORE_PATHS:
            probe = p + "X.kt" if p.endswith("/") else p
            self.assertTrue(sw.aura_targets(probe), p)
        self.assertEqual(sw.aura_targets(S + "androidMain/" + P + "extractor/FaradayJsDecoder.kt"),
                         ["app/src/main/kotlin/com/music/echo/utils/cipher/PipePipeLocalCipherDecoder.kt"])
        self.assertIn("player_configs.json", sw.aura_targets(S + "commonMain/" + P + "cipher/RemotePlayerConfigParser.kt"))
        self.assertEqual(sw.aura_targets("README.md"), [])

    def test_mapped_aura_files_exist(self):
        for _, targets in sw.AURA_MAP:
            for t in targets:
                path = os.path.join(sw.ROOT, t.split(" (")[0])
                self.assertTrue(os.path.exists(path), t)

    def test_cap(self):
        self.assertEqual(sw.cap("abc", 5), ("abc", False))
        self.assertEqual(sw.cap("abcdef", 3), ("abc", True))


class PlayerDiffTest(unittest.TestCase):
    def test_aliases_count_and_invalid_ids_are_dropped(self):
        players = sw.simpmusic_players(REGISTRY)
        self.assertEqual(set(players), {"13792696", "5ff90ebf", "24299858", "7b58401d"})
        self.assertEqual(players["7b58401d"]["primary"], "24299858")
        self.assertEqual(players["5ff90ebf"]["sts"], 20634)

    def test_our_hashes_both_shapes(self):
        self.assertEqual(sw.our_hashes(json.dumps([{"hash": "13792696"}, {"nohash": 1}])), {"13792696"})
        self.assertEqual(sw.our_hashes(json.dumps({"configs": [{"hash": " 5FF90EBF "}]})), {"5ff90ebf"})

    def test_new_players_are_the_ones_we_lack_newest_first(self):
        ours = json.dumps([{"hash": "13792696"}, {"hash": "7b58401d"}])
        leads = sw.new_players(REGISTRY, ours)
        self.assertEqual([x["hash"] for x in leads], ["24299858", "5ff90ebf"])
        self.assertEqual(leads[0]["sig"], "a0(72,7798,INPUT)")
        self.assertEqual(sw.new_players(REGISTRY, json.dumps([{"hash": h} for h in sw.simpmusic_players(REGISTRY)])), [])

    def test_added_since_seen(self):
        old = json.dumps({"players": {"13792696": {"sts": 1, "aliases": ["5ff90ebf"]}}})
        self.assertEqual(sw.added_since(old, REGISTRY), {"24299858", "7b58401d"})
        self.assertEqual(sw.added_since(REGISTRY, REGISTRY), set())
        self.assertIsNone(sw.added_since(None, REGISTRY))

    def test_real_files_parse(self):
        with open(sw.OURS, encoding="utf-8") as f:
            self.assertTrue(sw.our_hashes(f.read()))
        with open(sw.STATE, encoding="utf-8") as f:
            state = json.load(f)
        for key in ("simpmusic", "core", "pipepipe", "brave", "registry", "checked_at"):
            self.assertIn(key, state)
        with open(sw.AURA_TOML, encoding="utf-8") as f:
            pins = sw.aura_pins(f.read())
        self.assertTrue(pins["pipepipe"] and pins["brave"])


if __name__ == "__main__":
    unittest.main()
