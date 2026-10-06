"""
큐레이션 별(어드민 시드) 준비 — 위키백과/위키데이터/위키미디어 공용에서 좌표·요약·사진 후보를 모은다.

  python tools/seed/fetch.py meta      # places.json → build/meta.json (좌표, 요약, 사진 후보+라이선스)
  python tools/seed/fetch.py images    # meta.json 의 선택 사진 → build/img/<id>.jpg (4:3, 1440x1080) + 확인용 시트

사진 정책(2026-10-07 사용자 결정): 위키미디어 공용의 **퍼블릭 도메인 · CC0 · CC BY** 만(BY-SA·NC·ND 제외).
본문 끝에 작가/라이선스를 적고(seed.js 가 붙인다), 4:3 으로 자른 사본을 우리 Storage 에 올린다.
places.json 항목의 "file": "File:…" 로 사진을 직접 지정할 수 있다(후보 자동 선택보다 우선).
"""
import html
import io
import json
import math
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BUILD = ROOT / "build"
IMG = BUILD / "img"
UA = "StarySeed/1.0 (https://github.com/Chaminwoo/Stary; curated map stars)"

ALLOWED = re.compile(r"^(public domain|pd(-[a-z0-9.-]+)?|cc0( 1\.0)?|cc[- ]by( \d\.\d)?( [a-z]{2,3})?)$", re.I)


def get(url, params=None, binary=False, tries=4, post=False):
    body = None
    if params and post:
        body = urllib.parse.urlencode(params).encode("utf-8")
    elif params:
        url += ("&" if "?" in url else "?") + urllib.parse.urlencode(params)
    for i in range(tries):
        try:
            req = urllib.request.Request(url, data=body, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=40) as r:
                data = r.read()
            return data if binary else json.loads(data.decode("utf-8"))
        except Exception as e:  # 429/일시 오류 — 잠깐 쉬고 재시도
            if i == tries - 1 or getattr(e, "code", 0) in (400, 404, 414):
                raise
            time.sleep(2 + i * 3)


def wiki_api(lang):
    return f"https://{lang}.wikipedia.org/w/api.php"


def strip_html(s):
    s = re.sub(r"<[^>]+>", "", s or "")
    return html.unescape(s).strip()


def resolve_article(wiki):
    lang, title = ("en", wiki)
    if re.match(r"^[a-z]{2,3}:", wiki):
        lang, title = wiki.split(":", 1)
    d = get(wiki_api(lang), {
        "action": "query", "format": "json", "redirects": 1, "titles": title,
        "prop": "coordinates|pageprops|extracts", "exintro": 1, "explaintext": 1,
        "exchars": 1800, "ppprop": "wikibase_item",
    })
    pages = d.get("query", {}).get("pages", {})
    page = next(iter(pages.values()))
    if "missing" in page:
        return {"error": f"missing article {wiki}"}
    coords = page.get("coordinates", [{}])[0]
    return {
        "lang": lang,
        "title": page.get("title"),
        "qid": page.get("pageprops", {}).get("wikibase_item"),
        "lat": coords.get("lat"),
        "lng": coords.get("lon"),
        "extract": page.get("extract", ""),
    }


def wikidata_claims(qid):
    if not qid:
        return {}
    d = get("https://www.wikidata.org/w/api.php", {
        "action": "wbgetclaims", "format": "json", "entity": qid,
    })
    claims = d.get("claims", {})
    out = {}
    p18 = claims.get("P18", [])
    if p18:
        out["p18"] = ["File:" + c["mainsnak"]["datavalue"]["value"] for c in p18 if "datavalue" in c["mainsnak"]]
    p625 = claims.get("P625", [])
    if p625 and "datavalue" in p625[0]["mainsnak"]:
        v = p625[0]["mainsnak"]["datavalue"]["value"]
        out["lat"], out["lng"] = v["latitude"], v["longitude"]
    return out


def commons_geosearch(lat, lng, radius=800, limit=40):
    d = get("https://commons.wikimedia.org/w/api.php", {
        "action": "query", "format": "json", "list": "geosearch",
        "gscoord": f"{lat}|{lng}", "gsradius": radius, "gslimit": limit, "gsnamespace": 6,
    })
    return [g["title"] for g in d.get("query", {}).get("geosearch", [])]


def commons_search(q, limit=25):
    d = get("https://commons.wikimedia.org/w/api.php", {
        "action": "query", "format": "json", "list": "search", "srnamespace": 6,
        "srsearch": f"{q} filetype:bitmap", "srlimit": limit,
    })
    return [s["title"] for s in d.get("query", {}).get("search", [])]


BAD_NAME = re.compile(r"(map|logo|flag|coat|diagram|plan|chart|svg|locator|stamp|banknote|coin|poster|drawing|scheme|sketch|\.tif)", re.I)


def image_infos(titles):
    out = {}
    for i in range(0, len(titles), 40):
        chunk = titles[i:i + 40]
        d = get("https://commons.wikimedia.org/w/api.php", {
            "action": "query", "format": "json", "titles": "|".join(chunk),
            "prop": "imageinfo", "iiprop": "url|size|mime|extmetadata", "iiurlwidth": 1600,
        }, post=True)
        for p in d.get("query", {}).get("pages", {}).values():
            ii = (p.get("imageinfo") or [None])[0]
            if not ii:
                continue
            em = ii.get("extmetadata", {})
            lic = strip_html(em.get("LicenseShortName", {}).get("value", ""))
            out[p["title"]] = {
                "file": p["title"],
                "w": ii.get("width", 0), "h": ii.get("height", 0), "mime": ii.get("mime", ""),
                "thumb": ii.get("thumburl"), "page": ii.get("descriptionurl"),
                "license": lic,
                "licenseUrl": strip_html(em.get("LicenseUrl", {}).get("value", "")),
                "artist": strip_html(em.get("Artist", {}).get("value", ""))[:120],
                "credit": strip_html(em.get("Credit", {}).get("value", ""))[:120],
                "allowed": bool(ALLOWED.match(lic.strip())),
            }
    return out


def score(info, preferred, geo=()):
    if not info["allowed"] or info["mime"] not in ("image/jpeg", "image/png"):
        return -1
    if BAD_NAME.search(info["file"]):
        return -1
    w, h = info["w"], info["h"]
    if w < 1000 or h < 700:
        return -1
    ratio = w / h
    s = 0.0
    s += 3.0 if info["file"] in preferred else 0.0       # 위키데이터 대표 사진
    s += 1.0 if info["file"] in geo else 0.0             # 그 좌표 근처에서 찍힌 사진(키워드 검색보다 믿을 만함)
    s += 1.5 if 1.25 <= ratio <= 1.9 else (0.3 if ratio > 1.0 else -1.0)  # 가로 사진 선호(4:3 크롭)
    s += min(w, 4000) / 4000
    return s


def cmd_meta():
    places = json.loads((ROOT / "places.json").read_text(encoding="utf-8"))
    BUILD.mkdir(exist_ok=True)
    old = {}
    meta_path = BUILD / "meta.json"
    if meta_path.exists():
        old = {m["id"]: m for m in json.loads(meta_path.read_text(encoding="utf-8"))}
    out = []
    only = set(sys.argv[sys.argv.index("--only") + 1].split(",")) if "--only" in sys.argv else None
    for p in places:
        fresh = only is not None and p["id"] in only
        if p["id"] in old and not old[p["id"]].get("error") and "--refresh" not in sys.argv and not fresh:
            prev = old[p["id"]]
            prev.update({k: v for k, v in p.items() if k in ("theme", "cc", "lang", "file", "coord")})
            out.append(prev)
            continue
        print("…", p["id"], flush=True)
        art = resolve_article(p["wiki"])
        if "error" in art:
            out.append({**p, "error": art["error"]})
            print("   ✗", art["error"])
            continue
        wd = wikidata_claims(art["qid"])
        lat = art["lat"] if art["lat"] is not None else wd.get("lat")
        lng = art["lng"] if art["lng"] is not None else wd.get("lng")
        if p.get("coord"):
            lat, lng = p["coord"]
        preferred = wd.get("p18", [])
        cands = list(preferred)
        geo = commons_geosearch(lat, lng) if lat is not None else []
        cands += geo
        cands += commons_search(art["title"])
        seen, uniq = set(), []
        for c in cands:
            if c not in seen:
                seen.add(c)
                uniq.append(c)
        infos = image_infos(uniq)
        ranked = sorted((i for i in infos.values() if score(i, preferred, geo) >= 0),
                        key=lambda i: -score(i, preferred, geo))
        for r in ranked:
            r["src"] = "p18" if r["file"] in preferred else ("geo" if r["file"] in geo else "search")
        rec = {**p, "title": art["title"], "qid": art["qid"], "lat": lat, "lng": lng,
               "extract": art["extract"], "p18": preferred, "candidates": ranked[:8]}
        if p.get("file"):
            forced = image_infos([p["file"]]).get(p["file"])
            rec["chosen"] = forced
        else:
            rec["chosen"] = ranked[0] if ranked else None
        if rec["chosen"] and p.get("artist"):
            rec["chosen"]["artist"] = p["artist"]  # 기계 판독용 작가 정보가 없는 파일 — 설명 페이지의 작가명으로 대체
        out.append(rec)
        print("   ✓", lat, lng, "| 사진:", (rec["chosen"] or {}).get("file"), (rec["chosen"] or {}).get("license"))
        meta_path.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")  # 중간 저장
        time.sleep(0.3)
    meta_path.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    bad = [m["id"] for m in out if m.get("error") or not m.get("chosen") or m.get("lat") is None]
    print(f"\n{len(out)}곳 / 문제 {len(bad)}곳: {bad}")


def crop43(im):
    from PIL import Image
    im = im.convert("RGB")
    w, h = im.size
    target = 4 / 3
    if w / h > target:
        nw = int(h * target)
        x = (w - nw) // 2
        im = im.crop((x, 0, x + nw, h))
    else:
        nh = int(w / target)
        y = int((h - nh) * 0.45)  # 세로가 남으면 살짝 위쪽을 살린다(하늘·건물 꼭대기)
        im = im.crop((0, y, w, y + nh))
    return im.resize((1440, 1080), Image.LANCZOS)


def cmd_images():
    from PIL import Image, ImageDraw
    meta = json.loads((BUILD / "meta.json").read_text(encoding="utf-8"))
    IMG.mkdir(parents=True, exist_ok=True)
    thumbs = []
    for m in meta:
        ch = m.get("chosen")
        if not ch:
            continue
        dst = IMG / f"{m['id']}.jpg"
        key = IMG / f"{m['id']}.src"
        if not dst.exists() or not key.exists() or key.read_text(encoding="utf-8") != ch["file"]:
            print("↓", m["id"], ch["file"], flush=True)
            data = get(ch["thumb"], binary=True)
            im = crop43(Image.open(io.BytesIO(data)))
            im.save(dst, "JPEG", quality=85, optimize=True, progressive=True)
            key.write_text(ch["file"], encoding="utf-8")
            time.sleep(0.3)
        thumbs.append((m["id"], dst))
    # 확인용 시트 — 4열 × 3행 = 12장씩.
    per = 12
    for s in range(math.ceil(len(thumbs) / per)):
        sheet = Image.new("RGB", (4 * 320, 3 * 270), (12, 12, 16))
        d = ImageDraw.Draw(sheet)
        for k, (pid, path) in enumerate(thumbs[s * per:(s + 1) * per]):
            im = Image.open(path).resize((320, 240))
            x, y = (k % 4) * 320, (k // 4) * 270
            sheet.paste(im, (x, y))
            d.text((x + 6, y + 244), pid, fill=(230, 230, 230))
        sheet.save(BUILD / f"sheet_{s + 1:02d}.jpg", "JPEG", quality=80)
    print(f"사진 {len(thumbs)}장, 시트 {math.ceil(len(thumbs) / per)}장 → {BUILD}")


def cmd_search():
    """사진 후보 직접 찾기: python fetch.py search "검색어" [lat lng] — 허용 라이선스만, 큰 순."""
    q = sys.argv[2]
    titles = commons_search(q, limit=40)
    if len(sys.argv) >= 5:
        titles = commons_geosearch(float(sys.argv[3]), float(sys.argv[4]), radius=3000, limit=60) + titles
    infos = image_infos(list(dict.fromkeys(titles)))
    rows = [i for i in infos.values() if i["allowed"] and i["mime"] in ("image/jpeg", "image/png") and i["w"] >= 1000]
    rows.sort(key=lambda i: -(i["w"] * min(i["w"] / max(i["h"], 1), 1.6)))
    for i in rows[:14]:
        print(f'{i["w"]}x{i["h"]} | {i["license"]} | {i["file"]} | {i["artist"][:40]}')


def cmd_geocode():
    """좌표 확인: python fetch.py geocode "장소 이름" — OpenStreetMap Nominatim(가벼운 1회성 조회)."""
    d = get("https://nominatim.openstreetmap.org/search", {"q": sys.argv[2], "format": "json", "limit": 3})
    for r in d:
        print(r["lat"], r["lon"], "|", r["display_name"][:120])
    time.sleep(1.1)


if __name__ == "__main__":
    {"meta": cmd_meta, "images": cmd_images, "search": cmd_search, "geocode": cmd_geocode}[sys.argv[1]]()
