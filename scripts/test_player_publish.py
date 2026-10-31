import json
import os
import unittest

import player_publish as pp

CONFIGS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "player_configs.json")


class PlayerPublishTest(unittest.TestCase):
    def test_writing_the_file_back_changes_nothing_but_the_new_entry(self):
        """The button must never reformat the file the app reads live: an untouched file round-trips byte for byte."""
        with open(CONFIGS, encoding="utf-8") as f:
            text = f.read()
        self.assertEqual(pp.render(json.loads(text)), text)

    def test_the_new_entry_goes_first(self):
        old = [{"hash": "11111111"}]
        out = json.loads(pp.render([{"hash": "22222222"}] + old))
        self.assertEqual([e["hash"] for e in out], ["22222222", "11111111"])


if __name__ == "__main__":
    unittest.main()
