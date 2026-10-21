#!/bin/bash
# Fails when the brand values in iosApp/iosApp/Theme.swift or the AccentColor asset drift from the
# `ios` column of design/tokens.json. System colours (bg, ink, ...) are Apple semantic colours and
# have no hex in Swift, so only the brand, spacing and radius tokens are compared.
set -e
cd "$(dirname "$0")/.."
python3 -I - <<'PY'
import json, re, sys

tokens = json.load(open('design/tokens.json'))
swift = open('iosApp/iosApp/Theme.swift').read()
errors = []

def family(name):
    return {t['name']: t for t in tokens[name]['tokens']}

def argb(value):
    v = value.strip()
    if v.startswith('#'):
        return (int(v[1:], 16), 1.0)
    r, g, b, a = re.fullmatch(r'rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)', v).groups()
    return ((int(r) << 16) | (int(g) << 8) | int(b), float(a))

def block(kind):
    m = re.search(r'enum %s \{(.*?)\n\}' % kind, swift, re.S)
    if not m:
        errors.append('Theme.swift has no `enum %s`' % kind)
        return ''
    return m.group(1)

colors = family('color')
brand = block('Brand')
for swift_name, token in [
    ('accent', 'accent'), ('onAccent', 'on-accent'), ('accentSoft', 'accent-soft'),
    ('onAccentSoft', 'on-accent-soft'), ('scrim', 'scrim'), ('scrimStrong', 'scrim-strong'),
    ('progressTrack', 'progress-track'),
    ('avatar1', 'avatar-1'), ('avatar2', 'avatar-2'), ('avatar3', 'avatar-3'),
    ('avatar4', 'avatar-4'), ('avatar5', 'avatar-5'), ('avatar6', 'avatar-6'),
]:
    m = re.search(r'static let %s = Color\(hex: 0x([0-9A-Fa-f]{6})(?:, alpha: ([\d.]+))?\)' % swift_name, brand)
    if not m:
        errors.append('Brand.%s missing' % swift_name)
        continue
    got = (int(m.group(1), 16), float(m.group(2) or 1.0))
    want = argb(colors[token]['value']['ios'])
    if got != want:
        errors.append('Brand.%s is %s, tokens.json %s is %s' % (swift_name, got, token, want))

def px(token):
    return float(token['value'].removesuffix('px'))

for kind, mapping, fam in [
    ('Space', {'s%d' % i: 'space-%d' % i for i in range(1, 8)}, family('spacing')),
    ('Radius', {'artwork': 'radius-artwork', 'card': 'radius-ios-card'}, family('radius')),
    ('Size', {'thumbWidth': 'thumb-w'}, family('size')),
]:
    body = block(kind)
    for swift_name, token in mapping.items():
        m = re.search(r'static let %s: CGFloat = ([\d.]+)' % swift_name, body)
        if not m:
            errors.append('%s.%s missing' % (kind, swift_name))
        elif float(m.group(1)) != px(fam[token]):
            errors.append('%s.%s is %s, tokens.json %s is %s' % (kind, swift_name, m.group(1), token, px(fam[token])))

# The AccentColor asset must hold the same aqua.
asset = json.load(open('iosApp/iosApp/Assets.xcassets/AccentColor.colorset/Contents.json'))
c = asset['colors'][0]['color']['components']
got = (int(c['red'], 16) << 16) | (int(c['green'], 16) << 8) | int(c['blue'], 16)
if got != argb(colors['accent']['value']['ios'])[0]:
    errors.append('AccentColor asset is 0x%06X, tokens.json accent differs' % got)

if errors:
    print('\n'.join(errors))
    sys.exit(1)
print('swift tokens: match design/tokens.json')
PY
