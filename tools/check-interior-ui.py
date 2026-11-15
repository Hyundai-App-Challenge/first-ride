"""설치된 Chrome의 독립 headless 프로필로 사진 웹의 실제 JS·레이아웃을 검증한다."""
import argparse
from http.server import ThreadingHTTPServer
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import threading

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('sim_tests', ROOT / 'tools/tests/test_interior_simulator.py')
tests = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tests)
sim = tests.sim
SCRIPT = r"""
(async () => {
  const wait = async (predicate, label) => {
    for (let i=0;i<200;i++) { if(predicate()) return; await new Promise(r=>setTimeout(r,50)); }
    throw Error(label);
  };
  const check = (condition, label) => { if(!condition) throw Error(label); };
  const row = label => [...document.querySelectorAll('.state-row')].find(r=>r.querySelector('dt').textContent===label)?.querySelector('dd').textContent;
  try {
    await wait(()=>document.querySelector('#directions button') && !document.querySelector('#directions button').disabled, 'connected controls');
    await wait(()=>document.getElementById('photo').complete, 'photo loaded');
    check(document.getElementById('photo').naturalWidth===1800, 'shared source photograph');
    check(document.documentElement.scrollWidth<=innerWidth, 'page horizontal overflow');
    for(const button of document.querySelectorAll('.hotspot')) {
      const bounds=button.getBoundingClientRect();
      check(bounds.width>=44 && bounds.height>=44, 'touch target too small');
    }
    const front=document.querySelector('.hotspot[aria-label="앞유리 제상"]');
    front.focus(); check(document.activeElement===front, 'keyboard focus');
    front.click(); await wait(()=>row('앞유리 제상')==='켜짐' && !front.disabled, 'photo click did not update state');
    const choices=[...document.querySelectorAll('#directions button')];
    const windshield=choices.find(b=>b.textContent==='앞유리');
    windshield.click(); await wait(()=>row('운전석 바람 방향')==='앞유리' && !windshield.disabled, 'direction selection');
    check(windshield.getAttribute('aria-pressed')==='true', 'selected direction semantics');
    document.querySelector('.hotspot[aria-label="풍량 올리기"]').click();
    await wait(()=>row('풍량')==='4 / 8', 'fan adjustment');
    await fetch('/smoke/drive');
    await wait(()=>choices.every(b=>b.disabled), 'drive gate');
    await fetch('/smoke/park');
    await wait(()=>!windshield.disabled, 'park reconnection');
    await fetch('/smoke/down');
    await wait(()=>!document.getElementById('error').hidden && windshield.disabled, 'failure feedback');
    await fetch('/smoke/up');
    await wait(()=>document.getElementById('error').hidden && !windshield.disabled, 'connection recovery');
    check(document.getElementById('feedback').textContent.includes('연결됐어요'), 'stale failure message after recovery');
    parent.document.documentElement.dataset.smoke='PASS';
    parent.document.documentElement.dataset.viewport=String(innerWidth);
  } catch(error) { parent.document.documentElement.dataset.smoke='FAIL'; parent.document.documentElement.dataset.smokeError=error.message; }
})();
"""


class SmokeHandler(sim.Handler):
    def send_header(self, keyword, value):
        # 독립 테스트 서버에서만 같은 출처의 390px 프레임을 허용한다. 제품 서버의 CSP는 유지한다.
        if keyword == 'Content-Security-Policy' and self.server.mobile:
            value = value.replace("frame-ancestors 'none'", "frame-ancestors 'self'")
        super().send_header(keyword, value)

    def do_GET(self):
        if self.path == '/' and self.server.mobile:
            self.respond(200, b'<!doctype html><html><body style="margin:0"><iframe title="390px mobile viewport" src="/app" style="border:0;width:390px;height:1000px"></iframe></body></html>', 'text/html; charset=utf-8')
        elif self.path in ('/', '/app'):
            html = (sim.WEB / 'index.html').read_text(encoding='utf-8').replace('</head>', '<script src="/smoke.js" defer></script></head>')
            self.respond(200, html.encode(), 'text/html; charset=utf-8')
        elif self.path == '/smoke.js':
            self.respond(200, SCRIPT.encode(), 'text/javascript')
        elif self.path.startswith('/smoke/'):
            action = self.path.rsplit('/', 1)[-1]
            self.server.vc.state['gear']['actual']['value'] = 'Drive' if action == 'drive' else 'Park'
            self.server.vc.session_status = 500 if action == 'down' else 200
            self.respond(200, {})
        else:
            super().do_GET()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--chrome', required=True, help='installed Chrome executable')
    parser.add_argument('--mobile', action='store_true')
    args = parser.parse_args()
    fixture = tests.InteriorSimulatorTest()
    fixture.setUp()
    fixture.app.shutdown()
    fixture.app.server_close()
    app = ThreadingHTTPServer(('127.0.0.1', 0), SmokeHandler)
    app.bridge = sim.Bridge(fixture.vc.server_port)
    app.vc = fixture.vc
    app.mobile = args.mobile
    threading.Thread(target=app.serve_forever, daemon=True).start()
    captures = ROOT / 'captures'
    captures.mkdir(exist_ok=True)
    name = 'mobile' if args.mobile else 'desktop'
    try:
        with tempfile.TemporaryDirectory(prefix='interior-chrome-', dir=captures) as profile:
            result = subprocess.run([args.chrome, '--headless=new', '--disable-gpu', '--no-first-run',
                '--no-default-browser-check', '--disable-background-networking', '--hide-scrollbars',
                '--force-device-scale-factor=1', f'--user-data-dir={profile}',
                '--window-size=' + ('390,1000' if args.mobile else '1440,1080'),
                '--virtual-time-budget=16000', '--dump-dom',
                f'--screenshot={captures / ("ioniq5-web-" + name + ".png")}',
                f'http://127.0.0.1:{app.server_port}/'], capture_output=True, timeout=55)
            dom = result.stdout.decode('utf-8', errors='replace')
            (captures / f'ioniq5-web-{name}.html').write_text(dom, encoding='utf-8')
            if result.returncode != 0 or 'data-smoke="PASS"' not in dom:
                raise SystemExit('FAIL browser smoke; inspect captures/ioniq5-web-' + name + '.html')
            if args.mobile and 'data-viewport="390"' not in dom:
                raise SystemExit('FAIL: mobile viewport was not 390 CSS pixels')
            print(f'PASS {name}: photo, touch targets, focus, live controls, P gate, failure/recovery, no page overflow')
    finally:
        app.shutdown()
        app.server_close()
        fixture.doCleanups()
