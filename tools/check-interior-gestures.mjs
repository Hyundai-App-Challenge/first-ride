/** 독립 headless Chrome의 실제 pointer/keyboard 입력으로 실내 조작을 검증한다. 추가 패키지 없음. */
import {spawn} from 'node:child_process';
import {mkdtemp, readFile, rm, writeFile, mkdir} from 'node:fs/promises';
import {resolve, sep, basename} from 'node:path';
import {setTimeout as pause} from 'node:timers/promises';

const [chrome, base, mode = 'desktop'] = process.argv.slice(2);
if (!chrome || !base?.startsWith('http://127.0.0.1:')) throw Error('Run through check-interior-ui.py --gestures');
const captures = resolve('captures'); await mkdir(captures, {recursive: true});
const profile = await mkdtemp(resolve(captures, 'interior-gesture-chrome-'));
const child = spawn(chrome, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--disable-background-networking', '--remote-debugging-port=0', `--user-data-dir=${profile}`,
  '--window-size=1440,1080', base + '/?gestures'], {windowsHide: true, stdio: 'ignore'});
let socket, sequence = 0;
const pending = new Map();
const assert = (condition, message) => { if (!condition) throw Error(message); };
async function until(read, label) {
  for (let i = 0; i < 200; i++) { if (await read()) return; await pause(50); }
  throw Error('Timed out: ' + label);
}
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++sequence; pending.set(id, {resolve, reject});
    socket.send(JSON.stringify({id, method, params}));
  });
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', {expression, awaitPromise: true, returnByValue: true});
  if (result.exceptionDetails) throw Error(result.exceptionDetails.text + ': ' + result.exceptionDetails.exception?.description);
  return result.result.value;
}
const state = async () => (await fetch(base + '/api/state')).json();
const writes = async () => (await (await fetch(base + '/smoke/writes')).json()).count;
async function scene(id) {
  await evaluate(`document.querySelector('#views [data-view="${id}"]').click()`);
  await until(() => evaluate('document.getElementById("photo").complete'), 'scene image');
}
async function bounds(id) {
  await until(() => evaluate(`!!document.querySelector('.hotspot[data-control="${id}"]') && !document.querySelector('.hotspot[data-control="${id}"]').disabled`), 'ready control ' + id);
  return evaluate(`(() => {
    const button=document.querySelector('.hotspot[data-control="${id}"]');
    button.scrollIntoView({block:'center',inline:'center'}); button.focus({preventScroll:true});
    const r=button.getBoundingClientRect();
    return {x:r.x,y:r.y,width:r.width,height:r.height};
  })()`);
}
async function pointer(type, x, y) {
  if (mode === 'mobile') {
    const names = {mousePressed: 'touchStart', mouseMoved: 'touchMove', mouseReleased: 'touchEnd'};
    return send('Input.dispatchTouchEvent', {type: names[type], touchPoints: type === 'mouseReleased' ? [] : [{x, y, id: 1}]});
  }
  return send('Input.dispatchMouseEvent', {type, x, y, button: 'left', buttons: type === 'mouseReleased' ? 0 : 1});
}
async function lever(id, steps, cancel = false) {
  const b = await bounds(id), control = (await (await fetch(base + '/api/panel')).json()).controls.find(c => c.id === id);
  const step = Math.max(28, b.height / control.choices.length), distance = steps * step;
  const x = b.x + b.width / 2, y = distance > 0 ? b.y + b.height - 12 : b.y + 12;
  await pointer('mousePressed', x, y);
  for (let i = 1; i <= 6; i++) await pointer('mouseMoved', x, y - distance * i / 6);
  if (cancel) await send('Input.dispatchTouchEvent', {type: 'touchCancel', touchPoints: []});
  else await pointer('mouseReleased', x, y - distance);
}
async function dial(degrees) {
  const b = await bounds('headlights'), cx = b.x + b.width / 2, cy = b.y + b.height / 2, radius = Math.min(b.width, b.height) * .4;
  const point = angle => [cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius];
  await pointer('mousePressed', ...point(-Math.PI / 2));
  for (let i = 1; i <= 12; i++) await pointer('mouseMoved', ...point(-Math.PI / 2 + degrees * Math.PI / 180 * i / 12));
  await pointer('mouseReleased', ...point(-Math.PI / 2 + degrees * Math.PI / 180));
}
async function key(key) {
  await send('Input.dispatchKeyEvent', {type: 'keyDown', key, code: key});
  await send('Input.dispatchKeyEvent', {type: 'keyUp', key, code: key});
}
async function screenshot(name) {
  const {data} = await send('Page.captureScreenshot', {format: 'png'});
  await writeFile(resolve(captures, `ioniq5-gesture-${mode}-${name}.png`), Buffer.from(data, 'base64'));
}
try {
  let port;
  await until(async () => { try { port = (await readFile(resolve(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]; return port; } catch { return false; } }, 'Chrome debugger');
  const tabs = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  socket = new WebSocket(tabs.find(t => t.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, {once: true}); socket.addEventListener('error', reject, {once: true}); });
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data), request = pending.get(message.id);
    if (request) { pending.delete(message.id); message.error ? request.reject(Error(JSON.stringify(message.error))) : request.resolve(message.result); }
  });
  if (mode === 'mobile') await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 1000, deviceScaleFactor: 1, mobile: true});
  await until(() => evaluate('document.getElementById("connection")?.classList.contains("connected")'), 'connection');
  await scene('wipers');
  await lever('wiper', 2);
  await until(async () => (await state()).body.wipers.front.mode === 'Low', 'drag up to LO');
  const transform = await evaluate('getComputedStyle(document.querySelector(".wiper-left")).transform');
  await pause(250);
  assert(await evaluate('getComputedStyle(document.querySelector(".wiper-left")).transform') !== transform, 'Wiper is not moving');
  assert(await evaluate('getComputedStyle(document.querySelector(".wiper-left")).animationDuration') === '2.2s', 'LO speed');
  await screenshot('wipers');
  await lever('wiper', 1);
  await until(async () => (await state()).body.wipers.front.mode === 'High', 'drag up to HI');
  await until(() => evaluate('getComputedStyle(document.querySelector(".wiper-left")).animationDuration === "1s"'), 'HI speed');
  await until(() => evaluate('document.activeElement?.dataset.control === "wiper" && !document.activeElement.disabled'), 'focus after drag');
  await key('Home');
  await until(async () => (await state()).body.wipers.front.mode === 'Off', 'keyboard OFF');
  if (mode === 'mobile') {
    const before = await writes(); await lever('wiper', 2, true); await pause(150);
    assert(await writes() === before, 'Canceled touch wrote vehicle state');
  }
  await scene('lights');
  await dial(120);
  await until(async () => (await state()).lights.headlamp_mode === 'Position', 'rotate to position lights');
  await dial(60);
  await until(async () => (await state()).lights.headlamp_mode === 'LowBeam', 'rotate to headlights');
  await lever('turn', 1);
  await until(async () => (await state()).lights.turn_signal_mode === 'BlinkRight', 'lever up means right');
  await until(() => evaluate('document.getElementById("blink-right").classList.contains("blinking")'), 'right blink');
  assert(!await evaluate('document.getElementById("blink-left").classList.contains("blinking")'), 'Wrong side blinking');
  await lever('turn', -2);
  await until(async () => (await state()).lights.turn_signal_mode === 'BlinkLeft', 'lever down means left');
  await screenshot('lights');
  await lever('turn', 1);
  await until(async () => (await state()).lights.turn_signal_mode === 'Off', 'lever neutral');
  await scene('door');
  const b = await bounds('window-down');
  await pointer('mousePressed', b.x + b.width / 2, b.y + b.height / 2);
  await until(async () => (await state()).body.windows.front_left >= 30, 'window hold repeat');
  await pointer('mouseReleased', 5, 5);
  await pause(150);
  const stopped = (await state()).body.windows.front_left;
  await pause(650);
  assert((await state()).body.windows.front_left === stopped, 'Window continued after release outside');
  const next = await bounds('window-down');
  await pointer('mousePressed', next.x + next.width / 2, next.y + next.height / 2);
  await until(async () => (await state()).body.windows.front_left > stopped, 'second hold');
  await scene('overview');
  await pointer('mouseReleased', 5, 5); await pause(150);
  const navigated = (await state()).body.windows.front_left; await pause(650);
  assert((await state()).body.windows.front_left === navigated, 'Window continued after scene navigation');
  await scene('door');
  const up = await bounds('window-up');
  await pointer('mousePressed', up.x + up.width / 2, up.y + up.height / 2);
  await until(async () => (await state()).body.windows.front_left < navigated, 'hold before blur');
  await evaluate('window.dispatchEvent(new Event("blur"))');
  await pointer('mouseReleased', 5, 5); await pause(150);
  const blurred = (await state()).body.windows.front_left; await pause(650);
  assert((await state()).body.windows.front_left === blurred, 'Window continued after focus loss');
  await scene('overview');
  assert(await evaluate('document.documentElement.scrollWidth<=innerWidth'), 'Page overflow');
  console.log(`PASS ${mode}: real pointer/touch drag, dial rotation, keyboard, cancel, wiper speeds, turn blink, window hold/release/navigation`);
} catch (error) {
  if (socket?.readyState === WebSocket.OPEN) {
    await screenshot('failure');
    console.error(JSON.stringify({state: await state(), ui: await evaluate('({focus:document.activeElement?.dataset.control,error:document.getElementById("error").textContent,feedback:document.getElementById("feedback").textContent})')}));
  }
  throw error;
} finally {
  if (socket?.readyState === WebSocket.OPEN) { try { await send('Browser.close'); } catch {} socket.close(); }
  if (child.exitCode === null) child.kill();
  if (!resolve(profile).startsWith(captures + sep) || !basename(profile).startsWith('interior-gesture-chrome-')) throw Error('Unsafe cleanup path');
  for (let i = 0; i < 20; i++) { try { await rm(profile, {recursive: true, force: true}); break; } catch { await pause(100); } }
}
