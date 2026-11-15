"""실제 로컬 HTTP로 사진 계약·P 게이트·입력 검증·VC 쓰기/확인 실패를 검증한다."""
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import json
from pathlib import Path
import threading
import unittest
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlsplit
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('interior_simulator', ROOT / 'tools/interior_simulator.py')
sim = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sim)


class FakeVC(BaseHTTPRequestHandler):
    def handle_request(self):
        url = urlsplit(self.path)
        self.server.calls.append((self.command, url.path))
        if self.command == 'PUT' and self.server.write_status == 200 and self.server.apply_write:
            for field, values in parse_qs(url.query).items():
                control = next((c for c in sim.CONTROLS.values() if c['field'] == field
                                and '/vehicle/' + c.get('endpoint', 'hvac') == url.path), None)
                if control is None:
                    continue  # feature/area는 해당 쓰기의 고정 매개변수다.
                target = self.server.state[control.get('domain', 'hvac')]
                for key in control['state'][:-1]:
                    target = target[key]
                try:
                    value = json.loads(values[0])
                except ValueError:
                    value = values[0]
                target[control['state'][-1]] = value
            self.server.writes.append((url.path, parse_qs(url.query)))
        status = self.server.session_status if url.path == '/browser/session' else self.server.write_status if self.command == 'PUT' else 200
        self.send_response(status)
        self.end_headers()
        self.wfile.write(json.dumps(self.server.state if url.path == '/vehicle/state' else {}).encode())

    do_POST = do_GET = do_PUT = handle_request

    def log_message(self, *args):
        pass


class InteriorSimulatorTest(unittest.TestCase):
    def setUp(self):
        self.vc = ThreadingHTTPServer(('127.0.0.1', 0), FakeVC)
        self.vc.calls = []
        self.vc.writes = []
        self.vc.session_status = self.vc.write_status = 200
        self.vc.apply_write = True
        self.vc.state = {'gear': {'actual': {'value': 'Park'}}, 'hvac': {
            'power_on': True, 'ac_on': False, 'recirc_on': False,
            'front_defroster_on': False, 'rear_defroster_on': False, 'fan_speed': 3,
            'driver': {'temp_c': 22.0, 'fan_direction': 1}},
            'lights': {'headlamp_mode': 'Off', 'turn_signal_mode': 'Off'},
            'body': {'wipers': {'front': {'mode': 'Off'}}, 'windows': {'front_left': 0}, 'door_locks': {'all': False}},
            'window_lock': {'locked': {'all': False}}}
        self.app = sim.server(0, self.vc.server_port)
        for server in (self.vc, self.app):
            threading.Thread(target=server.serve_forever, daemon=True).start()
            self.addCleanup(server.server_close)
            self.addCleanup(server.shutdown)
        self.base = f'http://127.0.0.1:{self.app.server_port}'

    def call(self, method, path, payload=None, headers=None):
        options = {'Content-Type': 'application/json', **(headers or {})}
        body = None if payload is None else json.dumps(payload).encode()
        request = Request(self.base + path, data=body, method=method, headers=options)
        try:
            response = urlopen(request, timeout=5)
        except HTTPError as exc:
            response = exc
        with response:
            raw = response.read()
            return response.status, json.loads(raw) if response.headers.get_content_type() == 'application/json' else raw

    def test_photo_contract_and_app_aliases_share_valid_regions(self):
        panel = sim.PANEL
        self.assertTrue((sim.MODEL / panel['photo']).is_file())
        self.assertEqual(len(sim.CONTROLS), len(panel['controls']))
        views = {view['id']: view for view in panel['views']}
        for view in views.values():
            self.assertTrue((sim.MODEL / view['photo']).is_file())
            self.assertEqual(self.call('GET', '/' + view['photo'])[0], 200)
            for link in view.get('links', []):
                self.assertIn(link['view'], views)
            if 'parent' in view:
                self.assertIn(view['parent'], views)
                self.assertNotEqual(view['parent'], view['id'])
        for control in panel['controls']:
            x, y, w, h = views[control['view']]['crop']
            rx, ry, rw, rh = control['rect']
            self.assertTrue(x <= rx < rx + rw <= x + w)
            self.assertTrue(y <= ry < ry + rh <= y + h)
            if 'interaction' in control:
                centres = []
                for choice in control['choices']:
                    cx, cy, cw, ch = choice['rect']
                    self.assertTrue(x <= cx < cx + cw <= x + w)
                    self.assertTrue(y <= cy < cy + ch <= y + h)
                    centres.append(cy + ch / 2)
                self.assertEqual(centres, sorted(centres, reverse=True), control['id'])
        for course in (f'CRS-0{i}' for i in range(1, 7)):
            text = json.loads((sim.MODEL / f'{course}.json').read_text(encoding='utf-8'))
            for step in text['steps'].values():
                aliases = [step['locatorAsset'], *step.get('conditionLocators', {}).values(), *step.get('misoperationLocators', {}).values()]
                for alias in aliases:
                    self.assertIn(alias, panel['locators'])
                    self.assertTrue(set(panel['locators'][alias]) <= sim.CONTROLS.keys())
                    self.assertLessEqual(len({sim.CONTROLS[c]['view'] for c in panel['locators'][alias]}), 1)
        self.assertEqual(self.call('GET', '/api/panel'), (200, panel))

    def test_valid_toggle_and_direction_read_back_vehicle_state(self):
        status, result = self.call('POST', '/api/control', {'control': 'front'})
        self.assertEqual(status, 200)
        self.assertTrue(result['hvac']['front_defroster_on'])
        status, result = self.call('POST', '/api/control', {'control': 'direction', 'value': 4})
        self.assertEqual(status, 200)
        self.assertEqual(result['hvac']['driver']['fan_direction'], 4)

    def test_concurrent_toggles_are_serialized(self):
        with ThreadPoolExecutor(2) as pool:
            results = list(pool.map(lambda _: self.call('POST', '/api/control', {'control': 'ac'}), range(2)))
        self.assertTrue(all(status == 200 for status, _ in results))
        self.assertFalse(self.vc.state['hvac']['ac_on'])

    def test_lights_turns_wipers_windows_and_locks_confirm_correct_domains(self):
        cases = [('headlights', 'Position', 'lights', ['headlamp_mode']),
                 ('headlights', 'LowBeam', 'lights', ['headlamp_mode']),
                 ('headlights', 'Auto', 'lights', ['headlamp_mode']),
                 ('headlights', 'Off', 'lights', ['headlamp_mode']),
                 ('turn', 'BlinkLeft', 'lights', ['turn_signal_mode']),
                 ('turn', 'BlinkRight', 'lights', ['turn_signal_mode']),
                 ('turn', 'Off', 'lights', ['turn_signal_mode']),
                 ('wiper', 'Low', 'body', ['wipers', 'front', 'mode']),
                 ('wiper', 'High', 'body', ['wipers', 'front', 'mode']),
                 ('wiper', 'Auto', 'body', ['wipers', 'front', 'mode']),
                 ('wiper', 'Off', 'body', ['wipers', 'front', 'mode']),
                 ('window-down', None, 'body', ['windows', 'front_left']),
                 ('window-up', None, 'body', ['windows', 'front_left']),
                 ('window-lock', None, 'window_lock', ['locked', 'all']),
                 ('door-lock', None, 'body', ['door_locks', 'all']),
                 ('door-unlock', None, 'body', ['door_locks', 'all'])]
        for control, choice, domain, path in cases:
            payload = {'control': control}
            if choice is not None:
                payload['value'] = choice
            status, result = self.call('POST', '/api/control', payload)
            self.assertEqual(status, 200, control)
            actual = result[domain]
            for key in path:
                actual = actual[key]
            expected = choice if choice is not None else {'window-down': 10, 'window-up': 0,
                        'window-lock': True, 'door-lock': True, 'door-unlock': False}[control]
            self.assertEqual(actual, expected, control)
        self.assertEqual(self.vc.writes[0], ('/vehicle/lights', {'feature': ['headlamp_mode'], 'value': ['Position']}))
        self.assertIn(('/vehicle/window-lock', {'area': ['all'], 'locked': ['true']}), self.vc.writes)

    def test_off_button_does_not_turn_power_back_on(self):
        for _ in range(2):
            status, result = self.call('POST', '/api/control', {'control': 'power'})
            self.assertEqual(status, 200)
            self.assertFalse(result['hvac']['power_on'])
        status, result = self.call('POST', '/api/control', {'control': 'power-on'})
        self.assertEqual(status, 200)
        self.assertTrue(result['hvac']['power_on'])

    def test_numeric_steps_are_bounded(self):
        self.vc.state['hvac']['fan_speed'] = 8
        status, result = self.call('POST', '/api/control', {'control': 'fan-up'})
        self.assertEqual((status, result['hvac']['fan_speed']), (200, 8))
        self.vc.state['hvac']['driver']['temp_c'] = 16
        status, result = self.call('POST', '/api/control', {'control': 'temp-down'})
        self.assertEqual((status, result['hvac']['driver']['temp_c']), (200, 16))
        self.vc.state['body']['windows']['front_left'] = 100
        self.assertEqual(self.call('POST', '/api/control', {'control': 'window-down'})[1]['body']['windows']['front_left'], 100)
        self.vc.state['body']['windows']['front_left'] = 0
        self.assertEqual(self.call('POST', '/api/control', {'control': 'window-up'})[1]['body']['windows']['front_left'], 0)

    def test_invalid_inputs_never_reach_vehicle(self):
        for payload in ([], {'control': 'gear'}, {'control': []}, {'control': 'direction', 'value': True},
                        {'control': 'direction', 'value': 3}, {'control': 'ac', 'value': True},
                        {'control': 'ac', 'field': 'gear'}, {'control': 'headlights', 'value': 'HighBeam'},
                        {'control': 'headlights', 'value': True}, {'control': 'wiper', 'value': 'LOW'},
                        {'control': 'turn', 'value': 'Emergency'}, {'control': 'door-lock', 'value': False}):
            with self.subTest(payload=payload):
                self.assertEqual(self.call('POST', '/api/control', payload)[0], 400)
        self.assertEqual(self.vc.calls, [])

    def test_non_park_never_writes(self):
        self.vc.state['gear']['actual']['value'] = 'Drive'
        for payload in ({'control': 'ac'}, {'control': 'headlights', 'value': 'LowBeam'},
                        {'control': 'wiper', 'value': 'Low'}, {'control': 'window-down'}, {'control': 'door-lock'}):
            self.assertEqual(self.call('POST', '/api/control', payload)[0], 400)
        self.assertFalse(any(method == 'PUT' for method, _ in self.vc.calls))

    def test_failed_session_or_write_is_not_success(self):
        self.vc.session_status = 500
        self.assertEqual(self.call('POST', '/api/control', {'control': 'ac'})[0], 502)
        self.assertEqual(self.vc.calls, [('POST', '/browser/session')])
        self.vc.session_status = 200
        self.vc.write_status = 500
        self.assertEqual(self.call('POST', '/api/control', {'control': 'ac'})[0], 502)

    def test_unapplied_write_is_not_success(self):
        self.vc.apply_write = False
        self.assertEqual(self.call('POST', '/api/control', {'control': 'ac'})[0], 502)
        self.assertEqual(self.call('POST', '/api/control', {'control': 'headlights', 'value': 'Position'})[0], 502)

    def test_invalid_current_numeric_or_boolean_state_never_writes(self):
        self.vc.state['body']['windows']['front_left'] = True
        self.assertEqual(self.call('POST', '/api/control', {'control': 'window-down'})[0], 400)
        self.vc.state['body']['door_locks']['all'] = 'unknown'
        self.assertEqual(self.call('POST', '/api/control', {'control': 'door-lock'})[0], 400)
        self.assertFalse(self.vc.writes)

    def test_cross_origin_and_rebinding_hosts_cannot_write(self):
        for headers in ({'Origin': 'https://untrusted.example'}, {'Host': 'untrusted.example'}, {'Sec-Fetch-Site': 'cross-site'}):
            self.assertEqual(self.call('POST', '/api/control', {'control': 'ac'}, headers)[0], 403)
        self.assertEqual(self.vc.calls, [])

    def test_static_routes_never_serve_project_files(self):
        for path in ('/../local.properties', '/%2e%2e/.git/config', '/panel.json', '/missing'):
            self.assertEqual(self.call('GET', path)[0], 404)
        self.assertEqual(self.call('GET', '/assets/climate.jpg')[0], 200)
        self.assertEqual(self.call('GET', '/')[0], 200)


if __name__ == '__main__':
    unittest.main()
