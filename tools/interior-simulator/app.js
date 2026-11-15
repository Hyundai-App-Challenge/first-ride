'use strict';
const $ = id => document.getElementById(id);
let panel, view, state = null, connected = false, busy = false, revision = 0, screen = 'home';
let buttons = [], renderedScreen;

async function request(path, options = {}) {
  const response = await fetch(path, {cache: 'no-store', ...options});
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || '연결을 확인해 주세요.');
  return data;
}
function valueOf(control) {
  return state ? control.state.reduce((value, key) => value?.[key], state[control.domain || 'hvac']) : undefined;
}
function choiceName(id) {
  const control = panel.controls.find(c => c.id === id);
  return connected ? control.choices.find(c => c.value === valueOf(control))?.label || '—' : '—';
}
const onOff = value => typeof value === 'boolean' ? value ? '켜짐' : '꺼짐' : '—';
function temperature() {
  const value = state?.hvac?.driver?.temp_c;
  return connected && typeof value === 'number' && Number.isFinite(value) ? `${value.toFixed(1)}°` : '—';
}
function rows() {
  const h = state?.hvac, b = state?.body;
  const hvac = [['공조 전원', onOff(h?.power_on)], ['앞유리 제상', onOff(h?.front_defroster_on)],
    ['뒷유리 열선', onOff(h?.rear_defroster_on)], ['에어컨', onOff(h?.ac_on)],
    ['공기 순환', !h ? '—' : h.recirc_on ? '내기' : '외기'], ['풍량', h ? `${h.fan_speed} / 8` : '—'],
    ['운전석 바람 방향', choiceName('direction')], ['운전석 온도', temperature()]];
  const lights = [['조명', choiceName('headlights')], ['방향지시등', choiceName('turn')]];
  const body = [['와이퍼', choiceName('wiper')], ['운전석 창문 열림', b ? `${b.windows.front_left}%` : '—'],
    ['뒷좌석 창문 잠금', onOff(state?.window_lock?.locked?.all)], ['도어 잠금', onOff(b?.door_locks?.all)]];
  if (view.id === 'climate') return hvac;
  if (['lights', 'turn'].includes(view.id)) return lights;
  if (view.id === 'wipers') return body.slice(0, 1);
  if (['door', 'locks'].includes(view.id)) return body.slice(1);
  return [...lights, body[0], hvac[7], hvac[5]];
}
function renderDisplay() {
  for (const button of $('screen-tabs').children) button.setAttribute('aria-pressed', String(button.dataset.screen === screen));
  const content = $('screen-content');
  const add = (tag, text, className, id) => {
    const element = document.createElement(tag); element.textContent = text;
    if (className) element.className = className;
    if (id) element.id = id;
    content.append(element); return element;
  };
  // 폴링 중 버튼을 교체하면 포커스나 누르는 중인 클릭이 사라진다. 화면 전환 때만 조립한다.
  if (renderedScreen !== screen) {
    content.replaceChildren();
    if (screen === 'home') {
      add('strong', '첫 탑승', 'display-title'); add('span', '', '', 'display-status');
      for (const [target, label] of [['climate', '공조 화면 →'], ['vehicle', '차량 상태 →']]) {
        const button = add('button', label); button.addEventListener('click', () => { screen = target; renderDisplay(); });
      }
    } else if (screen === 'climate') {
      add('strong', '', 'display-temp', 'display-temperature'); add('span', '', '', 'display-hvac');
      const button = add('button', '공조 조작부 열기 →'); button.addEventListener('click', () => showView('climate', true));
    } else {
      add('strong', '', 'display-title', 'display-lights'); add('span', '', '', 'display-turn'); add('span', '', '', 'display-wiper');
    }
    renderedScreen = screen;
  }
  if (screen === 'home') {
    $('display-status').textContent = connected ? state.parked ? 'P에서 실습 중' : '기어 P가 필요해요' : '차량 연결 확인 중';
  } else if (screen === 'climate') {
    $('display-temperature').textContent = temperature();
    $('display-hvac').textContent = connected ? `풍량 ${state.hvac.fan_speed} · ${choiceName('direction')}` : '차량 연결 확인 중';
  } else {
    $('display-lights').textContent = `조명 · ${choiceName('headlights')}`;
    $('display-turn').textContent = `방향지시등 · ${choiceName('turn')}`;
    $('display-wiper').textContent = `와이퍼 · ${choiceName('wiper')}`;
  }
}
function update() {
  $('connection').textContent = !connected ? '차량 연결 확인 중' : state.parked ? '차량 연결됨 · P' : '기어 P에서 실습해 주세요';
  $('connection').classList.toggle('connected', connected && state.parked);
  const holding = buttons.some(entry => entry.held);
  for (const item of buttons) {
    const {button, control, choice} = item;
    const value = valueOf(control);
    button.disabled = ((busy || holding) && !item.held) || !connected || !state.parked;
    if (control.kind === 'step' && typeof value === 'number') button.disabled ||= control.delta > 0 ? value >= control.max : value <= control.min;
    if (control.kind === 'toggle') button.setAttribute('aria-pressed', String(connected && value === true));
    if (control.kind === 'set') button.setAttribute('aria-pressed', String(connected && value === control.value));
    if (choice !== undefined) button.setAttribute('aria-pressed', String(connected && value === choice));
    if (control.interaction && choice === undefined && !item.dragging) {
      showPosition(item, control.choices.findIndex(c => c.value === value));
    }
  }
  $('vehicle-state').replaceChildren(...rows().map(([label, value]) => {
    const row = document.createElement('div'); row.className = 'state-row';
    const dt = document.createElement('dt'); dt.textContent = label;
    const dd = document.createElement('dd'); dd.textContent = connected ? value : '—';
    row.append(dt, dd); return row;
  }));
  if (view.id === 'overview') renderDisplay();
  $('wiper-preview').hidden = view.id !== 'wipers';
  const wiper = connected ? state?.body?.wipers?.front?.mode : null;
  $('wiper-preview').dataset.mode = wiper || '';
  $('wiper-motion-state').textContent = !connected ? '차량 연결 확인 중' : wiper === 'Auto' ? 'AUTO · 강우 감지 대기 (강우 감지 미연동)' : `현재 ${choiceName('wiper')}`;
  $('lights-preview').hidden = !['lights', 'turn'].includes(view.id);
  for (const [id, mode] of [['blink-left', 'BlinkLeft'], ['blink-right', 'BlinkRight']]) {
    $(id).classList.toggle('blinking', connected && state.lights.turn_signal_mode === mode);
  }
  const light = connected ? state.lights.headlamp_mode : null;
  $('lamp-state').textContent = !connected ? '차량 연결 확인 중' : light === 'LowBeam' ? '전조등 · 미등 켜짐' : light === 'Position' ? '미등 켜짐' : light === 'Auto' ? 'AUTO · 자동 조명 모드' : '조명 꺼짐';
  $('lamp-state').classList.toggle('lit', ['Position', 'LowBeam'].includes(light));
}
function fail(error) {
  cancelInteractions();
  connected = false; $('error').textContent = error.message; $('error').hidden = false;
  $('feedback').textContent = '연결을 다시 확인하고 있어요. 상태가 확인되면 조작할 수 있어요.';
}
async function change(control, choice) {
  if (busy || !connected || !state.parked) return;
  if (control.kind === 'choice' && choice === undefined) {
    const group = $(`choices-${control.id}`);
    group.scrollIntoView({block: 'nearest'}); group.querySelector('button')?.focus();
    $('feedback').textContent = `${control.label}의 원하는 값을 선택해 주세요.`; return;
  }
  const focused = document.activeElement;
  busy = true; revision++; update(); $('feedback').textContent = `${control.label} 상태를 확인하고 있어요.`;
  try {
    const payload = {control: control.id}; if (choice !== undefined) payload.value = choice;
    state = await request('/api/control', {method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(payload)});
    connected = true; $('error').hidden = true; $('feedback').textContent = '차량 상태가 반영됐어요. 앱에서 결과를 확인해 주세요.';
  } catch (error) { fail(error); }
  finally {
    busy = false; update();
    // 요청 중 disabled로 포커스가 풀린 경우만 복원한다. 사용자가 옮긴 포커스는 유지한다.
    if (focused instanceof HTMLButtonElement && focused.isConnected && !focused.disabled && document.activeElement === document.body) focused.focus({preventScroll: true});
  }
}
function makeButton(control, label = control.label, choice) {
  const button = document.createElement('button'); button.type = 'button'; button.textContent = label; button.disabled = true;
  button.setAttribute('aria-label', label); button.dataset.control = control.id;
  if (choice !== undefined) button.dataset.value = String(choice);
  if (['toggle', 'set'].includes(control.kind) || choice !== undefined) button.setAttribute('aria-pressed', 'false');
  const item = {button, control, choice, suppressUntil: 0};
  button.addEventListener('click', event => { if (event.detail === 0 || Date.now() >= item.suppressUntil) change(control, choice); });
  if (control.interaction && choice === undefined) makeGesture(item);
  if (control.id.startsWith('window-') && control.kind === 'step') makeWindowHold(item);
  buttons.push(item); return button;
}
function showPosition(item, index) {
  const {button, control} = item;
  button.setAttribute('aria-valuenow', String(Math.max(0, index)));
  button.setAttribute('aria-valuetext', index < 0 || !connected ? '확인 중' : control.choices[index].label);
  button.dataset.mode = index < 0 || !connected ? '—' : control.choices[index].label;
  if (item.marker) {
    item.marker.hidden = index < 0 || !connected;
    if (!item.marker.hidden) {
      place(item.marker, control.choices[index].rect);
      item.marker.dataset.value = control.choices[index].value;
      item.marker.classList.toggle('preview', Boolean(item.dragging));
    }
  }
}
function makeGesture(item) {
  const {button, control} = item;
  button.dataset.interaction = control.interaction;
  button.setAttribute('role', 'slider');
  button.setAttribute('aria-valuemin', '0'); button.setAttribute('aria-valuemax', String(control.choices.length - 1));
  button.setAttribute('aria-orientation', 'vertical');
  button.setAttribute('aria-label', control.label + (control.interaction === 'dial' ? ' · 위아래로 굴려 조작' : ' · 위아래로 끌어 조작'));
  const select = index => change(control, control.choices[index].value);
  button.addEventListener('keydown', event => {
    const offsets = {ArrowUp: 1, ArrowRight: 1, ArrowDown: -1, ArrowLeft: -1};
    if (!(event.key in offsets) && !['Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const current = control.choices.findIndex(c => c.value === valueOf(control));
    if (current < 0) return;
    select(event.key === 'Home' ? 0 : event.key === 'End' ? control.choices.length - 1 : Math.max(0, Math.min(control.choices.length - 1, current + offsets[event.key])));
  });
  button.addEventListener('pointerdown', event => {
    if (event.button !== 0 || button.disabled || busy || item.dragging) return;
    const index = control.choices.findIndex(c => c.value === valueOf(control));
    if (index < 0) return;
    button.focus({preventScroll: true});
    const centres = control.choices.map(c => c.rect[1] + c.rect[3] / 2);
    const spacing = (Math.max(...centres) - Math.min(...centres)) / (centres.length - 1);
    // 원통 회전과 레버의 상하 위치는 사진의 표시 간격을 따른다. 평면 원형 회전은 사용하지 않는다.
    item.dragging = {id: event.pointerId, x: event.clientX, y: event.clientY, index, preview: index,
      step: spacing * $('photo').getBoundingClientRect().height, moved: false};
    button.setPointerCapture(event.pointerId);
  });
  button.addEventListener('pointermove', event => {
    const drag = item.dragging; if (!drag || drag.id !== event.pointerId) return;
    if (Math.hypot(event.clientX - drag.x, event.clientY - drag.y) < 10 && !drag.moved) return;
    drag.moved = true;
    const steps = (drag.y - event.clientY) / drag.step;
    drag.preview = Math.max(0, Math.min(control.choices.length - 1, drag.index + Math.round(steps)));
    showPosition(item, drag.preview);
    $('feedback').textContent = `놓으면 ${control.choices[drag.preview].label}(으)로 조작해요.`;
  });
  const finish = (event, cancel) => {
    const drag = item.dragging; if (!drag || drag.id !== event.pointerId) return;
    item.dragging = null;
    const photo = $('photo').getBoundingClientRect();
    const px = (event.clientX - photo.x) / photo.width, py = (event.clientY - photo.y) / photo.height;
    const tapped = !drag.moved && !cancel ? control.choices.findIndex(c => {
      const [x, y, w, h] = c.rect; return x <= px && px <= x + w && y <= py && py <= y + h;
    }) : -1;
    if (drag.moved || tapped >= 0) item.suppressUntil = Date.now() + 500;
    if (button.hasPointerCapture(event.pointerId)) button.releasePointerCapture(event.pointerId);
    if (!cancel && drag.moved && drag.preview !== drag.index) select(drag.preview);
    else if (tapped >= 0 && tapped !== drag.index) select(tapped);
    else {
      if (cancel && drag.moved) $('feedback').textContent = '조작을 취소했어요.';
      update();
    }
  };
  button.addEventListener('pointerup', event => finish(event, false));
  button.addEventListener('pointercancel', event => finish(event, true));
  button.addEventListener('lostpointercapture', event => { if (item.dragging) finish(event, true); });
}
function makeWindowHold(item) {
  const {button, control} = item;
  const stop = () => { item.held = false; };
  button.addEventListener('pointerdown', async event => {
    if (event.button !== 0 || button.disabled || busy || buttons.some(entry => entry.held)) return;
    button.setPointerCapture(event.pointerId); item.held = true; item.suppressUntil = Infinity;
    try {
      while (item.held && !button.disabled) {
        await change(control);
        if (!connected || !item.held) break;
        await new Promise(resolve => setTimeout(resolve, 300));
      }
    } finally { item.held = false; item.suppressUntil = Date.now() + 500; update(); }
  });
  for (const event of ['pointerup', 'pointercancel', 'lostpointercapture', 'blur']) button.addEventListener(event, stop);
}
function makePhotoTap(item) {
  const {button, control, choice} = item;
  // 사진의 터치 제스처는 pointerup으로 확정하고 호환 click은 중복 제거한다. 키보드 click은 유지한다.
  button.addEventListener('pointerdown', event => {
    if (event.button !== 0 || button.disabled || busy || item.dragging) return;
    button.focus({preventScroll: true});
    item.dragging = {id: event.pointerId, x: event.clientX, y: event.clientY, moved: false};
    button.setPointerCapture(event.pointerId);
  });
  button.addEventListener('pointermove', event => {
    const drag = item.dragging;
    if (drag?.id === event.pointerId) drag.moved ||= Math.hypot(event.clientX - drag.x, event.clientY - drag.y) >= 10;
  });
  const finish = (event, cancel) => {
    const drag = item.dragging; if (drag?.id !== event.pointerId) return;
    item.dragging = null; item.suppressUntil = Date.now() + 500;
    if (button.hasPointerCapture(event.pointerId)) button.releasePointerCapture(event.pointerId);
    const bounds = button.getBoundingClientRect();
    const inside = event.clientX >= bounds.left && event.clientX <= bounds.right && event.clientY >= bounds.top && event.clientY <= bounds.bottom;
    if (!cancel && !drag.moved && !button.disabled && inside) change(control, choice);
  };
  button.addEventListener('pointerup', event => finish(event, false));
  button.addEventListener('pointercancel', event => finish(event, true));
  button.addEventListener('lostpointercapture', event => finish(event, true));
}
function cancelInteractions() {
  for (const item of buttons) {
    item.held = false;
    const drag = item.dragging; item.dragging = null;
    if (drag && item.button.hasPointerCapture(drag.id)) item.button.releasePointerCapture(drag.id);
  }
}
function place(element, rect, centered = false) {
  const [x, y, w, h] = view.crop, [rx, ry, rw, rh] = rect;
  Object.assign(element.style, {left: `${(rx + (centered ? rw / 2 : 0) - x) / w * 100}%`, top: `${(ry + (centered ? rh / 2 : 0) - y) / h * 100}%`, width: `${rw / w * 100}%`, height: `${rh / h * 100}%`});
}
function navigateButton(id, label) {
  const button = document.createElement('button'); button.type = 'button'; button.textContent = label;
  button.addEventListener('click', () => showView(id, true)); return button;
}
function showView(id, focus = false) {
  cancelInteractions();
  view = panel.views.find(v => v.id === id) || panel.views[0]; history.replaceState(null, '', '#' + view.id);
  buttons = []; $('hotspots').replaceChildren(); $('control-list').replaceChildren();
  $('view-title').textContent = view.name; $('scene-description').textContent = view.description;
  $('controls-title').textContent = view.id === 'overview' ? '실내 둘러보기' : '이곳의 조작';
  $('back').hidden = view.id === 'overview';
  $('back').textContent = '← ' + panel.views.find(v => v.id === (view.parent || 'overview')).name;
  $('show-labels').hidden = view.id === 'overview'; $('panel').dataset.view = view.id;
  for (const button of $('views').children) button.setAttribute('aria-pressed', String(button.dataset.view === (view.parent || view.id)));
  const [x, y, w, h] = view.crop;
  $('panel').style.aspectRatio = `${w * view.photoWidth} / ${h * view.photoHeight}`;
  $('photo').src = '/' + view.photo; $('photo').alt = `아이오닉5 ${view.name}. ${view.description}`;
  Object.assign($('photo').style, {width: `${100 / w}%`, left: `${-x / w * 100}%`, top: `${-y / h * 100}%`});
  $('source').href = view.source; $('manual').hidden = !view.manual; if (view.manual) $('manual').href = view.manual;
  $('display').hidden = !view.display; if (view.display) place($('display'), view.display);
  $('photo-hint').textContent = view.id === 'overview' ? '표시된 위치를 누르면 가까이 볼 수 있어요. 화면 안의 홈·공조·차량도 눌러보세요.' : '노란 영역을 누르면 조작할 수 있어요.';
  for (const link of view.links || []) {
    const spot = navigateButton(link.view, link.label); spot.className = 'scene-link'; place(spot, link.rect); $('hotspots').append(spot);
  }
  for (const control of panel.controls.filter(c => c.view === view.id)) {
    const spot = makeButton(control); spot.className = 'hotspot'; spot.title = control.label;
    const label = document.createElement('span'); label.className = 'label'; label.textContent = control.label;
    spot.replaceChildren(label); place(spot, control.rect, true); $('hotspots').append(spot);
    if (control.interaction) {
      const item = buttons.find(entry => entry.button === spot);
      const marker = document.createElement('div'); marker.className = 'selection-marker'; marker.dataset.control = control.id;
      marker.setAttribute('aria-hidden', 'true'); item.marker = marker; $('hotspots').append(marker);
      // 조명 표시는 끝 스위치 바깥에 있다. 레버 위치는 같은 드래그 영역 안에서 탭으로 선택한다.
      for (const choice of control.interaction === 'dial' ? control.choices : []) {
        const target = makeButton(control, choice.label, choice.value); target.className = 'mode-target'; target.textContent = '';
        makePhotoTap(buttons.find(entry => entry.button === target));
        target.title = choice.label; place(target, choice.rect, true); $('hotspots').append(target);
      }
    }
    if (control.kind === 'choice') {
      const group = document.createElement('fieldset'), legend = document.createElement('legend'); legend.textContent = control.label;
      group.id = `choices-${control.id}`; group.append(legend);
      const choices = document.createElement('div'); choices.className = 'choices';
      for (const choice of control.choices) choices.append(makeButton(control, choice.label, choice.value));
      group.append(choices); $('control-list').append(group);
    } else $('control-list').append(makeButton(control));
  }
  if (view.id === 'overview') {
    for (const target of panel.views.filter(v => v.id !== 'overview' && !v.parent)) $('control-list').append(navigateButton(target.id, target.name + ' →'));
  } else for (const link of view.links || []) $('control-list').append(navigateButton(link.view, link.label + ' →'));
  if (view.id === 'wipers') $('photo-hint').textContent = '레버를 위로 끌면 OFF → AUTO → LO → HI, 아래로 끌면 반대예요. 놓으면 반영돼요.';
  if (view.id === 'lights') $('photo-hint').textContent = '끝 스위치를 위로 굴리면 OFF → AUTO → 미등 → 전조등, 아래로 굴리면 반대예요. 사진의 표시도 누를 수 있어요.';
  if (view.id === 'turn') $('photo-hint').textContent = '레버를 위쪽 A로 올리면 오른쪽, 아래쪽 A로 내리면 왼쪽, 가운데는 중립이에요.';
  if (view.id === 'door') $('photo-hint').textContent = '창문 버튼을 누르고 있으면 계속 움직이고 놓으면 멈춰요. 아래 버튼으로도 조작할 수 있어요.';
  document.querySelector('.photo-scroll').scrollLeft = 0;
  update(); if (focus) $('view-title').focus({preventScroll: true});
}
async function poll() {
  if (!busy) {
    const reading = revision;
    try {
      const latest = await request('/api/state');
      if (!busy && reading === revision) {
        const reconnected = !connected; state = latest; connected = true; $('error').hidden = true;
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
    for (const target of panel.views.filter(v => !v.parent)) {
      const button = navigateButton(target.id, target.name); button.dataset.view = target.id; $('views').append(button);
    }
    $('back').addEventListener('click', () => showView(view.parent || 'overview', true));
    $('show-labels').addEventListener('click', () => {
      const visible = $('panel').classList.toggle('labels-visible'); $('show-labels').setAttribute('aria-pressed', String(visible)); $('show-labels').textContent = visible ? '버튼 이름 숨기기' : '버튼 이름 보기';
    });
    for (const button of $('screen-tabs').children) button.addEventListener('click', () => { screen = button.dataset.screen; renderDisplay(); });
    window.addEventListener('hashchange', () => showView(location.hash.slice(1)));
    window.addEventListener('blur', () => { cancelInteractions(); update(); });
    document.addEventListener('visibilitychange', () => { if (document.hidden) { cancelInteractions(); update(); } });
    showView(location.hash.slice(1)); poll();
  } catch (error) { $('error').textContent = '실내 사진을 불러오지 못했어요. 페이지를 새로 열어 주세요.'; $('error').hidden = false; }
}
start();
