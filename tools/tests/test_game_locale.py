from pathlib import Path
import runpy
import unittest


BIN = Path(__file__).resolve().parents[1] / "linuxfs/overlay/usr/local/bin"
GAME_ENV = runpy.run_path(str(BIN / "droiddeck-game-env"))
game_locale = GAME_ENV["game_locale"]


class GameLocaleTest(unittest.TestCase):
    def test_the_device_language_replaces_the_plain_session_locale(self):
        env = game_locale({"LANG": "C.UTF-8", "LC_ALL": "C", "DD_GAME_LANG": "de_DE.UTF-8"}, installed=lambda _: True)
        self.assertEqual("de_DE.UTF-8", env["LANG"])
        self.assertNotIn("LC_ALL", env)

    def test_a_missing_locale_keeps_the_session_locale(self):
        env = game_locale({"LANG": "C.UTF-8", "DD_GAME_LANG": "de_DE.UTF-8"}, installed=lambda _: False)
        self.assertEqual("C.UTF-8", env["LANG"])

    def test_a_locale_the_user_set_wins(self):
        env = game_locale({"LANG": "fr_FR.UTF-8", "DD_GAME_LANG": "de_DE.UTF-8"}, installed=lambda _: True)
        self.assertEqual("fr_FR.UTF-8", env["LANG"])
        env = game_locale({"LANG": "C.UTF-8", "LC_ALL": "ja_JP.UTF-8", "DD_GAME_LANG": "de_DE.UTF-8"}, installed=lambda _: True)
        self.assertEqual("C.UTF-8", env["LANG"])

    def test_epic_gets_its_own_locale_form_even_without_the_glibc_locale(self):
        env = game_locale({"LANG": "C.UTF-8", "DD_GAME_LANG": "de_DE.UTF-8"}, installed=lambda _: False)
        self.assertEqual("de", env["DD_EPIC_LOCALE"])
        self.assertEqual("pt-BR", GAME_ENV["epic_locale"]("pt_BR.UTF-8"))
        self.assertEqual("zh-Hant", GAME_ENV["epic_locale"]("zh_TW.UTF-8"))

    def test_english_or_no_language_changes_nothing(self):
        self.assertEqual({"LANG": "C.UTF-8"}, game_locale({"LANG": "C.UTF-8"}, installed=lambda _: True))
        env = game_locale({"LANG": "C.UTF-8", "DD_GAME_LANG": "C.UTF-8"}, installed=lambda _: True)
        self.assertEqual("C.UTF-8", env["LANG"])


if __name__ == "__main__":
    unittest.main()
