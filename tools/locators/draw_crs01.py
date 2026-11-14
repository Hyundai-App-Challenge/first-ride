"""CRS-01 위치 안내 그림 3장(W-17). 공조 조작부 하나를 그리고 그림마다 목표 버튼 하나만 강조한다.

직접 그린 그림이다(POL-W-11). 제조사 설명서의 이미지를 쓰지 않는다. 기준 차종(D2)이 정해지면 버튼 배치를 그 차에 맞춘다.

    python3 -I tools/locators/draw_crs01.py app/src/main/assets/models/reference/assets
"""
import math, sys
from PIL import Image, ImageDraw, ImageFont

S = 2                      # 2배로 그린 뒤 줄여 가장자리를 매끄럽게
W, H = 1200, 580
BG = (246, 247, 249)
PANEL = (44, 48, 56)
BTN = (70, 76, 88)
ICON = (214, 219, 226)
DIM = (150, 156, 166)
ACCENT = (255, 138, 0)
ACCENT_ICON = (255, 255, 255)
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

def s(v): return int(round(v * S))

def wavy(d, x, y0, y1, color, w):
    pts = []
    n = 24
    for i in range(n + 1):
        t = i / n
        y = y0 + (y1 - y0) * t
        pts.append((s(x + 7 * math.sin(t * 2 * math.pi)), s(y)))
    d.line(pts, fill=color, width=s(w), joint="curve")
    # 위쪽 화살촉
    d.polygon([(s(x - 7), s(y1 + 3)), (s(x + 7), s(y1 + 3)), (s(x), s(y1 - 8))], fill=color)

def icon_front_defrost(d, cx, cy, c):
    # 부채꼴(앞유리) 윤곽 + 김이 올라가는 물결 화살표 3개
    r_out, r_in = 62, 18
    box_o = [s(cx - r_out), s(cy + 20 - r_out), s(cx + r_out), s(cy + 20 + r_out)]
    d.arc(box_o, 215, 325, fill=c, width=s(7))
    box_i = [s(cx - r_in), s(cy + 20 - r_in), s(cx + r_in), s(cy + 20 + r_in)]
    d.arc(box_i, 215, 325, fill=c, width=s(7))
    for a in (215, 325):
        ra = math.radians(a)
        d.line([(s(cx + r_in * math.cos(ra)), s(cy + 20 + r_in * math.sin(ra))),
                (s(cx + r_out * math.cos(ra)), s(cy + 20 + r_out * math.sin(ra)))], fill=c, width=s(7))
    for dx in (-22, 0, 22):
        wavy(d, cx + dx, cy + 34, cy - 4, c, 5)

def icon_rear_defrost(d, cx, cy, c):
    d.rounded_rectangle([s(cx - 50), s(cy - 30), s(cx + 50), s(cy + 22)], radius=s(8), outline=c, width=s(7))
    for dx in (-22, 0, 22):
        wavy(d, cx + dx, cy + 44, cy + 6, c, 5)

def icon_text(d, cx, cy, c, text, size):
    f = ImageFont.truetype(FONT, s(size))
    d.text((s(cx), s(cy)), text, font=f, fill=c, anchor="mm")

def icon_recirc(d, cx, cy, c):
    # 차 옆모습 윤곽 + 안에서 도는 화살표(내기 순환)
    body = [(-64, 22), (-64, 4), (-50, -2), (-32, -30), (30, -30), (48, -2), (64, 4), (64, 22)]
    d.polygon([(s(cx + x), s(cy + 6 + y)) for x, y in body], outline=c, width=s(6))
    r = 15
    ccx, ccy = cx, cy - 2
    d.arc([s(ccx - r), s(ccy - r), s(ccx + r), s(ccy + r)], 60, 360, fill=c, width=s(5))
    ax, ay = ccx + r, ccy
    d.polygon([(s(ax - 9), s(ay - 2)), (s(ax + 9), s(ay - 2)), (s(ax), s(ay + 10))], fill=c)

def icon_fan(d, cx, cy, c):
    for k in range(3):
        a = math.radians(90 + k * 120)
        bx, by = cx + 22 * math.cos(a), cy - 22 * math.sin(a)
        d.ellipse([s(bx - 18), s(by - 11), s(bx + 18), s(by + 11)], fill=c)
    d.ellipse([s(cx - 9), s(cy - 9), s(cx + 9), s(cy + 9)], fill=PANEL)

def button(d, box, highlighted, draw_icon):
    x0, y0, x1, y1 = box
    if highlighted:
        d.rounded_rectangle([s(x0 - 14), s(y0 - 14), s(x1 + 14), s(y1 + 14)], radius=s(30), outline=ACCENT, width=s(8))
    d.rounded_rectangle([s(x0), s(y0), s(x1), s(y1)], radius=s(20), fill=ACCENT if highlighted else BTN)
    draw_icon((x0 + x1) / 2, (y0 + y1) / 2, ACCENT_ICON if highlighted else ICON)

def draw(target, out):
    img = Image.new("RGB", (s(W), s(H)), BG)
    d = ImageDraw.Draw(img)
    # 대시보드 윤곽과 공조 조작부
    bw, bh, gap = 210, 150, 36
    row_w = bw * 4 + gap * 3
    x0 = (W - row_w) / 2
    top = 150
    bottom = top + bh + 36
    d.rounded_rectangle([s(40), s(40), s(W - 40), s(H - 40)], radius=s(40), fill=(226, 229, 234))
    d.rounded_rectangle([s(x0 - 50), s(top - 50), s(x0 + row_w + 50), s(bottom + 120 + 50)], radius=s(32), fill=PANEL)
    icons = {
        "front_defrost": lambda cx, cy, c: icon_front_defrost(d, cx, cy, c),
        "rear_defrost": lambda cx, cy, c: icon_rear_defrost(d, cx, cy, c),
        "ac": lambda cx, cy, c: icon_text(d, cx, cy, c, "A/C", 54),
        "recirculation": lambda cx, cy, c: icon_recirc(d, cx, cy, c),
    }
    x = x0
    for name, fn in icons.items():
        button(d, (x, top, x + bw, top + bh), name == target, fn)
        x += bw + gap
    # 아래 줄: 운전석 온도, 풍량, 동승석 온도. 강조하지 않는다
    sw = (row_w - gap * 2) / 3
    x = x0
    for label in ("temp", "fan", "temp"):
        box = (x, bottom, x + sw, bottom + 120)
        if label == "fan":
            button(d, box, False, lambda cx, cy, c: icon_fan(d, cx, cy, c))
        else:
            button(d, box, False, lambda cx, cy, c: icon_text(d, cx, cy, DIM, "22.0°", 40))
        x += sw + gap
    img = img.resize((W, H), Image.LANCZOS)
    img.save(out, "WEBP", quality=90, method=6)


if __name__ == "__main__":
    outdir = sys.argv[1]
    for target, name in (("front_defrost", "hvac_front_defrost"), ("ac", "hvac_ac"), ("recirculation", "hvac_recirculation")):
        draw(target, f"{outdir}/{name}.webp")
