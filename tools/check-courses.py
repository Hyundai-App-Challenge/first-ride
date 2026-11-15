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

    def process_id(self):
        if self._pid is None:
            def find_process():
                processes = self.call("shell", "ps", "-A", "-o", "USER,PID,NAME").stdout
                for line in processes.splitlines():
                    parts = line.split()
                    if len(parts) == 3 and parts[0].startswith(f"u{self.user}_") and parts[2] == self.app_id:
                        return parts[1]
                return None
            self._pid = wait_for(find_process, "target app process")
        return self._pid

    def current_phase(self):
        trace = self.call("logcat", "-d", "--pid=" + self.process_id(), "-s", "FR-TRACE:D").stdout
        phases = re.findall(r"step \d+/\d+ [A-Z_]+|screen CourseDone|follow-up [^\r\n]+|drive [A-Z_]+", trace)
        return phases[-1] if phases else ""

    def phase(self, expected):
        wait_for(lambda: expected in self.current_phase(), expected)

    def wait_text(self, text):
        wait_for(lambda: any(n.get("text") == text for n in self.nodes()), "app text " + text)

    def wait_absent(self, text):
        # 빈 dump/다른 화면을 텍스트가 사라진 성공으로 보지 않는다.
        def absent():
            nodes = self.nodes()
            return bool(nodes) and all(n.get("text") != text for n in nodes)
        wait_for(absent, "app text removed " + text)

    def background(self):
        self.call("shell", "am", "start", "--user", self.user, "-a", "android.settings.SETTINGS")
        wait_for(lambda: self.subscriptions() == 0, "background subscriptions 0")

    def subscriptions(self):
        trace = self.call("logcat", "-d", "--pid=" + self.process_id(), "-s", "FR-SIGNALS:D").stdout
        counts = re.findall(r"active=(\d+)", trace)
        return int(counts[-1]) if counts else None

    def progress_hash(self):
        result = self.call("shell", "run-as", self.app_id, "--user", self.user,
                           "sha256sum", "files/datastore/progress.preferences_pb").stdout
        parts = result.split()
        if not parts or not re.fullmatch(r"[0-9a-f]{64}", parts[0]):
            raise RuntimeError("Could not verify saved progress")
        return parts[0]


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


def restore_commands(state, follow_ups=False, recovery=False):
    hvac, body, lights = state["hvac"], state["body"], state["lights"]
    locks = state["window_lock"]["locked"]
    if state["gear"]["actual"]["value"] != "Park":
        raise RuntimeError("Park is required before running course checks")
    # VC의 all 제어로 정확히 복원할 수 없는 혼합 잠금 상태에서는 시작하지 않는다.
    if len(set(locks.values())) != 1 or len(set(body["door_locks"].values())) != 1:
        raise RuntimeError("Mixed lock states cannot be restored through VC all controls")
    commands = [
        ("turn-signal", {"mode": lights["turn_signal_mode"]}),
        ("hvac", {"power": hvac["power_on"], "ac": hvac["ac_on"], "recirc": hvac["recirc_on"],
                  "front_defroster": hvac["front_defroster_on"], "rear_defroster": hvac["rear_defroster_on"],
                  "fan_speed": hvac["fan_speed"], "driver_direction": hvac["driver"]["fan_direction"]}),
        ("lights", {"feature": "headlamp_mode", "value": lights["headlamp_mode"]}),
        ("body", {"front_wiper": body["wipers"]["front"]["mode"], "window_fl": body["windows"]["front_left"],
                  "doors_locked": body["door_locks"]["all"]}),
        ("window-lock", {"area": "all", "locked": locks["all"]}),
    ]
    if follow_ups or recovery:
        commands[1][1]["driver_temp"] = hvac["driver"]["temp_c"]
        commands.insert(0, ("gear", {"gear": state["gear"]["actual"]["value"]}))
    if recovery:
        next(fields for endpoint, fields in commands if endpoint == "body")["cabin_light"] = body["cabin_light"]["on"]
    return commands


class CourseSuite:
    def __init__(self, device, vc):
        self.device, self.vc = device, vc

    def open(self, course_id, action):
        name = json.loads((ROOT / "app/src/main/assets/courses" / (course_id + ".json")).read_text(encoding="utf-8"))["name"]
        self.device.call("logcat", "-c")
        self.device.tap(name)
        self.device.tap(action)

    def start(self, course_id):
        self.open(course_id, "시작하기")

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

    def _next_offer(self, include_max=True):
        ids = ("F1", "F2", "F3") if include_max else ("F2", "F3")
        allowed = {f"follow-up CRS-01-{step} WAITING" for step in ids}
        if include_max:
            allowed.add("follow-up CRS-01-F1 UNVERIFIABLE")
        allowed.add("follow-up Exhausted")
        return wait_for(lambda: (phase if (phase := self.device.current_phase()) in allowed else None), "next follow-up")

    def _after_max_defrost(self):
        phase = self._next_offer()
        if phase.startswith("follow-up CRS-01-F1 "):
            # v2.1.0의 공개 VC catalog에는 최대 제상 쓰기 제어가 없다.
            print("SKIP CRS-01-F1 completion: no max-defrost write control in VC 2.1.0", flush=True)
            self.device.call("logcat", "-c")
            self.device.tap("건너뛰기")
            phase = self._next_offer(include_max=False)
        return phase

    def _follow_up_home(self):
        self.device.foreground()
        self.device.wait_text("설정")
        if any(n.get("text") == "이어서 하기" for n in self.device.nodes()):
            raise RuntimeError("Interrupted follow-up must not offer resume")

    def follow_ups(self):
        d, v = self.device, self.vc
        before = d.progress_hash()
        limits = v.state()["hvac"]["limits"]
        fan_min, fan_max = limits["fan_speed"]
        temp_min, temp_max = limits["temperature_c"]
        if fan_min >= fan_max or temp_min >= temp_max:
            raise RuntimeError("Follow-up checks require adjustable fan and temperature ranges")
        fan_below, temp_below = max(fan_min, fan_max - 1), max(temp_min, temp_max - 1)

        def prepare(fan=fan_below, temperature=temp_below, ac=True):
            v.set("hvac", power=True, front_defroster=True, ac=ac, recirc=False,
                  fan_speed=fan, driver_temp=temperature)

        # 기본 조합 복구 중 유지 조건이 바뀌면 완료하지 않는다.
        prepare(ac=False)
        self.open("CRS-01", "김이 잘 안 걷힐 때")
        d.phase("follow-up CRS-01-S1-restore WAITING")
        self.change("hvac", "follow-up CRS-01-S1-restore WAITING", front_defroster=False)
        self.change("hvac", "follow-up CRS-01-S1-restore PARTIAL", ac=True)
        d.call("logcat", "-c"); v.set("hvac", front_defroster=True)
        phase = self._after_max_defrost()
        if phase != "follow-up CRS-01-F2 WAITING":
            raise RuntimeError("Expected fan follow-up after restoring all base conditions")
        self.change("hvac", "follow-up Asking", fan_speed=fan_max)
        d.tap("아직이요"); d.phase("follow-up CRS-01-F3 WAITING")
        self.change("hvac", "follow-up Asking", driver_temp=temp_max)
        d.tap("아직이요"); d.phase("follow-up Exhausted"); d.tap("홈으로")

        # 이미 최대인 풍량은 다시 제안하지 않고 온도 조치 뒤 해결 답으로 끝낸다.
        prepare(fan=fan_max)
        self.open("CRS-01", "김이 잘 안 걷힐 때")
        if self._after_max_defrost() != "follow-up CRS-01-F3 WAITING":
            raise RuntimeError("Already-max fan was offered again")
        self.change("hvac", "follow-up Asking", driver_temp=temp_max)
        d.tap("걷혔어요"); d.phase("follow-up Resolved"); d.tap("홈으로")

        prepare(fan=fan_max, temperature=temp_max)
        self.open("CRS-01", "김이 잘 안 걷힐 때")
        if self._after_max_defrost() != "follow-up Exhausted":
            raise RuntimeError("Already-complete follow-ups should be exhausted")
        d.tap("홈으로")

        # 후속 조치는 백그라운드·주행 중단 후 이어서 하기를 제안하지 않는다.
        prepare()
        self.open("CRS-01", "김이 잘 안 걷힐 때"); self._next_offer()
        d.call("shell", "am", "start", "--user", d.user, "-a", "android.settings.SETTINGS")
        wait_for(lambda: d.subscriptions() == 0, "background follow-up subscriptions 0")
        self._follow_up_home()
        self.open("CRS-01", "김이 잘 안 걷힐 때"); self._next_offer()
        self.change("gear", "drive RESTRICTED", gear="Drive")
        wait_for(lambda: d.subscriptions() == 0, "driving follow-up subscriptions 0")
        self.change("gear", "drive PARKED", gear="Park"); self._follow_up_home()
        if d.progress_hash() != before:
            raise RuntimeError("Follow-ups changed saved progress")
        print("PASS CRS-01 follow-ups: restore, hold, fan, temperature, answers, interruption, progress unchanged", flush=True)

    def recovery(self):
        d, v = self.device, self.vc
        v.set("hvac", power=True, rear_defroster=False, fan_speed=3, driver_direction=1)
        before = d.progress_hash()
        self.start("CRS-02"); d.phase("1/3 WAITING")
        self.change("hvac", "2/3 WAITING", rear_defroster=True)
        d.background()
        v.set("hvac", fan_speed=4)
        if d.progress_hash() != before:
            raise RuntimeError("Background interruption changed saved progress")
        d.wait_text("실습을 이어서 할까요?")
        d.call("logcat", "-c"); d.tap("이어서 하기"); d.phase("2/3 WAITING")
        self.change("hvac", "3/3 WAITING", fan_speed=5)
        self.change("gear", "drive RESTRICTED", gear="Drive")
        wait_for(lambda: d.subscriptions() == 0, "driving subscriptions 0")
        v.set("hvac", driver_direction=4)
        if d.progress_hash() != before:
            raise RuntimeError("Driving interruption changed saved progress")
        self.change("gear", "drive PARKED", gear="Park")
        d.wait_text("실습을 이어서 할까요?")
        d.call("logcat", "-c"); d.tap("이어서 하기"); d.phase("3/3 ALREADY_AT_TARGET")
        self.change("hvac", "3/3 WAITING", driver_direction=1)
        self.change("hvac", "screen CourseDone", driver_direction=4)

        # 재개 시점을 시작 전 값으로 덮지 않는다. 최초 풍량3까지 직접 되돌려야 목록에서 빠진다.
        labels = ("뒷유리 열선", "풍량", "앞유리 쪽 바람")
        for label in labels:
            d.wait_text("· " + label)
        for field, value, label in (("rear_defroster", False, labels[0]), ("fan_speed", 3, labels[1]),
                                    ("driver_direction", 1, labels[2])):
            v.set("hvac", **{field: value}); d.wait_absent("· " + label)
        d.wait_absent("실습하느라 바뀐 것이 남아 있어요")
        d.background(); v.set("hvac", rear_defroster=True)
        d.wait_text("· 뒷유리 열선")
        v.set("hvac", rear_defroster=False); d.wait_absent("· 뒷유리 열선")
        self.change("gear", "drive RESTRICTED", gear="Drive")
        wait_for(lambda: d.subscriptions() == 0, "hidden restore subscriptions 0")
        v.set("hvac", fan_speed=4)
        self.change("gear", "drive PARKED", gear="Park")
        d.wait_text("· 풍량")
        v.set("hvac", fan_speed=3); d.wait_absent("· 풍량"); d.tap("홈으로")
        d.wait_text("설정")

        completed = d.progress_hash()
        self.start("CRS-02"); d.phase("1/3 WAITING")
        d.tap("건너뛰기"); d.phase("2/3 WAITING")
        d.background(); d.wait_text("실습을 이어서 할까요?"); d.tap("홈으로")
        d.wait_text("설정")
        wait_for(lambda: d.subscriptions() == 0, "dismissed interruption subscriptions 0")
        if d.progress_hash() != completed:
            raise RuntimeError("Dismissed interrupted course changed saved progress")
        self.start("CRS-02"); d.phase("1/3 WAITING")
        d.tap("건너뛰기"); d.phase("2/3 WAITING")
        d.background(); d.wait_text("실습을 이어서 할까요?")
        d.call("logcat", "-c"); d.tap("이어서 하기"); d.phase("2/3 WAITING")
        self.change("hvac", "3/3 WAITING", fan_speed=4)
        self.change("hvac", "screen CourseDone", driver_direction=4)
        d.wait_text("건너뛴 단계가 있어요"); d.tap("홈으로"); d.wait_text("설정")
        if d.progress_hash() != completed:
            raise RuntimeError("Skipped retry downgraded an already-completed course")
        print("PASS recovery: resume choice, fresh targets, original baseline, skip preservation, restore lifecycle", flush=True)
        self.lights_check()

    def lights_check(self):
        d, v = self.device, self.vc
        before = d.progress_hash()
        v.set("lights", feature="headlamp_mode", value="Off")
        v.set("body", cabin_light=False)
        d.tap("소등 확인"); d.wait_text("확인한 등은 모두 꺼져 있어요.")
        v.set("lights", feature="headlamp_mode", value="LowBeam")
        d.wait_text("하향등, 미등이 켜져 있어요.")
        v.set("body", cabin_light=True)
        d.wait_text("하향등, 미등, 실내등이 켜져 있어요.")
        d.background()
        v.set("lights", feature="headlamp_mode", value="Off"); v.set("body", cabin_light=False)
        d.wait_text("확인한 등은 모두 꺼져 있어요.")
        self.change("gear", "drive RESTRICTED", gear="Drive")
        wait_for(lambda: d.subscriptions() == 0, "driving lights subscriptions 0")
        v.set("body", cabin_light=True)
        self.change("gear", "drive PARKED", gear="Park")
        d.wait_text("실내등이 켜져 있어요.")
        d.tap("닫기"); d.wait_text("설정")
        wait_for(lambda: d.subscriptions() == 0, "closed lights subscriptions 0")
        if d.progress_hash() != before:
            raise RuntimeError("Lights check changed saved progress")
        print("PASS lights: actual lamp updates, background/driving refresh, close, progress unchanged", flush=True)

    def run(self, courses, follow_ups=False, recovery=False):
        original_mode = self.device.real_mode()
        restore = restore_commands(self.vc.state(), follow_ups, recovery)
        failure, cleanup_errors = None, []
        try:
            self.device.set_mode(True)
            for course_id in courses:
                try:
                    self.course(course_id)
                    if follow_ups and course_id == "CRS-01":
                        self.follow_ups()
                except Exception as exc:
                    raise RuntimeError(f"{course_id}: {exc}") from exc
                print("PASS " + course_id, flush=True)
            if recovery:
                self.recovery()
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
    parser.add_argument("--follow-ups", action="store_true", help="also check CRS-01 follow-ups and interruptions")
    parser.add_argument("--recovery", action="store_true", help="also check CRS-02 resume, restoration and lights lifecycle")
    args = parser.parse_args(argv)
    if not args.app_id:
        parser.error("Set APP_ID or --app-id explicitly")
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", args.app_id):
        parser.error("Invalid Android applicationId")
    if args.follow_ups and "CRS-01" not in (args.course or COURSES):
        parser.error("--follow-ups requires CRS-01 in the selected courses")
    if args.recovery and "CRS-02" not in (args.course or COURSES):
        parser.error("--recovery requires CRS-02 in the selected courses")
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
            CourseSuite(device, vc).run(args.course or COURSES, follow_ups=args.follow_ups, recovery=args.recovery)
        print("ALL SELECTED COURSES PASSED; ORIGINAL STATE RESTORED")
        return 0
    except (OSError, RuntimeError, ValueError, subprocess.SubprocessError) as exc:
        print("FAIL: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
