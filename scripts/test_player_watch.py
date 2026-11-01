import json
import unittest

import player_watch as pw

HTML = 'x "jsUrl":"/s/player/abcdef12/player_ias.vflset/en_US/base.js" y'
KT = '''
    private val KNOWN_PLAYER_CONFIGS = mapOf(
        "3891b194" to HardcodedPlayerConfig(
            signatureTimestamp = 1,
        ),
        "c74cbcd6" to HardcodedPlayerConfig(
'''


class PlayerWatchTest(unittest.TestCase):
    def test_finds_the_player_in_the_page(self):
        url = pw.player_url_from_html(HTML)
        self.assertEqual(url, "https://music.youtube.com/s/player/abcdef12/player_ias.vflset/en_US/base.js")
        self.assertEqual(pw.hash_of(url), "abcdef12")
        self.assertIsNone(pw.player_url_from_html("<html>nothing</html>"))
        self.assertIsNone(pw.hash_of(None))

    def test_known_hashes_come_from_both_places(self):
        cfg = json.dumps([{"hash": "dfc3d2b2"}, {"hash": "not-a-hash"}, {"nohash": 1}])
        self.assertEqual(pw.hashes_in_json(cfg), {"dfc3d2b2"})
        self.assertEqual(pw.hashes_in_json(json.dumps({"configs": [{"hash": "aaaaaaaa"}]})), {"aaaaaaaa"})
        self.assertEqual(pw.hashes_in_kotlin(KT), {"3891b194", "c74cbcd6"})
        self.assertEqual(pw.known_hashes(cfg, KT), {"dfc3d2b2", "3891b194", "c74cbcd6"})

    def test_the_real_repository_files_parse_and_have_entries(self):
        with open(pw.CONFIGS, encoding="utf-8") as f:
            configs = f.read()
        with open(pw.KT, encoding="utf-8") as f:
            kotlin = f.read()
        known = pw.known_hashes(configs, kotlin)
        self.assertGreater(len(known), 10)
        self.assertIn("3891b194", known)


if __name__ == "__main__":
    unittest.main()
