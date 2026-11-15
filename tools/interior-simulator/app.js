'use strict';
const $ = id => document.getElementById(id);
let panel, state = null, connected = false, busy = false, revision = 0;
const buttons = [];

async function request(path, options = {}) {
  const response = await fetch(path, {cache: 'no-store', ...options});
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || '연결을 확인해 주세요.');
  return data;
}
function valueOf(control) {
  return state ? control.state.reduce((value, key) => value?.[key], state.hvac) : undefined;
}
function update() {
  $('connection').textContent = !connected ? '차량 연결 확인 중' : state.parked ? '차량 연결됨 · P' : '기어 P에서 실습해 주세요';
  $('connection').classList.toggle('connected', connected && state.parked);
  for (const {button, control, choice} of buttons) {
    const value = valueOf(control);
    button.disabled = busy || !connected || !state.parked;
    if (control.kind === 'step' && typeof value === 'number') {
      button.disabled ||= control.delta > 0 ? value >= control.max : value <= control.min;
    }
    if (control.kind === 'toggle') button.setAttribute('aria-pressed', String(value === true));
    if (control.kind === 'set') button.setAttribute('aria-pressed', String(value === control.value));
    if (choice !== undefined) button.setAttribute('aria-pressed', String(value === choice));
  }
  const h = state?.hvac;
  const direction = panel.controls.find(control => control.id === 'direction');
  const directionName = direction.choices.find(choice => choice.value === h?.driver?.fan_direction)?.label || '확인 중';
  const temperature = h?.driver?.temp_c;
  const onOff = value => typeof value === 'boolean' ? value ? '켜짐' : '꺼짐' : '—';
  const rows = [['공조 전원', onOff(h?.power_on)], ['앞유리 제상', onOff(h?.front_defroster_on)],
    ['뒷유리 열선', onOff(h?.rear_defroster_on)], ['에어컨', onOff(h?.ac_on)],
    ['공기 순환', !h ? '—' : h.recirc_on ? '내기' : '외기'],
    ['풍량', !h ? '—' : `${h.fan_speed} / 8`], ['운전석 바람 방향', h ? directionName : '—'],
    ['운전석 온도', typeof temperature === 'number' && Number.isFinite(temperature) ? `${temperature.toFixed(1)}°C` : '—']];
  $('vehicle-state').replaceChildren(...rows.map(([label, value]) => {
    const row = document.createElement('div'); row.className = 'state-row';
    const dt = document.createElement('dt'); dt.textContent = label;
    const dd = document.createElement('dd'); dd.textContent = connected ? value : '—';
    row.append(dt, dd); return row;
  }));
}
function fail(error) {
  connected = false;
  $('error').textContent = error.message;
  $('error').hidden = false;
  $('feedback').textContent = '연결을 다시 확인하고 있어요. 상태가 확인되면 조작할 수 있어요.';
}
async function change(control, choice) {
  if (busy || !connected || !state.parked) return;
  if (control.kind === 'choice' && choice === undefined) {
    $('direction-title').scrollIntoView({block: 'nearest', behavior: 'auto'});
    $('directions').querySelector('button')?.focus();
    $('feedback').textContent = '원하는 바람 방향을 선택해 주세요. 앞유리 단계의 목표는 ‘앞유리’예요.';
    return;
  }
  busy = true; revision++; update();
  $('feedback').textContent = `${control.label} 상태를 확인하고 있어요.`;
  try {
    const payload = {control: control.id};
    if (choice !== undefined) payload.value = choice;
    state = await request('/api/control', {method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(payload)});
    connected = true; $('error').hidden = true;
    $('feedback').textContent = '차량 상태가 반영됐어요. 앱에서 결과를 확인해 주세요.';
  } catch (error) { fail(error); }
  finally { busy = false; update(); }
}
function makeButton(control, label = control.label, choice) {
  const button = document.createElement('button');
  button.type = 'button'; button.textContent = label; button.disabled = true;
  button.setAttribute('aria-label', label);
  if (['toggle', 'set'].includes(control.kind) || choice !== undefined) button.setAttribute('aria-pressed', 'false');
  button.addEventListener('click', () => change(control, choice));
  buttons.push({button, control, choice}); return button;
}
async function poll() {
  if (!busy) {
    const reading = revision;
    try {
      const latest = await request('/api/state');
      if (!busy && reading === revision) {
        const reconnected = !connected;
        state = latest; connected = true; $('error').hidden = true;
        if (reconnected) $('feedback').textContent = '차량 상태가 연결됐어요. 앱의 안내에 따라 조작해 주세요.';
        update();
      }
    } catch (error) { if (!busy && reading === revision) { fail(error); update(); } }
  }
  setTimeout(poll, 1000);
}
async function start() {
  try {
    panel = await request('/api/panel');
    const [x, y, w, h] = panel.crop;
    $('panel').style.aspectRatio = `${w * panel.photoWidth} / ${h * panel.photoHeight}`;
    Object.assign($('photo').style, {width: `${100 / w}%`, left: `${-x / w * 100}%`, top: `${-y / h * 100}%`});
    $('source').href = panel.source;
    for (const control of panel.controls) {
      const [rx, ry, rw, rh] = control.rect;
      const spot = makeButton(control); spot.className = 'hotspot';
      const label = document.createElement('span'); label.className = 'label'; label.textContent = control.label;
      spot.replaceChildren(label); spot.title = control.label;
      Object.assign(spot.style, {left: `${(rx + rw / 2 - x) / w * 100}%`, top: `${(ry + rh / 2 - y) / h * 100}%`, width: `${rw / w * 100}%`, height: `${rh / h * 100}%`});
      $('hotspots').append(spot);
      $('control-list').append(makeButton(control));
      if (control.kind === 'choice') {
        for (const choice of control.choices) $('directions').append(makeButton(control, choice.label, choice.value));
      }
    }
    $('show-labels').addEventListener('click', () => {
      const visible = $('panel').classList.toggle('labels-visible');
      $('show-labels').setAttribute('aria-pressed', String(visible));
      $('show-labels').textContent = visible ? '버튼 이름 숨기기' : '버튼 이름 보기';
    });
    update(); poll();
  } catch (error) { $('error').textContent = '실습 사진을 불러오지 못했어요. 페이지를 새로 열어 주세요.'; $('error').hidden = false; }
}
start();
