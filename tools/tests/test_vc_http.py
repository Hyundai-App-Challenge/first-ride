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


ROOT = Path(__file__).resolve().parents[2]
BASH = os.environ.get("BASH_EXE") or shutil.which("bash")


class Handler(http.server.BaseHTTPRequestHandler):
    def respond(self):
        path = self.path.split("?")[0]
        self.server.calls.append((self.command, path))
        self.send_response(self.server.statuses.get(path, 200))
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"hvac":{"ac":false}}')

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

    def test_course_check_stops_on_injection_error(self):
        self.server.statuses["/vehicle/gear"] = 500
        env = dict(os.environ, ADB=self.adb.as_posix(), VC_PORT=str(self.server.server_port))
        env.pop("MSYS_NO_PATHCONV", None)
        result = subprocess.run(
            [BASH, "tools/check-crs01.sh"], cwd=ROOT, env=env,
            capture_output=True, text=True, encoding="utf-8", timeout=10,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("FAIL VC", result.stderr)
        self.assertNotIn("모두 통과", result.stdout)
