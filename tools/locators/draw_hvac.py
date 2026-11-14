"""공조 조작부 위치 안내 그림(W-17). 조작부 하나를 그림마다 같게 그리고 목표 버튼만 강조한다.

직접 그린 그림이다(POL-W-11). 제조사 설명서의 이미지를 쓰지 않는다.
버튼 자리는 차종별 파일(models/reference/CRS-01.json, CRS-02.json)의 위치 문구와 맞춘다:
앞유리 제상은 왼쪽 위, 풍량·바람 방향은 가운데, 뒷유리 열선은 아래쪽.
기준 차종(D2)이 정해지면 버튼 배치를 그 차에 맞춘다.

    python3 -I tools/locators/draw_hvac.py app/src/main/assets/models/reference/assets
"""
import math
import sys

from PIL import Image, ImageDraw, ImageFont

S = 2                      # 2배로 그린 뒤 줄여 가장자리를 매끄럽게
W, H = 1200, 760
BG = (246, 247, 249)
DASH = (226, 229, 234)
PANEL = (44, 48, 56)
BTN = (70, 76, 88)
ICON = (214, 219, 226)
ACCENT = (255, 138, 0)
ON_ACCENT = (255, 255, 255)
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def s(v):
    return int(round(v * S))


class Pen:
    def __init__(self, d):
        self.d = d

    def text(self, cx, cy, c, text, size):
        f = ImageFont.truetype(FONT, s(size))
        self.d.text((s(cx), s(cy)), text, font=f, fill=c, anchor="mm")

    def wavy_up(self, x, y0, y1, c, w=5):
        n = 24
        pts = [(s(x + 6 * math.sin(i / n * 2 * math.pi)), s(y0 + (y1 - y0) * i / n)) for i in range(n + 1)]
        self.d.line(pts, fill=c, width=s(w), joint="curve")
        self.d.polygon([(s(x - 7), s(y1 + 3)), (s(x + 7), s(y1 + 3)), (s(x), s(y1 - 8))], fill=c)

    def triangle(self, cx, cy, size, c, up=True):
        k = -1 if up else 1
        self.d.polygon([(s(cx - size), s(cy - k * size * 0.6)), (s(cx + size), s(cy - k * size * 0.6)), (s(cx), s(cy + k * size * 0.8))], fill=c)

    # 아이콘들. (cx, cy)는 버튼 가운데, c는 색
    def front_defrost(self, cx, cy, c, label=None):
        r_out, r_in, oy = 52, 15, 22 if label is None else 14
        for r in (r_out, r_in):
            self.d.arc([s(cx - r), s(cy + oy - r), s(cx + r), s(cy + oy + r)], 215, 325, fill=c, width=s(6))
        for a in (215, 325):
            ra = math.radians(a)
            self.d.line([(s(cx + r_in * math.cos(ra)), s(cy + oy + r_in * math.sin(ra))),
                         (s(cx + r_out * math.cos(ra)), s(cy + oy + r_out * math.sin(ra)))], fill=c, width=s(6))
        for dx in (-18, 0, 18):
            self.wavy_up(cx + dx, cy + oy + 6, cy + oy - 28, c, 4)
        if label:
            self.text(cx, cy + 40, c, label, 26)

    def rear_defrost(self, cx, cy, c):
        self.d.rounded_rectangle([s(cx - 46), s(cy - 26), s(cx + 46), s(cy + 20)], radius=s(8), outline=c, width=s(6))
        for dx in (-20, 0, 20):
            self.wavy_up(cx + dx, cy + 38, cy + 2, c, 4)

    def recirc(self, cx, cy, c):
        body = [(-58, 20), (-58, 4), (-45, -2), (-29, -27), (27, -27), (43, -2), (58, 4), (58, 20)]
        self.d.polygon([(s(cx + x), s(cy + 6 + y)) for x, y in body], outline=c, width=s(6))
        r, ccx, ccy = 14, cx, cy - 1
        self.d.arc([s(ccx - r), s(ccy - r), s(ccx + r), s(ccy + r)], 60, 360, fill=c, width=s(5))
        self.d.polygon([(s(ccx + r - 9), s(ccy - 2)), (s(ccx + r + 9), s(ccy - 2)), (s(ccx + r), s(ccy + 10))], fill=c)

    def fan(self, cx, cy, c, hole):
        for k in range(3):
            a = math.radians(90 + k * 120)
            bx, by = cx + 17 * math.cos(a), cy - 17 * math.sin(a)
            self.d.ellipse([s(bx - 14), s(by - 9), s(bx + 14), s(by + 9)], fill=c)
        self.d.ellipse([s(cx - 7), s(cy - 7), s(cx + 7), s(cy + 7)], fill=hole)

    def fan_step(self, cx, cy, c, hole, up):
        self.fan(cx - 16, cy, c, hole)
        self.triangle(cx + 30, cy, 13, c, up)

    def fan_direction(self, cx, cy, c):
        # 앉은 사람과 얼굴·발 쪽으로 가는 바람 화살표
        self.d.ellipse([s(cx - 26), s(cy - 40), s(cx - 6), s(cy - 20)], fill=c)
        self.d.line([(s(cx - 16), s(cy - 16)), (s(cx - 16), s(cy + 14)), (s(cx + 10), s(cy + 14)), (s(cx + 10), s(cy + 38))], fill=c, width=s(7), joint="curve")
        for y in (-30, 26):
            self.d.line([(s(cx + 46), s(cy + y)), (s(cx + 14), s(cy + y))], fill=c, width=s(5))
            self.d.polygon([(s(cx + 14), s(cy + y - 8)), (s(cx + 14), s(cy + y + 8)), (s(cx + 2), s(cy + y))], fill=c)

    def temperature(self, cx, cy, c):
        self.text(cx - 14, cy, c, "22.0°", 34)
        self.triangle(cx + 62, cy - 14, 10, c, True)
        self.triangle(cx + 62, cy + 14, 10, c, False)

    def power(self, cx, cy, c):
        self.d.arc([s(cx - 22), s(cy - 22), s(cx + 22), s(cy + 22)], 300, 240, fill=c, width=s(6))
        self.d.line([(s(cx), s(cy - 30)), (s(cx), s(cy - 4))], fill=c, width=s(6))


def layout(pen):
    """(이름, 상자, 아이콘) 목록. 상자는 그리기 좌표(1배)."""
    bw, bh, gap = 200, 130, 30
    top_w = bw * 4 + gap * 3
    x0 = (W - top_w) / 2
    y1, y2, y3 = 150, 150 + bh + 30, 150 + (bh + 30) * 2
    items = []
    for i, (name, fn) in enumerate([
        ("front_defrost", lambda cx, cy, c, h: pen.front_defrost(cx, cy, c)),
        ("max_defrost", lambda cx, cy, c, h: pen.front_defrost(cx, cy, c, "MAX")),
        ("ac", lambda cx, cy, c, h: pen.text(cx, cy, c, "A/C", 50)),
        ("recirculation", lambda cx, cy, c, h: pen.recirc(cx, cy, c)),
    ]):
        x = x0 + i * (bw + gap)
        items.append((name, (x, y1, x + bw, y1 + bh), fn))
    # 가운데 줄: 운전석 온도 | 풍량 내림 | 바람 방향 | 풍량 올림 | 동승석 온도
    widths = [196, 130, 142, 130, 196]
    sgap = (top_w - sum(widths)) / 4
    x = x0
    for name, w, fn in zip(
        ("temp_driver", "fan_down", "fan_direction", "fan_up", "temp_passenger"),
        widths,
        (lambda cx, cy, c, h: pen.temperature(cx, cy, c),
         lambda cx, cy, c, h: pen.fan_step(cx, cy, c, h, up=False),
         lambda cx, cy, c, h: pen.fan_direction(cx, cy, c),
         lambda cx, cy, c, h: pen.fan_step(cx, cy, c, h, up=True),
         lambda cx, cy, c, h: pen.temperature(cx, cy, c)),
    ):
        items.append((name, (x, y2, x + w, y2 + bh), fn))
        x += w + sgap
    # 아래 줄: AUTO | 뒷유리 열선 | 전원
    for i, (name, fn) in enumerate([
        ("auto", lambda cx, cy, c, h: pen.text(cx, cy, c, "AUTO", 40)),
        ("rear_defrost", lambda cx, cy, c, h: pen.rear_defrost(cx, cy, c)),
        ("power", lambda cx, cy, c, h: pen.power(cx, cy, c)),
    ]):
        bx = x0 + (i + 0.5) * (bw + gap)
        items.append((name, (bx, y3, bx + bw, y3 + bh), fn))
    panel = (x0 - 50, y1 - 50, x0 + top_w + 50, y3 + bh + 50)
    return items, panel


def draw(targets, out):
    img = Image.new("RGB", (s(W), s(H)), BG)
    d = ImageDraw.Draw(img)
    pen = Pen(d)
    items, panel = layout(pen)
    d.rounded_rectangle([s(40), s(40), s(W - 40), s(H - 40)], radius=s(40), fill=DASH)
    d.rounded_rectangle([s(v) for v in panel], radius=s(32), fill=PANEL)
    for name, (x0, y0, x1, y1), fn in items:
        hit = name in targets
        if hit:
            d.rounded_rectangle([s(x0 - 12), s(y0 - 12), s(x1 + 12), s(y1 + 12)], radius=s(28), outline=ACCENT, width=s(7))
        fill = ACCENT if hit else BTN
        d.rounded_rectangle([s(x0), s(y0), s(x1), s(y1)], radius=s(18), fill=fill)
        fn((x0 + x1) / 2, (y0 + y1) / 2, ON_ACCENT if hit else ICON, fill)
    img.resize((W, H), Image.LANCZOS).save(out, "WEBP", quality=90, method=6)


# 파일 이름 → 강조할 버튼
IMAGES = {
    "hvac_front_defrost": {"front_defrost"},
    "hvac_ac": {"ac"},
    "hvac_recirculation": {"recirculation"},
    "hvac_max_defrost": {"max_defrost"},
    "hvac_fan_speed": {"fan_down", "fan_up"},
    "hvac_temperature": {"temp_driver"},
    "hvac_rear_defrost": {"rear_defrost"},
    "hvac_fan_direction": {"fan_direction"},
}

if __name__ == "__main__":
    for name, targets in IMAGES.items():
        draw(targets, f"{sys.argv[1]}/{name}.webp")
