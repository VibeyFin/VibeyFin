#!/bin/bash
# Fails when the spacing, radius and size values in iosApp/iosApp/App/Theme.swift drift from design/tokens.json.
# The colours are generated (./gradlew generateThemes), and CI fails when the generated files differ.
set -e
cd "$(dirname "$0")/.."
python3 -I - <<'PY'
import json, re, sys

tokens = json.load(open('design/tokens.json'))
swift = open('iosApp/iosApp/App/Theme.swift').read()
errors = []

def family(name):
    return {t['name']: t for t in tokens[name]['tokens']}

def block(kind):
    m = re.search(r'enum %s \{(.*?)\n\}' % kind, swift, re.S)
    if not m:
        errors.append('Theme.swift has no `enum %s`' % kind)
        return ''
    return m.group(1)

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

if errors:
    print('\n'.join(errors))
    sys.exit(1)
print('swift tokens: match design/tokens.json')
PY
