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
    const navigation = id => document.querySelector(`#views [data-view="${id}"]`).click();
    const choice = (id, label) => [...document.querySelectorAll(`#choices-${id} button`)].find(b=>b.textContent===label);
    const clickPhoto = id => {
      const button=document.querySelector(`.hotspot[data-control="${id}"]`);
      button.scrollIntoView({block:'center',inline:'center'});
      const r=button.getBoundingClientRect();
      check(button.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)), 'covered photo control '+id);
      button.click(); return button;
    };
    const validateScene = async () => {
      const view=JSON.parse(await (await fetch('/api/panel')).text()).views.find(v=>v.id===document.getElementById('panel').dataset.view);
      await wait(()=>document.getElementById('photo').complete && document.getElementById('photo').naturalWidth===view.photoWidth, 'scene photograph');
      check(document.documentElement.scrollWidth<=innerWidth, 'page horizontal overflow');
      for(const button of document.querySelectorAll('.hotspot')) {
        const bounds=button.getBoundingClientRect();
        check(bounds.width>=44 && bounds.height>=44, 'touch target too small');
      }
    };
    await wait(()=>document.querySelector('#views button') && document.getElementById('connection').classList.contains('connected'), 'connected controls');
    await wait(()=>document.getElementById('photo').complete, 'photo loaded');
    await validateScene();
    check(document.getElementById('photo').naturalWidth===2000, 'authentic cockpit photograph');
    for(const button of document.querySelectorAll('.scene-link')) {
      button.scrollIntoView({block:'center',inline:'center'});
      const r=button.getBoundingClientRect();
      check(button.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)), 'covered scene navigation');
    }
    document.querySelector('[data-screen="climate"]').click();
    check(document.getElementById('screen-content').textContent.includes('22.0'), 'climate display screen');
    document.querySelector('[data-screen="vehicle"]').click();
    check(document.getElementById('screen-content').textContent.includes('조명'), 'vehicle display screen');
    document.querySelector('[data-screen="home"]').click();
    check(document.getElementById('screen-content').textContent.includes('첫 탑승'), 'home display screen');
    const homeButton=document.querySelector('#screen-content button'); homeButton.focus();
    await new Promise(r=>setTimeout(r,1200));
    check(homeButton.isConnected && document.activeElement===homeButton, 'poll replaced focused display button');
    navigation('lights'); await validateScene();
    check((await (await fetch('/smoke/writes')).json()).count===0, 'navigation changed vehicle state');
    clickPhoto('headlights');
    check(document.activeElement===choice('headlights','OFF'), 'rotary switch choice focus');
    const position=choice('headlights','미등'); position.click();
    await wait(()=>row('조명')==='미등' && !position.disabled, 'position lights');
    choice('headlights','전조등').click(); await wait(()=>row('조명')==='전조등' && !position.disabled, 'headlights');
    document.querySelector('#hotspots .scene-link').click(); await validateScene();
    const left=choice('turn','왼쪽');
    left.click(); await wait(()=>row('방향지시등')==='왼쪽' && !left.disabled, 'turn signal');
    document.getElementById('back').click(); check(document.getElementById('panel').dataset.view==='lights', 'turn parent back');
    navigation('wipers'); await validateScene();
    choice('wiper','LO · 보통').click(); await wait(()=>row('와이퍼')==='LO · 보통' && !choice('wiper','OFF').disabled, 'wiper');
    navigation('door'); await validateScene();
    clickPhoto('window-down'); await wait(()=>row('운전석 창문 열림')==='10%' && !document.querySelector('[data-control="window-down"]').disabled, 'window');
    clickPhoto('window-lock'); await wait(()=>row('뒷좌석 창문 잠금')==='켜짐' && !document.querySelector('[data-control="window-lock"]').disabled, 'window lock');
    document.querySelector('#hotspots .scene-link').click(); await validateScene();
    clickPhoto('door-lock'); await wait(()=>row('도어 잠금')==='켜짐' && !document.querySelector('[data-control="door-lock"]').disabled, 'door lock');
    document.getElementById('back').click(); check(document.getElementById('panel').dataset.view==='door', 'nested back');
    navigation('climate'); await validateScene();
    const front=document.querySelector('.hotspot[aria-label="앞유리 제상"]');
    front.focus(); check(document.activeElement===front, 'keyboard focus');
    clickPhoto('front'); await wait(()=>row('앞유리 제상')==='켜짐' && !front.disabled, 'photo click did not update state');
    const choices=[...document.querySelectorAll('#choices-direction button')];
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
    navigation('overview'); await validateScene();
    document.querySelector('[data-screen="vehicle"]').click();
    check(document.getElementById('screen-content').textContent.includes('전조등'), 'state survived scene navigation');
    document.querySelector('.photo-scroll').scrollLeft=200;
    window.scrollTo(0,0);
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
            if action == 'writes':
                self.respond(200, {'count': len(self.server.vc.writes)})
                return
            self.server.vc.state['gear']['actual']['value'] = 'Drive' if action == 'drive' else 'Park'
            self.server.vc.session_status = 500 if action == 'down' else 200
            self.respond(200, {})
        else:
            super().do_GET()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--chrome', required=True, help='installed Chrome executable')
    parser.add_argument('--mobile', action='store_true')
    parser.add_argument('--gestures', action='store_true', help='real mouse/touch drag, dial and hold using Chrome input protocol')
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
        if args.gestures:
            subprocess.run(['node', str(ROOT / 'tools/check-interior-gestures.mjs'), args.chrome,
                            f'http://127.0.0.1:{app.server_port}', name], check=True, timeout=55)
            raise SystemExit(0)
        with tempfile.TemporaryDirectory(prefix='interior-chrome-', dir=captures) as profile:
            result = subprocess.run([args.chrome, '--headless=new', '--disable-gpu', '--no-first-run',
                '--no-default-browser-check', '--disable-background-networking', '--hide-scrollbars',
                '--force-device-scale-factor=1', f'--user-data-dir={profile}',
                '--window-size=' + ('390,1000' if args.mobile else '1440,1080'),
                '--virtual-time-budget=25000', '--dump-dom',
                f'--screenshot={captures / ("ioniq5-web-" + name + ".png")}',
                f'http://127.0.0.1:{app.server_port}/'], capture_output=True, timeout=55)
            dom = result.stdout.decode('utf-8', errors='replace')
            (captures / f'ioniq5-web-{name}.html').write_text(dom, encoding='utf-8')
            if result.returncode != 0 or 'data-smoke="PASS"' not in dom:
                raise SystemExit('FAIL browser smoke; inspect captures/ioniq5-web-' + name + '.html')
            if args.mobile and 'data-viewport="390"' not in dom:
                raise SystemExit('FAIL: mobile viewport was not 390 CSS pixels')
            print(f'PASS {name}: 7 scenes, screen switching, all signal domains, hit targets, P gate, failure/recovery, no overflow')
    finally:
        app.shutdown()
        app.server_close()
        fixture.doCleanups()
