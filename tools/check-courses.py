#!/usr/bin/env python3
"""설치된 debug 앱의 CRS-01~06을 VC/실제 SDK로 확인하고 시작 상태를 복원한다."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
COURSES = tuple(f"CRS-{n:02}" for n in range(1, 7))


def wait_for(read, description):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        value = read()
        if value:
            return value
        time.sleep(0.2)
    raise RuntimeError(f"Timed out: {description}")


def select_serial(adb, requested):
    result = subprocess.run([adb, "devices"], check=True, capture_output=True, text=True, timeout=10)
    available = [line.split()[0] for line in result.stdout.splitlines() if re.fullmatch(r"\S+\s+device", line.strip())]
    if requested:
        if requested not in available:
            raise RuntimeError("Selected device is not connected and authorized")
        return requested
    if len(available) != 1:
        raise RuntimeError("Set ANDROID_SERIAL to one connected device from adb devices")
    return available[0]


class Device:
    def __init__(self, adb, serial, app_id):
        self.command = [adb, "-s", serial]
        self.app_id = app_id
        self.component = f"{app_id}/com.firstride.MainActivity"
        self.user = None
        self._pid = None

    def call(self, *args, check=True):
        return subprocess.run(self.command + list(args), check=check, capture_output=True, text=True,
                              encoding="utf-8", timeout=15)

    def preflight(self):
        self.user = self.call("shell", "am", "get-current-user").stdout.strip()
        if not self.user.isdigit():
            raise RuntimeError("Could not determine the current Android user")
        # release 앱에 신호원 표식을 쓰거나 데이터를 초기화하지 않는다.
        try:
            self.call("shell", "run-as", self.app_id, "--user", self.user, "true")
        except subprocess.CalledProcessError as exc:
            raise RuntimeError("Target must be an installed debuggable app for the current Android user") from exc

    def real_mode(self):
        result = self.call("shell", "run-as", self.app_id, "--user", self.user,
                           "test", "-e", "files/real_signals", check=False)
        if result.returncode not in (0, 1) or result.stderr.strip():
            raise RuntimeError("Could not read the debug signal-source marker")
        return result.returncode == 0

    def stop(self):
        self.call("shell", "am", "force-stop", "--user", self.user, self.app_id)
        self._pid = None

    def foreground(self):
        self.call("shell", "am", "start", "--user", self.user, "-n", self.component, "--activity-reorder-to-front")

    def set_mode(self, real):
        args = ("shell", "run-as", self.app_id, "--user", self.user)
        if real:
            self.call(*args, "mkdir", "-p", "files")
            self.call(*args, "touch", "files/real_signals")
        else:
            self.call(*args, "rm", "-f", "files/real_signals")
        self.stop()
        self.foreground()

    def nodes(self):
        self.foreground()
        xml = self.call("exec-out", "uiautomator", "dump", "/dev/tty").stdout
        start, end = xml.find("<?xml"), xml.find("</hierarchy>")
        if start < 0 or end < 0:
            return []
        return [n for n in ET.fromstring(xml[start:end + 12]).iter("node")
                if n.get("package") == self.app_id]

    def tap(self, text):
        def find():
            for node in self.nodes():
                if text not in (node.get("text"), node.get("content-desc")):
                    continue
                bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
                if bounds:
                    x1, y1, x2, y2 = map(int, bounds.groups())
                    if x2 > x1 and y2 > y1:
                        return ((x1 + x2) // 2, (y1 + y2) // 2)
            return None
        x, y = wait_for(find, "app control " + text)
        self.call("shell", "input", "tap", str(x), str(y))

    def phase(self, expected):
        if self._pid is None:
            def find_process():
                processes = self.call("shell", "ps", "-A", "-o", "USER,PID,NAME").stdout
                for line in processes.splitlines():
                    parts = line.split()
                    if len(parts) == 3 and parts[0].startswith(f"u{self.user}_") and parts[2] == self.app_id:
                        return parts[1]
                return None
            self._pid = wait_for(find_process, "target app process")
        def matches():
            trace = self.call("logcat", "-d", "--pid=" + self._pid, "-s", "FR-TRACE:D").stdout
            phases = re.findall(r"step \d+/\d+ [A-Z_]+|screen CourseDone", trace)
            return phases and expected in phases[-1]
        wait_for(matches, expected)


class VirtualConsole:
    def __init__(self, port):
        self.base = f"http://127.0.0.1:{port}"
        self.stopping = threading.Event()
        self.heartbeat = None

    def request(self, method, path, **fields):
        url = self.base + path
        if fields:
            url += "?" + urllib.parse.urlencode({k: str(v).lower() if isinstance(v, bool) else v for k, v in fields.items()})
        with urllib.request.urlopen(urllib.request.Request(url, method=method), timeout=5) as response:
            return response.read()

    def session(self):
        self.request("POST", "/browser/session", client="firstride-course-qa")

    def set(self, endpoint, **fields):
        self.session()
        self.request("PUT", "/vehicle/" + endpoint, **fields)

    def state(self):
        self.session()
        return json.loads(self.request("GET", "/vehicle/state"))

    def __enter__(self):
        self.session()
        def keepalive():
            while not self.stopping.wait(1):
                try:
                    self.session()
                except OSError:
                    pass  # 실제 조회·변경 실패는 호출자에게 전달한다.
        self.heartbeat = threading.Thread(target=keepalive, daemon=True)
        self.heartbeat.start()
        return self

    def __exit__(self, *exc):
        self.stopping.set()
        self.heartbeat.join(timeout=6)


def restore_commands(state):
    hvac, body, lights = state["hvac"], state["body"], state["lights"]
    locks = state["window_lock"]["locked"]
    if state["gear"]["actual"]["value"] != "Park":
        raise RuntimeError("Park is required before running course checks")
    # VC의 all 제어로 정확히 복원할 수 없는 혼합 잠금 상태에서는 시작하지 않는다.
    if len(set(locks.values())) != 1 or len(set(body["door_locks"].values())) != 1:
        raise RuntimeError("Mixed lock states cannot be restored through VC all controls")
    return [
        ("turn-signal", {"mode": lights["turn_signal_mode"]}),
        ("hvac", {"power": hvac["power_on"], "ac": hvac["ac_on"], "recirc": hvac["recirc_on"],
                  "front_defroster": hvac["front_defroster_on"], "rear_defroster": hvac["rear_defroster_on"],
                  "fan_speed": hvac["fan_speed"], "driver_direction": hvac["driver"]["fan_direction"]}),
        ("lights", {"feature": "headlamp_mode", "value": lights["headlamp_mode"]}),
        ("body", {"front_wiper": body["wipers"]["front"]["mode"], "window_fl": body["windows"]["front_left"],
                  "doors_locked": body["door_locks"]["all"]}),
        ("window-lock", {"area": "all", "locked": locks["all"]}),
    ]


class CourseSuite:
    def __init__(self, device, vc):
        self.device, self.vc = device, vc

    def start(self, course_id):
        name = json.loads((ROOT / "app/src/main/assets/courses" / (course_id + ".json")).read_text(encoding="utf-8"))["name"]
        self.device.call("logcat", "-c")
        self.device.tap(name)
        self.device.tap("시작하기")

    def home(self):
        self.device.phase("screen CourseDone")
        self.device.tap("홈으로")

    def change(self, endpoint, expected, **fields):
        # HIGH→AUTO처럼 같은 phase인 변경도 이전 로그를 성공으로 읽지 않는다.
        self.device.call("logcat", "-c")
        self.vc.set(endpoint, **fields)
        self.device.phase(expected)

    def course(self, course_id):
        v, d = self.vc, self.device
        if course_id == "CRS-01":
            v.set("hvac", power=True, ac=False, recirc=False, front_defroster=False)
            self.start(course_id); d.phase("WAITING")
            self.change("hvac", "PARTIAL", front_defroster=True)
            self.change("hvac", "CORRECTION", recirc=True)
            self.change("hvac", "screen CourseDone", recirc=False, ac=True); self.home()
            self.start(course_id); d.phase("ALREADY_AT_TARGET")
            self.change("hvac", "WAITING", front_defroster=False)
            self.change("hvac", "screen CourseDone", front_defroster=True); self.home()
        elif course_id == "CRS-02":
            v.set("hvac", power=True, rear_defroster=False, fan_speed=3, driver_direction=1)
            self.start(course_id); d.phase("1/3 WAITING")
            self.change("hvac", "2/3 WAITING", rear_defroster=True)
            self.change("hvac", "3/3 WAITING", fan_speed=4)
            self.change("hvac", "screen CourseDone", driver_direction=4); self.home()
        elif course_id == "CRS-03":
            v.set("lights", feature="headlamp_mode", value="Off")
            self.start(course_id); d.phase("1/3 WAITING")
            self.change("lights", "2/3 WAITING", feature="headlamp_mode", value="Position")
            self.change("lights", "3/3 WAITING", feature="headlamp_mode", value="LowBeam")
            self.change("lights", "screen CourseDone", feature="headlamp_mode", value="Position"); self.home()
        elif course_id == "CRS-04":
            v.set("body", front_wiper="Off")
            self.start(course_id); d.phase("1/2 WAITING")
            self.change("body", "1/2 CORRECTION", front_wiper="High")
            self.change("body", "1/2 CORRECTION", front_wiper="Auto")
            self.change("body", "2/2 WAITING", front_wiper="Low")
            self.change("body", "screen CourseDone", front_wiper="Off"); self.home()
        elif course_id == "CRS-05":
            v.set("turn-signal", mode="Off")
            self.start(course_id); d.phase("1/2 WAITING")
            self.change("turn-signal", "1/2 CORRECTION", mode="BlinkRight")
            self.change("turn-signal", "2/2 WAITING", mode="BlinkLeft")
            self.change("turn-signal", "screen CourseDone", mode="BlinkRight")
            # 사각지대 카메라가 포커스를 가져간다. 중립 복귀 후 앱 UI를 선택한다.
            v.set("turn-signal", mode="Off"); self.home()
        elif course_id == "CRS-06":
            v.set("body", window_fl=0, doors_locked=False)
            v.set("window-lock", area="all", locked=False)
            self.start(course_id); d.phase("1/3 WAITING")
            self.change("body", "2/3 WAITING", window_fl=10)
            self.change("window-lock", "3/3 WAITING", area="all", locked=True)
            self.change("body", "screen CourseDone", doors_locked=True); self.home()
        else:
            raise RuntimeError("No scenario for " + course_id)

    def run(self, courses):
        original_mode = self.device.real_mode()
        restore = restore_commands(self.vc.state())
        failure, cleanup_errors = None, []
        try:
            self.device.set_mode(True)
            for course_id in courses:
                try:
                    self.course(course_id)
                except Exception as exc:
                    raise RuntimeError(f"{course_id}: {exc}") from exc
                print("PASS " + course_id, flush=True)
        except (Exception, KeyboardInterrupt) as exc:
            failure = exc
        finally:
            try:
                self.device.stop()
            except Exception as exc:
                cleanup_errors.append("stop: " + str(exc))
            for endpoint, fields in restore:
                try:
                    self.vc.set(endpoint, **fields)
                except Exception as exc:
                    cleanup_errors.append(endpoint + ": " + str(exc))
            try:
                self.device.set_mode(original_mode)
            except Exception as exc:
                cleanup_errors.append("signal source: " + str(exc))
        if failure or cleanup_errors:
            detail = [str(failure)] if failure else []
            detail += ["restore failed: " + error for error in cleanup_errors]
            raise RuntimeError("; ".join(detail)) from failure


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app-id", default=os.environ.get("APP_ID"), help="installed debug package (required)")
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--course", action="append", choices=COURSES, help="repeat to select courses; default all six")
    args = parser.parse_args(argv)
    if not args.app_id:
        parser.error("Set APP_ID or --app-id explicitly")
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", args.app_id):
        parser.error("Invalid Android applicationId")
    adb = os.environ.get("ADB") or shutil.which("adb")
    if not adb:
        parser.error("Set ADB to the adb executable")
    try:
        serial = select_serial(adb, args.serial)
        device = Device(adb, serial, args.app_id)
        device.preflight()
        port = int(os.environ.get("VC_PORT", "8088"))
        device.call("forward", f"tcp:{port}", f"tcp:{port}")
        with VirtualConsole(port) as vc:
            CourseSuite(device, vc).run(args.course or COURSES)
        print("ALL SELECTED COURSES PASSED; ORIGINAL STATE RESTORED")
        return 0
    except (OSError, RuntimeError, ValueError, subprocess.SubprocessError) as exc:
        print("FAIL: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
