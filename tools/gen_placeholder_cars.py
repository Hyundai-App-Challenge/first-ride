#!/usr/bin/env python3
"""가상의 일반 차량(실제 차종이 아님) 3D 모형을 한 바퀴 돌려 연속 이미지로 렌더링한다.

실제 차 이미지(assets/cars/<차종>/)가 준비되기 전의 자리표시자다. 외부 3D 프로그램 없이 numpy와 Pillow만 쓴다.
차는 부호 있는 거리 함수(SDF)로 정의하고 레이 마칭으로 그린다. 카메라와 조명은 고정하고 차를 돌린다(턴테이블).

    python tools/gen_placeholder_cars.py                       # 전부 만든다(8코어에서 수 분)
    python tools/gen_placeholder_cars.py --only Sedan --frame 5 --out /tmp/test.png   # 한 장만 확인
"""
import argparse
import math
import os
import sys
from concurrent.futures import ProcessPoolExecutor

import numpy as np
from PIL import Image

F = np.float32
FRAMES = 72
OUT_W, OUT_H = 1000, 560
SS = 1.5                      # 슈퍼샘플링 배율. 가장자리를 매끄럽게 한다.
CAM_DIST, CAM_ELEV_DEG, FOV_DEG = 9.5, 11.0, 26.0
TARGET = np.array([0.0, 0.72, 0.0], dtype=F)
START_YAW_DEG = 35.0          # 0번 프레임은 앞쪽 3/4 각도

# 차 한 대의 치수(m). 앞이 +x, 위가 +y, 오른쪽이 +z. 바퀴는 x=0 기준 앞뒤 대칭이다.
MODELS = {
    "Sedan": dict(
        paint=(0.50, 0.54, 0.60), L=4.75, W=1.86, ox=0.0, wb=2.85, clear=0.18, belt=0.93, roof=1.40, wr=0.355,
        cab_cx=-0.32, cab_hx=1.10, tf=0.62, tr=0.52, tz=0.20, cab_hz=0.80, hood_len=1.45, hood_ang=0.17,
        trunk_len=1.05, trunk_ang=0.16, bed=None),
    "SUV": dict(
        paint=(0.085, 0.10, 0.125), L=4.70, W=1.96, ox=-0.02, wb=2.80, clear=0.25, belt=1.06, roof=1.73, wr=0.385,
        cab_cx=-0.38, cab_hx=1.52, tf=0.30, tr=0.08, tz=0.12, cab_hz=0.86, hood_len=1.05, hood_ang=0.14,
        trunk_len=0.0, trunk_ang=0.0, bed=None),
    "Hatchback": dict(
        paint=(0.045, 0.13, 0.58), L=4.15, W=1.80, ox=0.0, wb=2.60, clear=0.16, belt=0.94, roof=1.47, wr=0.33,
        cab_cx=-0.30, cab_hx=1.28, tf=0.44, tr=0.17, tz=0.16, cab_hz=0.79, hood_len=1.00, hood_ang=0.19,
        trunk_len=0.0, trunk_ang=0.0, bed=None),
    "Pickup": dict(
        paint=(0.06, 0.20, 0.16), L=5.30, W=2.00, ox=0.0, wb=3.20, clear=0.27, belt=1.09, roof=1.86, wr=0.40,
        cab_cx=0.52, cab_hx=0.96, tf=0.30, tr=0.04, tz=0.12, cab_hz=0.88, hood_len=1.10, hood_ang=0.11,
        trunk_len=0.0, trunk_ang=0.0, bed=dict(floor=0.76, x0=-2.57, x1=-0.50)),
}

# ---------------------------------------------------------------- 거리 함수

def smin(a, b, k):
    h = np.clip(F(0.5) + F(0.5) * (b - a) / k, 0, 1)
    return b * (1 - h) + a * h - k * h * (1 - h)


def smax(a, b, k):
    return -smin(-a, -b, k)


def rbox(x, y, z, cx, cy, cz, bx, by, bz, r):
    qx, qy, qz = np.abs(x - cx) - bx, np.abs(y - cy) - by, np.abs(z - cz) - bz
    out = np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2 + np.maximum(qz, 0) ** 2)
    return out + np.minimum(np.maximum(np.maximum(qx, qy), qz), 0) - r


def wheel_d(x, y, z, P):
    hw, zc, rr = 0.13, P["W"] / 2 - 0.20, 0.05
    px, ly = np.abs(x) - P["wb"] / 2, y - P["wr"]
    d1 = np.sqrt(px * px + ly * ly) - P["wr"] + rr
    d2 = np.abs(np.abs(z) - zc) - hw + rr
    return np.minimum(np.maximum(d1, d2), 0) + np.sqrt(np.maximum(d1, 0) ** 2 + np.maximum(d2, 0) ** 2) - rr


def arch_d(x, y, z, P):
    px, ly = np.abs(x) - P["wb"] / 2, y - P["wr"]
    r = np.sqrt(px * px + ly * ly) - (P["wr"] + 0.075)
    slab = np.abs(np.abs(z) - (P["W"] / 2 - 0.10)) - 0.30
    return np.maximum(r, slab)


def body_d(x, y, z, P, full=True):
    L, W, belt, clear, roof = P["L"], P["W"], P["belt"], P["clear"], P["roof"]
    xo = x - P["ox"]
    az = np.abs(z)
    # 아래 차체. 위로 갈수록 폭이 좁아진다(tumblehome).
    u0 = np.clip((y - clear) / (belt - clear), 0, 1.2)
    zt = az / (1 - 0.09 * u0)
    r = 0.24
    lower = rbox(xo, y, zt, 0, (clear + belt) / 2, 0, L / 2 - r, (belt - clear) / 2 - r, W / 2 - r, r) * F(0.9)
    # 보닛과 트렁크 경사
    a = P["hood_ang"]
    nx, ny = math.sin(a), math.cos(a)
    lower = smax(lower, nx * (xo - (L / 2 - P["hood_len"])) + ny * (y - belt), 0.12)
    if P["trunk_len"] > 0:
        b = P["trunk_ang"]
        lower = smax(lower, math.sin(b) * (-xo - (L / 2 - P["trunk_len"])) + math.cos(b) * (y - belt), 0.12)
    # 지붕 부분(위로 갈수록 앞뒤와 폭이 좁아진다)
    u = np.clip((y - belt) / (roof - belt), -0.2, 1.2)
    tx = np.where(xo > P["cab_cx"], F(P["tf"]), F(P["tr"]))
    xs = (xo - P["cab_cx"]) / (1 - tx * u)
    zs = az / (1 - P["tz"] * u)
    rc = 0.16
    cab = rbox(xs, y, zs, 0, (belt + roof) / 2 - 0.02, 0, P["cab_hx"] - rc, (roof - belt) / 2 + 0.05 - rc, P["cab_hz"] - rc, rc) * F(0.72)
    d = smin(lower, cab, 0.16)
    if not full:
        return d
    # 사이드 미러
    mir = rbox(xo, y, az, P["cab_cx"] + P["cab_hx"] * 0.72, belt + 0.07, W / 2 - 0.03, 0.05, 0.035, 0.07, 0.03)
    d = np.minimum(d, mir)
    d = smax(d, -arch_d(x, y, z, P), 0.05)
    if P["bed"]:
        bd = P["bed"]
        cav = rbox(x, y, z, (bd["x0"] + bd["x1"]) / 2, bd["floor"] + 0.5, 0, (bd["x1"] - bd["x0"]) / 2 - 0.02, 0.5 - 0.02, W / 2 - 0.12 - 0.02, 0.02)
        d = smax(d, -cav, 0.03)
    return d


def scene_d(x, y, z, P):
    return np.minimum(body_d(x, y, z, P), wheel_d(x, y, z, P))


# ---------------------------------------------------------------- 렌더링

def norm3(x, y, z):
    n = np.sqrt(x * x + y * y + z * z) + F(1e-9)
    return x / n, y / n, z / n


def car_to_world(x, y, z, th):          # 법선용. 월드 -> 차 변환의 역
    c, s = math.cos(th), math.sin(th)
    return x * c - z * s, y, x * s + z * c


def world_to_car(x, y, z, th):
    c, s = math.cos(th), math.sin(th)
    return x * c + z * s, y, -x * s + z * c


def smoothstep(a, b, v):
    t = np.clip((v - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def env(dx, dy, dz):
    """스튜디오 환경: 어두운 바닥, 밝은 하늘, 위쪽 소프트박스 두 개."""
    k = smoothstep(-0.12, 0.55, dy)
    r = 0.075 + (0.70 - 0.075) * k
    g = 0.082 + (0.76 - 0.082) * k
    b = 0.095 + (0.86 - 0.095) * k
    s1 = np.exp(-(((dx - 0.45) ** 2) * 6 + ((dy - 0.62) ** 2) * 26 + ((dz - 0.35) ** 2) * 4)) * 2.6
    s2 = np.exp(-(((dx + 0.55) ** 2) * 5 + ((dy - 0.35) ** 2) * 40 + ((dz + 0.2) ** 2) * 5)) * 1.5
    hz = np.exp(-((dy - 0.02) ** 2) * 160) * 0.35
    return r + s1 + s2 + hz, g + s1 + s2 + hz, b + s1 * 1.02 + s2 + hz


KEY = np.array([-0.45, 0.80, 0.55], dtype=F)
KEY /= np.linalg.norm(KEY)


def march(P, ox_, oy_, oz_, dx, dy, dz, t0, t1, steps=90):
    n = dx.shape[0]
    t = t0.copy()
    hit = np.zeros(n, dtype=bool)
    idx = np.arange(n)
    for _ in range(steps):
        if idx.size == 0:
            break
        tt = t[idx]
        d = scene_d(ox_[idx] + dx[idx] * tt, oy_[idx] + dy[idx] * tt, oz_[idx] + dz[idx] * tt, P)
        h = d < F(0.0018) * np.maximum(tt, 1)
        hit[idx[h]] = True
        tt = tt + np.maximum(d, F(0.0005)) * F(0.88)
        t[idx] = tt
        idx = idx[(~h) & (tt < t1[idx])]
    return hit, t


def soft_shadow(P, px, py, pz, ldx, ldy, ldz, k=10.0, steps=26):
    res = np.ones(px.shape[0], dtype=F)
    t = np.full(px.shape[0], F(0.04))
    for _ in range(steps):
        d = scene_d(px + ldx * t, py + ldy * t, pz + ldz * t, P)
        res = np.minimum(res, np.clip(k * d / t, 0, 1))
        t = t + np.clip(d, 0.03, 0.4)
    return res


def materials(P, x, y, z, nx, ny, nz):
    N = x.shape[0]
    xo, az, sz = x - P["ox"], np.abs(z), np.sign(z)
    L, W, belt, roof, clear = P["L"], P["W"], P["belt"], P["roof"], P["clear"]
    db, dw = body_d(x, y, z, P), wheel_d(x, y, z, P)
    isw = dw < db
    alb = np.tile(np.array(P["paint"], dtype=F), (N, 1))
    kind = np.zeros(N, dtype=np.int8)          # 0 페인트, 1 유리, 2 타이어, 3 휠, 4 빛, 5 무광 검정
    emis = np.zeros((N, 3), dtype=F)

    # 유리
    near_cab = np.abs(xo - P["cab_cx"]) < P["cab_hx"] + 0.02
    pillar = (np.abs(xo - (P["cab_cx"] + 0.05)) < 0.065) & (np.abs(nz) > 0.5)
    slope_glass = (np.abs(nx) > 0.32) & (ny < 0.93) & (az < P["cab_hz"] - 0.12)
    glass = (y > belt + 0.025) & (y < roof - 0.045) & ((ny < 0.74) | slope_glass) & near_cab & ~pillar & (az < P["cab_hz"] + 0.02)
    kind[glass] = 1
    # 문 틈
    side = (np.abs(nz) > 0.8) & (y > clear + 0.28) & (y < belt + 0.02) & ~isw
    for xl in (P["cab_cx"] + P["cab_hx"] * 0.60, P["cab_cx"] - 0.02, P["cab_cx"] - P["cab_hx"] * 0.62):
        alb = np.where((side & (np.abs(xo - xl) < 0.007))[:, None], alb * F(0.25), alb)
    # 앞뒤 램프, 그릴, 아래쪽
    lamp_y = (y > belt - 0.34) & (y < belt - 0.10)
    head = (nx > 0.5) & (xo > L / 2 - 0.55) & lamp_y & (az > 0.38) & ~isw
    tail = (nx < -0.5) & (xo < -L / 2 + 0.55) & lamp_y & (az > 0.3) & ~isw
    grill = (nx > 0.5) & (xo > L / 2 - 0.55) & (y < belt - 0.40) & (y > clear + 0.10) & (az < 0.55) & ~isw
    low = (y < clear + 0.07) & ~isw
    kind[grill | low | (ny < -0.5)] = 5
    kind[head | tail] = 4
    emis[head] = (1.5, 1.55, 1.65)
    emis[tail] = (1.7, 0.05, 0.08)
    if P["bed"]:
        bd = P["bed"]
        inbed = (x > bd["x0"]) & (x < bd["x1"] + 0.05) & (y < bd["floor"] + 0.7) & (y > bd["floor"] - 0.05) & (az < W / 2 - 0.1) & (ny > 0.3)
        kind[inbed] = 5
    # 바퀴
    px, ly = np.abs(x) - P["wb"] / 2, y - P["wr"]
    r2 = np.sqrt(px * px + ly * ly)
    outer = (nz * sz) > 0.6
    rim = isw & outer & (r2 < P["wr"] * 0.68)
    kind[isw] = 2
    kind[rim] = 3
    ang = np.arctan2(ly, px)
    spoke = smoothstep(0.1, 0.55, np.cos(5 * ang) * 0.5 + 0.5)
    alb = np.where(rim[:, None], (F(0.04) + F(0.62) * spoke)[:, None] * np.ones((1, 3), dtype=F), alb)
    alb = np.where((rim & (r2 < 0.06))[:, None], F(0.7), alb)
    alb = np.where((kind == 2)[:, None], F(0.016), alb)
    alb = np.where((kind == 5)[:, None], F(0.018), alb)
    alb = np.where((kind == 1)[:, None], F(0.012), alb)
    return alb, kind, emis


def render(P, frame, frames=FRAMES, outw=OUT_W, outh=OUT_H, ss=SS):
    W, H = int(outw * ss), int(outh * ss)
    th = math.radians(START_YAW_DEG + frame * 360.0 / frames)
    el = math.radians(CAM_ELEV_DEG)
    cam = TARGET + F(CAM_DIST) * np.array([0.0, math.sin(el), math.cos(el)], dtype=F)
    f = TARGET - cam
    f /= np.linalg.norm(f)
    right = np.cross(f, np.array([0, 1, 0], dtype=F))
    right /= np.linalg.norm(right)
    up = np.cross(right, f)
    tanv = math.tan(math.radians(FOV_DEG) / 2)
    jj, ii = np.mgrid[0:H, 0:W]
    u = ((2 * (ii.ravel() + 0.5) / W - 1) * (W / H) * tanv).astype(F)
    v = ((1 - 2 * (jj.ravel() + 0.5) / H) * tanv).astype(F)
    dx = f[0] + u * right[0] + v * up[0]
    dy = f[1] + u * right[1] + v * up[1]
    dz = f[2] + u * right[2] + v * up[2]
    dx, dy, dz = norm3(dx, dy, dz)
    N = dx.shape[0]
    # 차 공간으로
    cx0, cy0, cz0 = world_to_car(F(cam[0]), F(cam[1]), F(cam[2]), th)
    rdx, rdy, rdz = world_to_car(dx, dy, dz, th)
    ox_, oy_, oz_ = np.full(N, cx0, dtype=F), np.full(N, cy0, dtype=F), np.full(N, cz0, dtype=F)

    # 차를 감싸는 상자와의 교차
    hx, hy, hz = P["L"] / 2 + 0.3, P["roof"] + 0.25, P["W"] / 2 + 0.35
    with np.errstate(divide="ignore", invalid="ignore"):
        inv = lambda a: np.where(np.abs(a) < 1e-8, F(1e-8), a)
        t1x, t2x = (-hx - ox_) / inv(rdx), (hx - ox_) / inv(rdx)
        t1y, t2y = (-0.05 - oy_) / inv(rdy), (hy - oy_) / inv(rdy)
        t1z, t2z = (-hz - oz_) / inv(rdz), (hz - oz_) / inv(rdz)
    tmin = np.maximum(np.maximum(np.minimum(t1x, t2x), np.minimum(t1y, t2y)), np.minimum(t1z, t2z))
    tmax = np.minimum(np.minimum(np.maximum(t1x, t2x), np.maximum(t1y, t2y)), np.maximum(t1z, t2z))
    cand = np.nonzero((tmax > np.maximum(tmin, 0)))[0]
    ci = lambda a: a[cand]
    hit_c, t_c = march(P, ci(ox_), ci(oy_), ci(oz_), ci(rdx), ci(rdy), ci(rdz), np.maximum(ci(tmin), 0).astype(F), ci(tmax).astype(F))
    hit = np.zeros(N, dtype=bool)
    hit[cand[hit_c]] = True
    hi = cand[hit_c]
    th_ = t_c[hit_c]

    rgb = np.zeros((N, 3), dtype=F)
    alpha = np.zeros(N, dtype=F)

    # --- 바닥 그림자(차를 못 맞춘 광선 가운데 바닥을 보는 것)
    ground = (~hit) & (dy < -1e-4)
    gi = np.nonzero(ground)[0]
    tg = -cam[1] / dy[gi]
    gwx, gwz = cam[0] + dx[gi] * tg, cam[2] + dz[gi] * tg
    rad = np.sqrt(gwx ** 2 + gwz ** 2)
    near = rad < 6.4
    gi, gwx, gwz, rad = gi[near], gwx[near], gwz[near], rad[near]
    gx, gy, gz = world_to_car(gwx.astype(F), np.zeros_like(gwx, dtype=F), gwz.astype(F), th)
    dist = scene_d(gx, gy + 0.001, gz, P)
    kx, ky, kz = world_to_car(F(KEY[0]), F(KEY[1]), F(KEY[2]), th)
    sh = soft_shadow(P, gx, gy + 0.01, gz, np.full_like(gx, kx), np.full_like(gx, ky), np.full_like(gx, kz), k=6.0)
    contact = np.exp(-np.maximum(dist, 0) * 2.6) * 0.62
    cast = (1 - sh) * 0.34
    fade = 1 - smoothstep(3.4, 6.2, rad)
    alpha[gi] = np.clip(np.maximum(contact, cast) * fade, 0, 0.7)
    rgb[gi] = F([0.02, 0.025, 0.04])

    # --- 차
    if hi.size:
        px, py, pz = ox_[hi] + rdx[hi] * th_, oy_[hi] + rdy[hi] * th_, oz_[hi] + rdz[hi] * th_
        e = F(0.0015)
        # 사면체 법선
        k = [(1, -1, -1), (-1, -1, 1), (-1, 1, -1), (1, 1, 1)]
        gnx = gny = gnz = 0
        for sx, sy, sz_ in k:
            d = scene_d(px + sx * e, py + sy * e, pz + sz_ * e, P)
            gnx, gny, gnz = gnx + sx * d, gny + sy * d, gnz + sz_ * d
        nx, ny, nz = norm3(gnx, gny, gnz)
        alb, kind, emis = materials(P, px, py, pz, nx, ny, nz)
        # 월드 공간
        wnx, wny, wnz = car_to_world(nx, ny, nz, th)
        vx, vy, vz = -dx[hi], -dy[hi], -dz[hi]
        ndv = np.clip(wnx * vx + wny * vy + wnz * vz, 0, 1)
        # 그림자와 AO
        sh = soft_shadow(P, px + nx * 0.01, py + ny * 0.01, pz + nz * 0.01, np.full_like(px, kx), np.full_like(px, ky), np.full_like(px, kz), k=8.0, steps=22)
        occ = np.zeros_like(px)
        for h, w in ((0.05, 1.0), (0.14, 0.7), (0.3, 0.45)):
            occ += w * np.clip(h - scene_d(px + nx * h, py + ny * h, pz + nz * h, P), 0, 1) / h
        ao = np.clip(1 - 0.55 * occ / 2.15, 0.25, 1)
        # 바닥 가까운 곳은 더 어둡게
        ao *= np.clip(0.55 + py * 1.1, 0.55, 1)
        sky = smoothstep(-0.6, 0.8, wny)
        amb = np.stack([0.07 + 0.26 * sky, 0.08 + 0.29 * sky, 0.10 + 0.34 * sky], axis=1)
        diff = np.clip(wnx * KEY[0] + wny * KEY[1] + wnz * KEY[2], 0, 1) * sh
        col = alb * (amb * ao[:, None] + (diff * 0.95)[:, None] * np.array([1.0, 0.97, 0.92], dtype=F))
        # 반사
        rx = -vx + 2 * ndv * wnx
        ry = -vy + 2 * ndv * wny
        rz = -vz + 2 * ndv * wnz
        er, eg, eb = env(rx, ry, rz)
        fres = (1 - ndv) ** 5
        refl_w = np.where(kind == 1, 0.16 + 0.60 * fres,
                  np.where(kind == 0, 0.085 + 0.50 * fres,
                  np.where(kind == 3, 0.25 + 0.4 * fres,
                  np.where(kind == 4, 0.1 + 0.4 * fres, 0.03 + 0.10 * fres)))).astype(F)
        # 그림자 속 부분은 반사도 줄인다
        refl_w = refl_w * (0.55 + 0.45 * ao)
        col += refl_w[:, None] * np.stack([er, eg, eb], axis=1)
        col += emis
        rgb[hi] = col
        alpha[hi] = 1

    # 톤맵 + 감마
    c = rgb / (1 + rgb * F(0.12))
    c = np.clip(c, 0, 1) ** F(1 / 2.2)
    # 그림자 픽셀(검정)은 알파로만 표현한다. 가장자리가 매끄럽도록 알파로 미리 곱한 값으로 줄인다.
    prem = np.concatenate([c * alpha[:, None], alpha[:, None]], axis=1).reshape(H, W, 4)
    out = np.empty((outh, outw, 4), dtype=F)
    # 박스 필터로 다운샘플한 뒤 알파를 되돌린다. 먼저 Pillow의 부동소수 채널로 줄인다.
    chans = [Image.fromarray(prem[:, :, i].astype(F), mode="F").resize((outw, outh), Image.LANCZOS) for i in range(4)]
    for i in range(4):
        out[:, :, i] = np.asarray(chans[i])
    a = np.clip(out[:, :, 3], 0, 1)
    rgbp = np.clip(out[:, :, :3], 0, 1)
    straight = np.where(a[..., None] > 1e-4, rgbp / np.maximum(a[..., None], 1e-4), 0)
    res = np.concatenate([np.clip(straight, 0, 1), a[..., None]], axis=2)
    return Image.fromarray((res * 255 + 0.5).astype(np.uint8), "RGBA")



# ---------------------------------------------------------------- 실내 파노라마

INT_W, INT_H, INT_LAT = 3072, 680, 40.0     # 가로 360도, 세로 +-40도(원통형에 가까운 파노라마)


def interior_geom(P):
    xf = P["cab_cx"] + P["cab_hx"] * 0.92       # 대시보드 쪽 벽
    xr = P["cab_cx"] - P["cab_hx"] * 0.90       # 뒷벽
    ln = xf - xr
    return dict(xf=xf, xr=xr, floor=0.34, seat_x=xf - 0.95, rear_x=max(xf - 1.85, xr + 0.30), ln=ln)


def interior_objs(x, y, z, P, G):
    """대시보드, 화면, 핸들, 좌석, 콘솔의 거리 함수."""
    xf, floor = G["xf"], G["floor"]
    az = np.abs(z)
    hz = P["cab_hz"]
    dash = smin(rbox(x, y, z, xf - 0.30, floor + 0.50, 0, 0.30 - 0.07, 0.16 - 0.07, hz - 0.05 - 0.07, 0.07),
                rbox(x, y, z, xf - 0.16, floor + 0.62, 0, 0.18 - 0.05, 0.12 - 0.05, hz - 0.10 - 0.05, 0.05), 0.08)
    screen = rbox(x, y, z, xf - 0.46, floor + 0.70, 0.02, 0.012, 0.085, 0.20, 0.008)
    cluster = rbox(x, y, z, xf - 0.43, floor + 0.62, -0.37, 0.04, 0.05, 0.16, 0.02)
    # 핸들: 운전석(z=-0.37) 앞, 위로 기울인 토러스
    phi = 0.38
    nx_, ny_ = -math.cos(phi), math.sin(phi)
    cx, cy, cz = xf - 0.62, floor + 0.62, -0.37
    rx, ry, rz = x - cx, y - cy, z - cz
    ax = rx * nx_ + ry * ny_
    rad = np.sqrt(np.maximum(rx * rx + ry * ry + rz * rz - ax * ax, 0))
    wheel = np.sqrt((rad - 0.19) ** 2 + ax * ax) - 0.02
    sx = G["seat_x"]
    seatF = np.minimum(
        smin(rbox(x, y, az, sx, floor + 0.20, 0.37, 0.27 - 0.07, 0.10 - 0.07, 0.24 - 0.07, 0.07),
             rbox(x, y, az, sx - 0.27, floor + 0.50, 0.37, 0.08 - 0.05, 0.34 - 0.05, 0.24 - 0.05, 0.05), 0.10),
        rbox(x, y, az, sx - 0.31, floor + 0.88, 0.37, 0.05 - 0.04, 0.10 - 0.04, 0.10 - 0.04, 0.04))
    rxp = G["rear_x"]
    seatR = smin(rbox(x, y, z, rxp, floor + 0.20, 0, 0.26 - 0.07, 0.10 - 0.07, hz - 0.10 - 0.07, 0.07),
                 rbox(x, y, z, rxp - 0.26, floor + 0.50, 0, 0.08 - 0.05, 0.34 - 0.05, hz - 0.10 - 0.05, 0.05), 0.10)
    console = rbox(x, y, z, sx + 0.12, floor + 0.20, 0, 0.60 - 0.05, 0.13 - 0.05, 0.10 - 0.05, 0.05)
    return dict(dash=dash, screen=screen, cluster=cluster, wheel=wheel, seatF=seatF, seatR=seatR, console=console)


def cabin_in_d(x, y, z, P, G):
    """실내 공간의 바깥 모양. 지붕부와 같은 기울기로 바닥까지 내려온다(차체와 합치면 접합부에 가짜 면이 생긴다)."""
    belt, roof = P["belt"], P["roof"]
    u = np.clip((y - belt) / (roof - belt), -0.2, 1.2)
    tx = np.where(x - P["ox"] > P["cab_cx"], F(P["tf"]), F(P["tr"]))
    xs = (x - P["ox"] - P["cab_cx"]) / (1 - tx * u)
    zs = np.abs(z) / (1 - P["tz"] * u)
    lo, hi, rc = G["floor"] - 0.05, roof + 0.03, 0.16
    return rbox(xs, y, zs, 0, (lo + hi) / 2, 0, P["cab_hx"] - rc, (hi - lo) / 2 - rc, P["cab_hz"] - rc, rc) * F(0.72)


def interior_d(x, y, z, P, G, parts=False):
    inner = -(cabin_in_d(x, y, z, P, G) + F(0.075))
    shell = np.minimum(inner, np.minimum(np.minimum(G["xf"] - x, x - G["xr"]), y - G["floor"]))
    o = interior_objs(x, y, z, P, G)
    obj = None
    for v in o.values():
        obj = v if obj is None else np.minimum(obj, v)
    if parts:
        return shell, o
    return np.minimum(shell, obj)


def render_interior(P, width=INT_W, height=INT_H):
    G = interior_geom(P)
    eye = (G["seat_x"] - 0.02, G["floor"] + 0.80, -0.37)
    W, H = width, height
    lat_max = math.radians(INT_LAT)
    out = np.zeros((H * W, 3), dtype=F)
    jj, ii = np.mgrid[0:H, 0:W]
    lon = ((ii.ravel() + 0.5) / W - 1 / 6) * 2 * math.pi         # 1/6 지점이 정면이다
    lat = (0.5 - (jj.ravel() + 0.5) / H) * 2 * lat_max
    dx = (np.cos(lat) * np.cos(lon)).astype(F)
    dy = np.sin(lat).astype(F)
    dz = (np.cos(lat) * np.sin(lon)).astype(F)
    CH = 300_000
    for s0 in range(0, H * W, CH):
        sl = slice(s0, min(s0 + CH, H * W))
        out[sl] = shade_interior(P, G, eye, dx[sl], dy[sl], dz[sl])
    c = out / (1 + out * F(0.12))
    c = np.clip(c, 0, 1) ** F(1 / 2.2)
    return Image.fromarray((c.reshape(H, W, 3) * 255 + 0.5).astype(np.uint8), "RGB")


def shade_interior(P, G, eye, dx, dy, dz):
    n = dx.shape[0]
    ex, ey, ez = (np.full(n, F(v)) for v in eye)
    t = np.full(n, F(0.02))
    idx = np.arange(n)
    for _ in range(110):
        if idx.size == 0:
            break
        tt = t[idx]
        d = interior_d(ex[idx] + dx[idx] * tt, ey[idx] + dy[idx] * tt, ez[idx] + dz[idx] * tt, P, G)
        h = d < F(0.0015) * np.maximum(tt, 1)
        tt = tt + np.maximum(d, F(0.0004)) * F(0.9)
        t[idx] = tt
        idx = idx[(~h) & (tt < 6.0)]
    px, py, pz = ex + dx * t, ey + dy * t, ez + dz * t
    e = F(0.0015)
    gx = gy = gz = 0
    for sx, sy, sz_ in [(1, -1, -1), (-1, -1, 1), (-1, 1, -1), (1, 1, 1)]:
        d = interior_d(px + sx * e, py + sy * e, pz + sz_ * e, P, G)
        gx, gy, gz = gx + sx * d, gy + sy * d, gz + sz_ * d
    nx, ny, nz = norm3(gx, gy, gz)
    shell, parts = interior_d(px, py, pz, P, G, parts=True)
    names = list(parts)
    which = np.argmin(np.stack([shell] + [parts[k] for k in names], axis=0), axis=0)   # 0은 벽, 이후는 부품

    belt, roof = P["belt"], P["roof"]
    alb = np.full((n, 3), F(0.085))
    emis = np.zeros((n, 3), dtype=F)
    wall = which == 0
    pillar = (np.abs(px - (P["cab_cx"] + 0.05)) < 0.07) | (np.abs(px - G["xr"]) < 0.10) | (np.abs(px - (G["xf"] + 0.05)) < 0.12)
    win = wall & (py > belt + 0.02) & (py < roof - 0.16) & ~pillar & (ny < 0.8)
    roofm = wall & (py > roof - 0.20) & (ny < -0.4)
    alb = np.where(roofm[:, None], F(0.40), alb)
    alb = np.where((wall & (py < G["floor"] + 0.04))[:, None], F(0.018), alb)
    armrest = wall & (py > belt - 0.14) & (py < belt - 0.07) & (np.abs(nz) > 0.5)
    alb = np.where(armrest[:, None], F(0.17), alb)
    shade = {"dash": 0.075, "screen": 0.01, "cluster": 0.03, "wheel": 0.05, "seatF": 0.12, "seatR": 0.12, "console": 0.085}
    for k, nm in enumerate(names, start=1):
        alb = np.where((which == k)[:, None], F(shade[nm]), alb)
    # 센터 디스플레이: 앱의 코발트 포인트색으로 단순한 화면을 그린다
    sc = which == names.index("screen") + 1
    su = (pz - 0.02) / 0.20
    sv = (py - (G["floor"] + 0.70)) / 0.085
    mapb = (su > -0.95) & (su < 0.12) & (sv > -0.82) & (sv < 0.68)
    tile = (su > 0.28) & (su < 0.95) & (((sv > -0.82) & (sv < -0.34)) | ((sv > -0.2) & (sv < 0.26)) | ((sv > 0.38) & (sv < 0.68)))
    bar = sv > 0.80
    ui = np.where(tile, 0.85, np.where(mapb, 0.42 + 0.12 * np.sin(su * 24.0 + sv * 9.0), np.where(bar, 0.30, 0.10)))
    scr = np.stack([0.04 + 0.10 * ui, 0.10 + 0.30 * ui, 0.42 + 0.95 * ui], axis=1).astype(F)
    emis = np.where((sc & (nx < -0.5))[:, None], scr, emis)
    # 대시 윗면 가장자리의 가는 포인트 선
    dsh = which == names.index("dash") + 1
    line = dsh & (np.abs(py - (G["floor"] + 0.665)) < 0.006) & (nx < -0.2)
    emis = np.where(line[:, None], np.array([0.10, 0.22, 0.95], dtype=F), emis)

    # 조명: 창으로 들어오는 하늘빛이 위에서 아래로
    up = smoothstep(-0.2, 1.0, ny)
    amb = (0.26 + 0.50 * up)[:, None] * np.array([0.92, 0.96, 1.0], dtype=F)
    occ = np.zeros(n, dtype=F)
    for h, w in ((0.05, 1.0), (0.14, 0.7), (0.3, 0.45)):
        occ += w * np.clip(h - interior_d(px + nx * h, py + ny * h, pz + nz * h, P, G), 0, 1) / h
    ao = np.clip(1 - 0.7 * occ / 2.15, 0.2, 1)
    front = np.clip(-nx, 0, 1) * 0.25 + np.clip(ny, 0, 1) * 0.18
    col = alb * (amb * ao[:, None] + front[:, None])
    # 약한 반사
    ndv = np.clip(-(nx * dx + ny * dy + nz * dz), 0, 1)
    dn = dx * nx + dy * ny + dz * nz
    er, eg, eb = env(dx - 2 * dn * nx, dy - 2 * dn * ny, dz - 2 * dn * nz)
    rw = (0.03 + 0.35 * (1 - ndv) ** 4) * np.where(which == 0, 0.5, 1.0)
    col += (rw * ao)[:, None] * np.stack([er, eg, eb], axis=1) * F(0.55)
    col += emis
    # 창밖은 환경(스튜디오 하늘)을 그대로 보여 준다
    er, eg, eb = env(dx, dy, dz)
    col = np.where(win[:, None], np.stack([er, eg, eb], axis=1) * F(0.78), col)
    return col.astype(F)


def job(args):
    name, frame, outdir = args
    if frame < 0:                                   # 실내 파노라마
        path = os.path.join(outdir, name, "interior.webp")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        render_interior(MODELS[name]).save(path, "WEBP", quality=86, method=4)
        return path
    img = render(MODELS[name], frame)
    path = os.path.join(outdir, name, f"ext_{frame:03d}.webp")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "WEBP", quality=84, alpha_quality=92, method=4)
    return path


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="app/src/main/assets/cars")
    ap.add_argument("--only")
    ap.add_argument("--frame", type=int)
    ap.add_argument("--interior", action="store_true", help="실내 파노라마만 만든다(--frame과 같이 쓰면 한 장 확인)")
    ap.add_argument("--small", action="store_true", help="실내 시험용 작은 해상도")
    ap.add_argument("--workers", type=int, default=max(1, (os.cpu_count() or 2) - 1))
    a = ap.parse_args()
    if a.interior and a.frame is not None:
        w, h = (INT_W // 3, INT_H // 3) if a.small else (INT_W, INT_H)
        render_interior(MODELS[a.only or "Sedan"], w, h).save(a.out)
        sys.exit(0)
    if a.frame is not None:
        render(MODELS[a.only or "Sedan"], a.frame).save(a.out)
        sys.exit(0)
    names = [a.only] if a.only else list(MODELS)
    jobs = [(n, -1, a.out) for n in names] + [(n, i, a.out) for n in names for i in range(FRAMES)]
    with ProcessPoolExecutor(a.workers) as ex:
        for k, p in enumerate(ex.map(job, jobs, chunksize=2), 1):
            if k % 24 == 0 or k <= len(names):
                print(f"{k}/{len(jobs)}", flush=True)
    print("완료")
