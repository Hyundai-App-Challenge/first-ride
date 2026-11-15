"""통합 검증 도구의 대상 선택·중간 실패·원상 복원을 차량 연결 없이 확인한다."""
from contextlib import redirect_stderr, redirect_stdout
from copy import deepcopy
import http.server
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import threading
import unittest
from unittest.mock import patch
import urllib.error

SPEC = importlib.util.spec_from_file_location("course_checks", Path(__file__).resolve().parents[1] / "check-courses.py")
QA = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(QA)


class FakeDevice:
    def __init__(self, real=False):
        self.mode = real
        self.modes = []
        self.stops = 0

    def real_mode(self):
        return self.mode

    def set_mode(self, real):
        self.modes.append(real)
        self.mode = real

    def stop(self):
        self.stops += 1


class FakeVC:
    def __init__(self):
        self.original = dict(power=False, ac=True, recirc=True, front_defroster=False,
                             rear_defroster=True, fan_speed=7, driver_direction=2,
                             headlamp_mode="LowBeam", turn_signal_mode="BlinkLeft",
                             front_wiper="High", window_fl=37, doors_locked=True, window_locked=True)
        self.values = dict(self.original)
        self.snapshot = {
            "gear": {"actual": {"value": "Park"}},
            "hvac": {"power_on": False, "ac_on": True, "recirc_on": True,
                     "front_defroster_on": False, "rear_defroster_on": True, "fan_speed": 7,
                     "driver": {"fan_direction": 2}},
            "lights": {"headlamp_mode": "LowBeam", "turn_signal_mode": "BlinkLeft"},
            "body": {"wipers": {"front": {"mode": "High"}}, "windows": {"front_left": 37},
                     "door_locks": {"all": True, "front_left": True}},
            "window_lock": {"locked": {"all": True, "front_left": True}},
        }
        self.calls = []
        self.fail_endpoint = None

    def state(self):
        return deepcopy(self.snapshot)

    def set(self, endpoint, **fields):
        self.calls.append(endpoint)
        if endpoint == self.fail_endpoint:
            raise RuntimeError("HTTP 503")
        if endpoint == "lights":
            self.values["headlamp_mode"] = fields["value"]
        elif endpoint == "turn-signal":
            self.values["turn_signal_mode"] = fields["mode"]
        elif endpoint == "window-lock":
            self.values["window_locked"] = fields["locked"]
        else:
            self.values.update(fields)


class StateRecoveryTest(unittest.TestCase):
    def test_mid_course_failure_restores_every_control_and_original_fake_mode(self):
        device, vc = FakeDevice(), FakeVC()
        suite = QA.CourseSuite(device, vc)
        def fail(_):
            vc.values = {key: "changed" for key in vc.values}
            raise RuntimeError("SDK check failed")
        with patch.object(suite, "course", side_effect=fail):
            with self.assertRaisesRegex(RuntimeError, "SDK check failed"):
                suite.run(["CRS-01"])
        self.assertEqual(vc.values, vc.original)
        self.assertEqual(device.modes, [True, False])
        self.assertFalse(device.mode)

    def test_success_keeps_original_real_mode_and_restores_controls(self):
        device, vc = FakeDevice(real=True), FakeVC()
        suite = QA.CourseSuite(device, vc)
        with patch.object(suite, "course", side_effect=lambda _: vc.values.update(fan_speed=3)), redirect_stdout(io.StringIO()):
            suite.run(["CRS-02"])
        self.assertEqual(vc.values, vc.original)
        self.assertEqual(device.modes, [True, True])
        self.assertTrue(device.mode)

    def test_one_restore_failure_does_not_skip_other_domains_or_mode_restore(self):
        device, vc = FakeDevice(), FakeVC()
        suite = QA.CourseSuite(device, vc)
        def fail(_):
            vc.values = {key: "changed" for key in vc.values}
            vc.fail_endpoint = "hvac"
            raise RuntimeError("SDK check failed")
        with patch.object(suite, "course", side_effect=fail):
            with self.assertRaisesRegex(RuntimeError, "restore failed: hvac"):
                suite.run(["CRS-01"])
        for key in ("headlamp_mode", "turn_signal_mode", "front_wiper", "window_fl", "doors_locked", "window_locked"):
            self.assertEqual(vc.values[key], vc.original[key])
        self.assertFalse(device.mode)
        self.assertIn("window-lock", vc.calls)

    def test_interrupt_also_restores_state(self):
        device, vc = FakeDevice(), FakeVC()
        suite = QA.CourseSuite(device, vc)
        with patch.object(suite, "course", side_effect=KeyboardInterrupt):
            with self.assertRaises(RuntimeError):
                suite.run(["CRS-01"])
        self.assertEqual(vc.values, vc.original)
        self.assertFalse(device.mode)

    def test_drive_or_mixed_locks_never_start_or_mutate_controls(self):
        for state in ("drive", "doors", "windows"):
            with self.subTest(state=state):
                device, vc = FakeDevice(), FakeVC()
                if state == "drive":
                    vc.snapshot["gear"]["actual"]["value"] = "Drive"
                elif state == "doors":
                    vc.snapshot["body"]["door_locks"]["front_left"] = False
                else:
                    vc.snapshot["window_lock"]["locked"]["front_left"] = False
                with self.assertRaises(RuntimeError):
                    QA.CourseSuite(device, vc).run(["CRS-01"])
                self.assertEqual(vc.calls, [])
                self.assertEqual(device.modes, [])


class TargetTest(unittest.TestCase):
    def test_phase_and_activity_use_only_selected_android_user_process(self):
        device = QA.Device("adb", "one", "com.firstride.qa39")
        device.user = "10"
        def call(*args, **kwargs):
            if args[:2] == ("shell", "ps"):
                output = "USER PID NAME\nu0_a1 111 com.firstride.qa39\nu10_a1 222 com.firstride.qa39\nu10_a2 333 com.other\n"
            else:
                output = "FR-TRACE step 1/2 WAITING\n"
            return subprocess.CompletedProcess(args, 0, output)
        with patch.object(device, "call", side_effect=call) as run:
            device.stop()
            device.foreground()
            device.phase("1/2 WAITING")
            self.assertIn(unittest.mock.call("shell", "am", "force-stop", "--user", "10", device.app_id), run.call_args_list)
            self.assertIn(unittest.mock.call("shell", "am", "start", "--user", "10", "-n", device.component,
                                            "--activity-reorder-to-front"), run.call_args_list)
            self.assertIn(unittest.mock.call("logcat", "-d", "--pid=222", "-s", "FR-TRACE:D"), run.call_args_list)

    def test_signal_change_discards_old_phase_and_never_checks_after_http_failure(self):
        calls = []
        device, vc = QA.Device("adb", "one", "com.firstride.qa39"), FakeVC()
        suite = QA.CourseSuite(device, vc)
        with patch.object(device, "call", side_effect=lambda *args: calls.append("clear")), \
                patch.object(device, "phase", side_effect=lambda _: calls.append("phase")), \
                patch.object(vc, "set", side_effect=lambda *args, **kwargs: calls.append("write")):
            suite.change("body", "1/2 CORRECTION", front_wiper="Auto")
        self.assertEqual(calls, ["clear", "write", "phase"])
        with patch.object(device, "call"), patch.object(vc, "set", side_effect=RuntimeError("HTTP 500")), \
                patch.object(device, "phase") as phase:
            with self.assertRaisesRegex(RuntimeError, "HTTP 500"):
                suite.change("body", "1/2 CORRECTION", front_wiper="Auto")
            phase.assert_not_called()

    def test_missing_or_invalid_app_target_never_runs_adb(self):
        for args in ([], ["--app-id", "com.example;bad"]):
            with patch.dict(os.environ, {}, clear=True), patch.object(QA.subprocess, "run") as run, redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as exit_result:
                    QA.main(args)
                self.assertEqual(exit_result.exception.code, 2)
                run.assert_not_called()

    def test_device_must_be_connected_and_unambiguous(self):
        result = subprocess.CompletedProcess([], 0, "List of devices attached\none\tdevice\ntwo\tdevice\nblocked\tunauthorized\n")
        with patch.object(QA.subprocess, "run", return_value=result):
            self.assertEqual(QA.select_serial("adb", "two"), "two")
            for requested in (None, "blocked", "missing"):
                with self.subTest(requested=requested), self.assertRaises(RuntimeError):
                    QA.select_serial("adb", requested)

    def test_non_debuggable_app_fails_before_vc_or_signal_changes(self):
        def call(_self, *args, **kwargs):
            if args == ("shell", "am", "get-current-user"):
                return subprocess.CompletedProcess(args, 0, "10\n")
            raise subprocess.CalledProcessError(1, args)
        with patch.dict(os.environ, {"ADB": "adb"}, clear=True), patch.object(QA, "select_serial", return_value="one"), \
                patch.object(QA.Device, "call", call), patch.object(QA, "VirtualConsole") as vc, redirect_stderr(io.StringIO()):
            self.assertEqual(QA.main(["--app-id", "com.firstride"]), 1)
            vc.assert_not_called()

    def test_ui_control_with_same_text_is_selected_only_from_target_package(self):
        device = QA.Device("adb", "one", "com.firstride.qa39")
        device.user = "10"
        xml = '<hierarchy><node package="native" text="시작하기" bounds="[0,0][10,10]"/>' \
              '<node package="com.firstride.qa39" text="시작하기" bounds="[100,100][200,200]"/></hierarchy>'
        def call(*args, **kwargs):
            return subprocess.CompletedProcess(args, 0, '<?xml version="1.0"?>' + xml if args[0] == "exec-out" else "")
        with patch.object(device, "call", side_effect=call) as run:
            device.tap("시작하기")
            self.assertEqual(run.call_args.args, ("shell", "input", "tap", "150", "150"))


class Handler(http.server.BaseHTTPRequestHandler):
    def respond(self):
        path = self.path.split("?")[0]
        self.server.calls.append((self.command, path))
        self.send_response(self.server.statuses.get(path, 200))
        self.end_headers()
        self.wfile.write(b"{}")
    do_POST = do_PUT = respond
    def log_message(self, *args):
        pass


class HttpFailureTest(unittest.TestCase):
    def setUp(self):
        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.server.calls, self.server.statuses = [], {}
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.vc = QA.VirtualConsole(self.server.server_port)

    def test_session_http_error_prevents_vehicle_change(self):
        self.server.statuses["/browser/session"] = 503
        with self.assertRaises(urllib.error.HTTPError):
            self.vc.set("hvac", ac=True)
        self.assertEqual(self.server.calls, [("POST", "/browser/session")])

    def test_vehicle_http_error_is_failure(self):
        self.server.statuses["/vehicle/hvac"] = 500
        with self.assertRaises(urllib.error.HTTPError):
            self.vc.set("hvac", ac=True)
        self.assertEqual(self.server.calls[-1], ("PUT", "/vehicle/hvac"))
