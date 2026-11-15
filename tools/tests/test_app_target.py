"""QA 패키지 선택과 설치 실패 시 데이터 보존을 실제 Bash 실행으로 확인한다."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
BASH = os.environ.get("BASH_EXE") or shutil.which("bash")


class AppTargetTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.log = Path(self.temp.name) / "adb.log"
        self.adb = Path(self.temp.name) / "adb.sh"
        self.adb.write_text(
            '#!/usr/bin/env bash\n'
            'printf "%s\\n" "$*" >> "$ADB_LOG"\n'
            'if [[ "$*" == "shell am get-current-user" ]]; then echo "${TEST_ANDROID_USER-10}"; fi\n'
            'if [[ "$1" == install && "${FAIL_INSTALL:-}" == 1 ]]; then\n'
            '  echo INSTALL_FAILED_UPDATE_INCOMPATIBLE; exit 1\n'
            'fi\n', encoding="utf-8", newline="\n",
        )
        self.adb.chmod(0o755)

    def run_script(self, name, *args, **overrides):
        env = dict(os.environ, ADB=self.adb.as_posix(), ADB_LOG=self.log.as_posix())
        for key in ("APP_ID", "ACTIVITY", "FAIL_INSTALL", "TEST_ANDROID_USER"):
            env.pop(key, None)
        env.update(overrides)
        result = subprocess.run(
            [BASH, f"tools/{name}", *args], cwd=ROOT, env=env,
            text=True, encoding="utf-8", capture_output=True,
        )
        calls = self.log.read_text(encoding="utf-8").splitlines() if self.log.exists() else []
        return result, calls

    def test_default_package_still_works(self):
        result, calls = self.run_script("signals.sh", "real")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("shell run-as com.firstride --user 10 touch files/real_signals", calls)
        self.assertIn("shell am start --user 10 -n com.firstride/com.firstride.MainActivity", calls)

    def test_qa_package_uses_original_activity_namespace(self):
        result, calls = self.run_script("signals.sh", "real", APP_ID="com.firstride.qa39")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("shell run-as com.firstride.qa39 --user 10 touch files/real_signals", calls)
        self.assertIn("shell am force-stop --user 10 com.firstride.qa39", calls)
        self.assertIn("shell am start --user 10 -n com.firstride.qa39/com.firstride.MainActivity", calls)

    def test_fake_mode_changes_only_qa_package(self):
        result, calls = self.run_script("signals.sh", "fake", APP_ID="com.firstride.qa39")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("shell run-as com.firstride.qa39 --user 10 rm -f files/real_signals", calls)

    def test_install_failure_never_deletes_or_launches_existing_app(self):
        result, calls = self.run_script("install.sh", "debug", FAIL_INSTALL="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(calls, ["shell am get-current-user", "install -r -d app/build/outputs/apk/debug/app-debug.apk"])

    def test_install_launches_requested_measure_activity(self):
        result, calls = self.run_script(
            "install.sh", "debug", APP_ID="com.firstride.qa39",
            ACTIVITY=".measure.MeasureActivity",
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("shell am start --user 10 -n com.firstride.qa39/com.firstride.measure.MeasureActivity", calls)

    def test_invalid_build_type_does_not_touch_device(self):
        result, calls = self.run_script("install.sh", "unknown")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(calls, [])

    def test_other_android_user_is_used_for_marker_stop_and_start(self):
        result, calls = self.run_script("signals.sh", "real", APP_ID="com.firstride.qa39", TEST_ANDROID_USER="11")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("shell run-as com.firstride.qa39 --user 11 touch files/real_signals", calls)
        self.assertIn("shell am force-stop --user 11 com.firstride.qa39", calls)
        self.assertIn("shell am start --user 11 -n com.firstride.qa39/com.firstride.MainActivity", calls)

    def test_unreadable_android_user_prevents_signal_changes_and_install(self):
        for script, arg in (("signals.sh", "real"), ("install.sh", "debug")):
            if self.log.exists():
                self.log.unlink()
            result, calls = self.run_script(script, arg, TEST_ANDROID_USER="")
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(calls, ["shell am get-current-user"])

    def tap_ui(self, xml, text="Start", **overrides):
        dump = Path(self.temp.name) / "ui.xml"
        dump.write_text(xml, encoding="utf-8")
        self.adb.write_text('#!/usr/bin/env bash\nprintf "%s\\n" "$*" >> "$ADB_LOG"\n'
                            'if [[ "$1" == exec-out ]]; then cat "$UI_DUMP"; fi\n', encoding="utf-8", newline="\n")
        return self.run_script("tap.sh", text, UI_DUMP=dump.as_posix(), **overrides)

    def test_tap_with_app_id_ignores_another_apps_identical_button(self):
        xml = '<hierarchy><node package="native" text="Start" bounds="[0,0][10,10]"/>' \
              '<node package="com.firstride.qa39" text="Start" bounds="[100,100][200,200]"/></hierarchy>'
        result, calls = self.tap_ui(xml, APP_ID="com.firstride.qa39")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls[-1], "shell input tap 150 150")

    def test_tap_decodes_xml_escaped_text(self):
        xml = '<hierarchy><node text="A &amp; B &quot;C&quot;" bounds="[10,20][30,40]"/></hierarchy>'
        result, calls = self.tap_ui(xml, 'A & B "C"')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls[-1], "shell input tap 20 30")

    def test_tap_skips_zero_sized_elements(self):
        xml = '<hierarchy><node text="Start" bounds="[0,0][0,0]"/>' \
              '<node text="Start" bounds="[20,20][40,40]"/></hierarchy>'
        result, calls = self.tap_ui(xml)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls[-1], "shell input tap 30 30")

    def test_truncated_dump_never_triggers_a_tap(self):
        result, calls = self.tap_ui('<hierarchy><node text="Start" bounds="[10,20][30,40]"/>')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any(call.startswith("shell input tap") for call in calls))

    def test_tap_without_app_id_can_still_target_native_controls(self):
        result, calls = self.tap_ui('<hierarchy><node package="native" content-desc="Start" bounds="[10,20][30,40]"/></hierarchy>')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls[-1], "shell input tap 20 30")
