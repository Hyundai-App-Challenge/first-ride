"""아이오닉5 운전석 사진 실습을 로컬 Pleos Virtual Console에 연결한다. 외부 의존성 없음."""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import threading
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / 'app/src/main/assets/panels/ioniq5-2025'
WEB = ROOT / 'tools/interior-simulator'
PANEL = json.loads((MODEL / 'panel.json').read_text(encoding='utf-8'))
CONTROLS = {control['id']: control for control in PANEL['controls']}
DOMAINS = ('hvac', 'lights', 'body', 'window_lock')


def public_state(state):
    return {**{key: state[key] for key in DOMAINS},
            'parked': state['gear']['actual']['value'] == 'Park'}


def control_value(state, control):
    value = state[control.get('domain', 'hvac')]
    for key in control['state']:
        value = value[key]
    return value


class Bridge:
    def __init__(self, port=8088):
        self.base = f'http://127.0.0.1:{port}'
        self.lock = threading.Lock()

    def request(self, method, path, **values):
        query = urlencode({key: str(value).lower() if isinstance(value, bool) else value
                           for key, value in values.items()})
        url = self.base + path + ('?' + query if query else '')
        with urlopen(Request(url, method=method), timeout=4) as response:
            body = response.read()
        try:
            return json.loads(body) if body else None
        except ValueError as exc:
            raise RuntimeError('VC 응답 형식이 올바르지 않습니다') from exc

    def read(self):
        # VC 2.1.0은 활성 브라우저 세션이 있어야 SDK에 값을 전달한다(tools/vc.sh와 동일).
        self.request('POST', '/browser/session', client='firstride-interior')
        state = self.request('GET', '/vehicle/state')
        return public_state(state)

    def state(self):
        with self.lock:
            return self.read()

    def change(self, payload):
        if not isinstance(payload, dict) or set(payload) - {'control', 'value'}:
            raise ValueError('조작 이름과 선택 값만 보낼 수 있어요.')
        control = CONTROLS.get(payload.get('control')) if isinstance(payload.get('control'), str) else None
        if control is None:
            raise ValueError('지원하지 않는 조작이에요.')
        if control['kind'] == 'choice':
            value = payload.get('value')
            if not any(type(value) is type(choice['value']) and value == choice['value']
                       for choice in control['choices']):
                raise ValueError('이 조작에서 제공하는 값 중에서 선택해 주세요.')
        elif 'value' in payload:
            raise ValueError('이 버튼은 선택 값을 받지 않아요.')
        with self.lock:
            state = self.read()
            if not state['parked']:
                raise ValueError('기어를 P로 바꾼 뒤 조작해 주세요.')
            current = control_value(state, control)
            if control['kind'] in ('toggle', 'set'):
                if type(current) is not bool:
                    raise ValueError('현재 차량 상태를 확인할 수 없어요.')
                value = not current if control['kind'] == 'toggle' else control['value']
            elif control['kind'] == 'step':
                if type(current) not in (int, float) or not control['min'] <= current <= control['max']:
                    raise ValueError('현재 차량 상태를 확인할 수 없어요.')
                value = max(control['min'], min(control['max'], current + control['delta']))
            self.request('PUT', '/vehicle/' + control.get('endpoint', 'hvac'),
                         **{**control.get('params', {}), control['field']: value})
            # 표시 상태는 낙관적으로 바꾸지 않고 VC에서 다시 읽는다. 앱 완료는 SDK가 판정한다.
            confirmed = self.request('GET', '/vehicle/state')
            actual = control_value(confirmed, control)
            if actual != value or (type(actual) is bool) != (type(value) is bool):
                raise RuntimeError('변경 결과를 확인하지 못했어요. 현재 상태를 다시 확인해 주세요.')
            return public_state(confirmed)


class Handler(BaseHTTPRequestHandler):
    def respond(self, status, body, mime='application/json; charset=utf-8'):
        if not isinstance(body, bytes):
            body = json.dumps(body, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', mime)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'")
        self.end_headers()
        self.wfile.write(body)

    def allowed(self):
        hosts = {f'127.0.0.1:{self.server.server_port}', f'localhost:{self.server.server_port}'}
        host = self.headers.get('Host')
        origin = self.headers.get('Origin')
        if (host not in hosts or (origin is not None and origin != f'http://{host}')
                or self.headers.get('Sec-Fetch-Site') == 'cross-site'):
            self.respond(403, {'error': '이 PC의 실습 화면에서만 요청할 수 있어요.'})
            return False
        return True

    def api(self, action):
        try:
            self.respond(200, action())
        except ValueError as exc:
            self.respond(400, {'error': str(exc)})
        except (HTTPError, URLError, TimeoutError, OSError, KeyError, TypeError, RuntimeError):
            self.respond(502, {'error': '차량과 연결할 수 없어요. 에뮬레이터와 VC 연결을 확인해 주세요.'})

    def do_GET(self):
        if not self.allowed():
            return
        path = urlsplit(self.path).path
        if path == '/api/state':
            self.api(self.server.bridge.state)
        elif path == '/api/panel':
            self.respond(200, PANEL)
        else:
            files = {'/': (WEB / 'index.html', 'text/html; charset=utf-8'),
                     '/app.js': (WEB / 'app.js', 'text/javascript; charset=utf-8'),
                     '/style.css': (WEB / 'style.css', 'text/css; charset=utf-8')}
            for view in PANEL['views']:
                photo = view['photo']
                files['/' + photo] = (MODEL / photo, 'image/png' if photo.endswith('.png') else 'image/jpeg')
            if path not in files:
                self.respond(404, {'error': '페이지를 찾을 수 없어요.'})
                return
            file, mime = files[path]
            self.respond(200, file.read_bytes(), mime)

    def do_POST(self):
        if not self.allowed():
            return
        if self.path != '/api/control':
            self.respond(404, {'error': '지원하지 않는 요청이에요.'})
            return
        if self.headers.get_content_type() != 'application/json':
            self.respond(415, {'error': 'JSON 요청만 받을 수 있어요.'})
            return
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= 1024:
                raise ValueError()
            payload = json.loads(self.rfile.read(length))
        except (ValueError, UnicodeDecodeError):
            self.respond(400, {'error': '요청 형식을 확인해 주세요.'})
            return
        self.api(lambda: self.server.bridge.change(payload))

    def log_message(self, *args):
        pass


def server(port=8090, vc_port=8088):
    result = ThreadingHTTPServer(('127.0.0.1', port), Handler)
    result.daemon_threads = True
    result.bridge = Bridge(vc_port)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8090)
    parser.add_argument('--vc-port', type=int, default=8088)
    args = parser.parse_args()
    if not all(1 <= port <= 65535 for port in (args.port, args.vc_port)):
        parser.error('포트는 1~65535 범위여야 합니다.')
    with server(args.port, args.vc_port) as app:
        print(f'아이오닉5 실내 실습: http://127.0.0.1:{args.port}/ (종료: Ctrl+C)', flush=True)
        try:
            app.serve_forever()
        except KeyboardInterrupt:
            pass
