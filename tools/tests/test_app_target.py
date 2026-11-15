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
            'if [[ "$*" == "shell am get-current-user" ]]; then echo 10; fi\n'
            'if [[ "$1" == install && "${FAIL_INSTALL:-}" == 1 ]]; then\n'
            '  echo INSTALL_FAILED_UPDATE_INCOMPATIBLE; exit 1\n'
            'fi\n', encoding="utf-8", newline="\n",
        )
        self.adb.chmod(0o755)

    def run_script(self, name, *args, **overrides):
        env = dict(os.environ, ADB=self.adb.as_posix(), ADB_LOG=self.log.as_posix())
        for key in ("APP_ID", "ACTIVITY", "FAIL_INSTALL"):
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
        self.assertIn("shell am start -n com.firstride/com.firstride.MainActivity", calls)

    def test_qa_package_uses_original_activity_namespace(self):
        result, calls = self.run_script("signals.sh", "real", APP_ID="com.firstride.qa39")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("shell run-as com.firstride.qa39 --user 10 touch files/real_signals", calls)
        self.assertIn("shell am force-stop com.firstride.qa39", calls)
        self.assertIn("shell am start -n com.firstride.qa39/com.firstride.MainActivity", calls)

    def test_fake_mode_changes_only_qa_package(self):
        result, calls = self.run_script("signals.sh", "fake", APP_ID="com.firstride.qa39")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("shell run-as com.firstride.qa39 --user 10 rm -f files/real_signals", calls)

    def test_install_failure_never_deletes_or_launches_existing_app(self):
        result, calls = self.run_script("install.sh", "debug", FAIL_INSTALL="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(calls, ["install -r -d app/build/outputs/apk/debug/app-debug.apk"])

    def test_install_launches_requested_measure_activity(self):
        result, calls = self.run_script(
            "install.sh", "debug", APP_ID="com.firstride.qa39",
            ACTIVITY=".measure.MeasureActivity",
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("shell am start -n com.firstride.qa39/com.firstride.measure.MeasureActivity", calls)

    def test_invalid_build_type_does_not_touch_device(self):
        result, calls = self.run_script("install.sh", "unknown")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(calls, [])
