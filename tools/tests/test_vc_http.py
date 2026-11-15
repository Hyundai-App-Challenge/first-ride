"""실제 curl과 로컬 HTTP 서버로 VC 오류 전달을 확인한다. 차량에는 연결하지 않는다."""
import http.server
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import unittest
import urllib.parse


ROOT = Path(__file__).resolve().parents[2]
BASH = os.environ.get("BASH_EXE") or shutil.which("bash")


class Handler(http.server.BaseHTTPRequestHandler):
    def respond(self):
        path = self.path.split("?")[0]
        self.server.calls.append((self.command, path))
        self.server.requests.append((self.command, self.path))
        self.send_response(self.server.statuses.get(path, 200))
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(self.server.body).encode())

    do_POST = do_PUT = do_GET = respond

    def log_message(self, *args):
        pass


class VcHttpTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.adb = Path(self.temp.name) / "adb.sh"
        self.adb.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8", newline="\n")
        self.adb.chmod(0o755)
        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.server.statuses = {}
        self.server.calls = []
        self.server.requests = []
        self.server.body = {"hvac": {"ac": False}}
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def run_vc(self, *args):
        env = dict(os.environ, ADB=self.adb.as_posix(), VC_PORT=str(self.server.server_port))
        env.pop("MSYS_NO_PATHCONV", None)
        return subprocess.run(
            [BASH, "tools/vc.sh", *args], cwd=ROOT, env=env,
            capture_output=True, text=True, encoding="utf-8", timeout=10,
        )

    def test_success_keeps_status_and_state_output(self):
        result = self.run_vc("set", "hvac", "ac=true")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "200")
        self.assertIn(("PUT", "/vehicle/hvac"), self.server.calls)
        result = self.run_vc("state")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout), {"hvac": {"ac": False}})

    def test_vehicle_http_error_fails(self):
        self.server.statuses["/vehicle/hvac"] = 500
        self.assertNotEqual(self.run_vc("set", "hvac", "ac=true").returncode, 0)

    def test_failed_session_never_changes_vehicle(self):
        self.server.statuses["/browser/session"] = 500
        self.assertNotEqual(self.run_vc("set", "hvac", "ac=true").returncode, 0)
        self.assertEqual(self.server.calls, [("POST", "/browser/session")])

    def test_state_http_error_fails(self):
        self.server.statuses["/vehicle/state"] = 500
        self.assertNotEqual(self.run_vc("state").returncode, 0)

    def run_scene(self, scene):
        env = dict(os.environ, ADB=self.adb.as_posix(), VC_PORT=str(self.server.server_port))
        env.pop("MSYS_NO_PATHCONV", None)
        return subprocess.run([BASH, "tools/demo-scene.sh", scene], cwd=ROOT, env=env,
                              capture_output=True, text=True, encoding="utf-8", timeout=15)

    def test_demo_reset_turns_on_hvac_and_failure_does_not_report_success(self):
        self.assertEqual(self.run_scene("reset").returncode, 0)
        hvac = next(url for method, url in self.server.requests if method == "PUT" and url.startswith("/vehicle/hvac?"))
        self.assertEqual(urllib.parse.parse_qs(urllib.parse.urlsplit(hvac).query)["power"], ["true"])
        self.server.statuses["/vehicle/hvac"] = 500
        result = self.run_scene("reset")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("reset:", result.stdout)

    def test_demo_lights_follow_three_course_steps_and_cabin_scene_clears_exterior(self):
        for scene in ("5", "6", "6-1", "6-2", "11"):
            result = self.run_scene(scene)
            self.assertEqual(result.returncode, 0, result.stderr)
        modes = [urllib.parse.parse_qs(urllib.parse.urlsplit(url).query)["value"][0]
                 for method, url in self.server.requests if method == "PUT" and url.startswith("/vehicle/lights?")]
        self.assertEqual(modes, ["Position", "Off", "Position", "LowBeam", "Position", "Off"])

    def run_course_check(self):
        log = Path(self.temp.name) / "adb.log"
        if os.name == "nt":
            # Python도 직접 실행할 수 있는 Windows 테스트용 adb다.
            self.adb = Path(self.temp.name) / "adb.cmd"
            self.adb.write_text('@echo off\necho %*>>"%ADB_LOG%"\n'
                                'if "%1"=="-s" (\nshift\nshift\n)\n'
                                'if "%1"=="devices" echo qa-device device\n'
                                'if "%1 %2 %3"=="shell am get-current-user" echo 10\n'
                                'if "%6"=="test" exit /b 1\nexit /b 0\n', encoding="ascii")
        else:
            self.adb.write_text('#!/usr/bin/env bash\nprintf "%s\\n" "$*" >> "$ADB_LOG"\n'
                                'if [[ "${1:-}" == -s ]]; then shift 2; fi\n'
                                'if [[ "$1" == devices ]]; then printf "List of devices attached\\nqa-device\\tdevice\\n"; fi\n'
                                'if [[ "$*" == "shell am get-current-user" ]]; then echo 10; fi\n'
                                'if [[ "$*" == *"test -e files/real_signals" ]]; then exit 1; fi\n', encoding="utf-8", newline="\n")
        env = dict(os.environ, ADB=self.adb.as_posix(), VC_PORT=str(self.server.server_port))
        env.update(APP_ID="com.firstride.qa39", ANDROID_SERIAL="qa-device", ADB_LOG=log.as_posix(), PYTHONIOENCODING="utf-8")
        env.pop("MSYS_NO_PATHCONV", None)
        result = subprocess.run(
            [BASH, "tools/check-crs01.sh"], cwd=ROOT, env=env,
            capture_output=True, text=True, encoding="utf-8", timeout=10,
        )
        return result, log.read_text(encoding="utf-8").splitlines()

    def test_course_check_stops_on_injection_error_and_restores_source(self):
        self.server.body = {
            "gear": {"actual": {"value": "Park"}},
            "hvac": {"power_on": True, "ac_on": False, "recirc_on": False, "front_defroster_on": False,
                     "rear_defroster_on": False, "fan_speed": 3, "driver": {"fan_direction": 1}},
            "lights": {"headlamp_mode": "Off", "turn_signal_mode": "Off"},
            "body": {"wipers": {"front": {"mode": "Off"}}, "windows": {"front_left": 0}, "door_locks": {"all": False}},
            "window_lock": {"locked": {"all": False}},
        }
        self.server.statuses["/vehicle/hvac"] = 500
        result, calls = self.run_course_check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("FAIL:", result.stderr)
        self.assertIn("HTTP Error 500", result.stderr)
        self.assertNotIn("ALL SELECTED COURSES PASSED", result.stdout)
        self.assertIn("-s qa-device shell run-as com.firstride.qa39 --user 10 rm -f files/real_signals", calls)
        self.assertFalse(any(call.startswith("exec-out") for call in calls))

    def test_course_check_state_error_never_changes_vehicle(self):
        self.server.statuses["/vehicle/state"] = 500
        result, _ = self.run_course_check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("FAIL:", result.stderr)
        self.assertIn(("GET", "/vehicle/state"), self.server.calls)
        self.assertFalse(any(method == "PUT" for method, _ in self.server.calls))
