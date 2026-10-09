#!/usr/bin/env python3
"""Generate app/src/main/assets/emoji.tsv from Unicode + CLDR data.

Sources (downloaded from GitHub):
  - unicode-org/unicodetools : emoji-test.txt (the keyboard emoji list, in order)
  - unicode-org/cldr          : Korean / English emoji annotations (search keywords)

Usage:
  python3 tools/generate_emoji_data.py              # uses EMOJI_VERSION below
  python3 tools/generate_emoji_data.py --emoji 19.0 # when a new Emoji version ships

Output format (one base emoji per line, tab separated):
  group <TAB> emoji <TAB> skin-tone variants (space separated) <TAB> Korean keywords (|) <TAB> English keywords (|)
The first keyword of each language is the emoji's name.
"""
import argparse
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

EMOJI_VERSION = "18.0"
UNICODETOOLS = "https://raw.githubusercontent.com/unicode-org/unicodetools/main/unicodetools/data/emoji/{v}/emoji-test.txt"
CLDR = "https://raw.githubusercontent.com/unicode-org/cldr/main/common/{kind}/{lang}.xml"

GROUPS = {
    "Smileys & Emotion": "smileys",
    "People & Body": "people",
    "Animals & Nature": "nature",
    "Food & Drink": "food",
    "Travel & Places": "travel",
    "Activities": "activities",
    "Objects": "objects",
    "Symbols": "symbols",
    "Flags": "flags",
}
# Unicode 그룹을 그대로 쓰면 탭 내용이 헷갈리는 하위 그룹을 다른 탭으로 옮긴다.
#   하트·감정 기호(❤️ 💯 💤 💬)는 얼굴이 아니므로 '하트·기호' 탭으로,
#   하늘·날씨(☀️ 🌙 ⛅ 🌈 ❄️ 🔥)는 '동물·자연' 탭으로,
#   시계·모래시계(⌛ ⏰ 🕐)는 '사물' 탭으로.
SUBGROUPS = {
    "heart": "symbols",
    "emotion": "symbols",
    "sky & weather": "nature",
    "time": "objects",
}
# 옮겨 간 하위 그룹을 탭 안에서 어디에 둘지. 0이면 원래 순서(파일 순서)대로, 1이면 탭의 맨 뒤.
#   하트는 기호 탭 맨 앞, 날씨는 동물·식물 다음, 시계는 사물 맨 뒤에 온다.
SUBGROUP_AT_END = {"time"}
GROUP_ORDER = ["smileys", "people", "nature", "food", "travel", "activities", "objects", "symbols", "flags"]
SKIN = {0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF}
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "emoji.tsv")


def fetch(url):
    print("download", url, file=sys.stderr)
    with urllib.request.urlopen(url, timeout=120) as r:
        return r.read().decode("utf-8")


def key_of(cps):
    """Identity of an emoji ignoring skin tones and variation selector."""
    return tuple(c for c in cps if c not in SKIN and c != 0xFE0F)


def load_annotations(lang):
    names, keywords = {}, {}
    for kind in ("annotations", "annotationsDerived"):
        root = ET.fromstring(fetch(CLDR.format(kind=kind, lang=lang)))
        for a in root.iter("annotation"):
            cp = a.get("cp")
            text = (a.text or "").strip()
            if not cp or not text or text == "↑↑↑":
                continue
            k = cp.replace("️", "")
            if a.get("type") == "tts":
                names.setdefault(k, text)
            else:
                keywords.setdefault(k, [t.strip() for t in text.split("|") if t.strip()])
    return names, keywords


def words(names, keywords, emoji):
    k = emoji.replace("️", "")
    out = []
    for w in [names.get(k, "")] + keywords.get(k, []):
        w = w.replace("\t", " ").replace("|", " ").strip().lower()
        if w and w not in out:
            out.append(w)
    return "|".join(out)


# 나라 국기: CLDR 이름은 '깃발: 일본'뿐이라 '일본'으로 찾으면 맨 뒤로 밀리고, 흔히 부르는 이름(한국, 우리나라)으로는
# 찾을 수 없다. 나라 이름과 다른 이름을 '깃발: …' 앞에 둔다. 앱은 '깃발: …'까지를 모두 이름으로 보아 먼저 보여 준다.
# 영어의 두 글자 약어(us, un, eu)는 평범한 단어와 겹쳐 추천이 잘못 뜨므로 넣지 않는다.
FLAG_ALIASES = {
    "🇰🇷": (["한국", "우리나라", "남한", "코리아"], ["korea"]),
    "🇺🇸": (["미합중국", "아메리카"], ["usa", "america"]),
    "🇰🇵": (["조선", "북조선"], ["dprk"]),
    "🇬🇧": ([], ["uk", "britain", "great britain"]),
    "🇨🇳": (["중화인민공화국"], []),
    "🇦🇺": (["호주"], []),
    "🇹🇷": (["터키"], ["turkey"]),
    "🇬🇪": (["그루지야"], []),
    "🇳🇱": (["홀란드"], ["holland"]),
    "🇨🇿": (["체코 공화국"], ["czech"]),
    "🇹🇼": (["타이완"], []),
    "🇹🇭": (["타이"], []),
    "🇮🇳": (["인디아"], []),
    "🇦🇪": (["아랍 에미리트"], ["uae"]),
    "🇿🇦": (["남아공", "남아프리카 공화국"], []),
    "🇸🇦": (["사우디"], []),
    "🇻🇦": (["바티칸"], ["vatican"]),
    "🇩🇪": (["도이칠란트"], ["deutschland"]),
    "🇪🇺": (["유럽"], ["europe"]),
    "🇺🇳": (["유엔"], []),
}


def flag_words(emoji, line, prefix, lang):
    """나라 국기의 키워드 줄에 나라 이름과 다른 이름을 이름('깃발: …') 앞에 넣는다. 국기가 아니면 그대로."""
    kw = line.split("|") if line else []
    if not kw or not kw[0].startswith(prefix):
        return line
    country = kw[0][len(prefix):].strip()
    names = [country]
    short = re.sub(r"\s*\(.*\)\s*$", "", country)   # 홍콩(중국 특별행정구) → 홍콩, myanmar (burma) → myanmar
    if short != country:
        names.append(short)
        inner = re.search(r"\(([^()]*)\)\s*$", country)
        if inner and lang == "en":
            names.append(inner.group(1).strip())   # burma
    names += FLAG_ALIASES.get(emoji, ([], []))[0 if lang == "ko" else 1]
    extra = ["국기"] if lang == "ko" else []
    out = []
    for w in names + kw[:1] + extra + kw[1:]:
        w = w.strip().lower()
        if w and w not in out:
            out.append(w)
    return "|".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--emoji", default=EMOJI_VERSION)
    args = ap.parse_args()

    test = fetch(UNICODETOOLS.format(v=args.emoji))
    ko_names, ko_kw = load_annotations("ko")
    en_names, en_kw = load_annotations("en")

    line_re = re.compile(r"^([0-9A-F ]+?)\s*;\s*fully-qualified\s*#\s*(\S+)")
    group = None
    subgroup = None
    bases = []          # (탭, 맨 뒤 여부, 원래 순서, emoji, key)
    by_key = {}
    variants = {}
    for line in test.splitlines():
        if line.startswith("# group:"):
            group = GROUPS.get(line.split(":", 1)[1].strip())
            continue
        if line.startswith("# subgroup:"):
            subgroup = line.split(":", 1)[1].strip()
            continue
        if group is None:
            continue
        m = line_re.match(line)
        if not m:
            continue
        cps = [int(x, 16) for x in m.group(1).split()]
        emoji = "".join(chr(c) for c in cps)
        tones = [c for c in cps if c in SKIN]
        k = key_of(cps)
        if not tones:
            if k not in by_key:
                by_key[k] = emoji
                target = SUBGROUPS.get(subgroup, group)
                at_end = 1 if subgroup in SUBGROUP_AT_END else 0
                bases.append((target, at_end, len(bases), emoji, k))
        elif len(set(tones)) == 1 and k in by_key:
            variants.setdefault(k, []).append(emoji)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("# NewSwipe emoji data - Emoji %s, generated by tools/generate_emoji_data.py\n" % args.emoji)
        bases.sort(key=lambda b: (GROUP_ORDER.index(b[0]), b[1], b[2]))
        for group, _, _, emoji, k in bases:
            f.write("\t".join([
                group, emoji, " ".join(variants.get(k, [])),
                flag_words(emoji, words(ko_names, ko_kw, emoji), "깃발: ", "ko"),
                flag_words(emoji, words(en_names, en_kw, emoji), "flag: ", "en"),
            ]) + "\n")
    print("wrote %d emoji to %s" % (len(bases), OUT), file=sys.stderr)


if __name__ == "__main__":
    main()
