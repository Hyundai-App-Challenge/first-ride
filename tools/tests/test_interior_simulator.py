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
                control = next(c for c in sim.CONTROLS.values() if c['field'] == field)
                target = self.server.state['hvac']
                for key in control['state'][:-1]:
                    target = target[key]
                target[control['state'][-1]] = json.loads(values[0])
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
        self.vc.session_status = self.vc.write_status = 200
        self.vc.apply_write = True
        self.vc.state = {'gear': {'actual': {'value': 'Park'}}, 'hvac': {
            'power_on': True, 'ac_on': False, 'recirc_on': False,
            'front_defroster_on': False, 'rear_defroster_on': False, 'fan_speed': 3,
            'driver': {'temp_c': 22.0, 'fan_direction': 1}}}
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
        x, y, w, h = panel['crop']
        self.assertEqual(len(sim.CONTROLS), len(panel['controls']))
        for control in panel['controls']:
            rx, ry, rw, rh = control['rect']
            self.assertTrue(x <= rx < rx + rw <= x + w)
            self.assertTrue(y <= ry < ry + rh <= y + h)
        for course in ('CRS-01', 'CRS-02'):
            text = json.loads((sim.MODEL / f'{course}.json').read_text(encoding='utf-8'))
            for step in text['steps'].values():
                aliases = [step['locatorAsset'], *step.get('conditionLocators', {}).values(), *step.get('misoperationLocators', {}).values()]
                for alias in aliases:
                    self.assertIn(alias, panel['locators'])
                    self.assertTrue(set(panel['locators'][alias]) <= sim.CONTROLS.keys())
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

    def test_invalid_inputs_never_reach_vehicle(self):
        for payload in ([], {'control': 'gear'}, {'control': []}, {'control': 'direction', 'value': True},
                        {'control': 'direction', 'value': 3}, {'control': 'ac', 'value': True},
                        {'control': 'ac', 'field': 'gear'}):
            with self.subTest(payload=payload):
                self.assertEqual(self.call('POST', '/api/control', payload)[0], 400)
        self.assertEqual(self.vc.calls, [])

    def test_non_park_never_writes(self):
        self.vc.state['gear']['actual']['value'] = 'Drive'
        self.assertEqual(self.call('POST', '/api/control', {'control': 'ac'})[0], 400)
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
