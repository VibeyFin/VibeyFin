#!/bin/bash
# Fails when the shipped languages disagree: shared/languages.json, Android's locales_config.xml and iOS's CFBundleLocalizations
# must list the same tags, and each shipped language needs a strings file in every layer (shared/strings/<layer>/) and a plural
# rule. A strings file that is not listed yet is a language still being translated: it may be empty, {} or partial, and it needs
# no plural rule until it ships. A key in two layers is an override and needs different English text; an identical copy fails.
# Every file that has text must use only {{placeholders}} that the English layers have for that key; keys the English layers lack
# are only warned about (English may have dropped them since the translation was last synced).
set -e
cd "$(dirname "$0")/.."
python3 -I - <<'PY'
import glob, json, os, plistlib, re, sys

errors = []

layers = sorted(os.path.basename(p) for p in glob.glob('shared/strings/*') if os.path.isdir(p))
files = {layer: {os.path.basename(p)[:-5] for p in glob.glob('shared/strings/%s/*.json' % layer)} for layer in layers}
if not layers:
    errors.append('shared/strings has no layer folders')
listed = [entry['tag'] for entry in json.load(open('shared/languages.json'))]
names = [entry.get('name') for entry in json.load(open('shared/languages.json'))]
android = set(re.findall(r'android:name="([^"]+)"', open('androidApp/src/main/res/xml/locales_config.xml').read()))
ios = set(plistlib.load(open('iosApp/iosApp/Info.plist', 'rb')).get('CFBundleLocalizations', []))

if len(listed) != len(set(listed)):
    errors.append('languages.json lists a tag twice')
if 'en' not in listed:
    errors.append('languages.json must list en')
if not all(names):
    errors.append('every languages.json entry needs a name')

sources = {'shared/languages.json': set(listed), 'locales_config.xml': android, 'CFBundleLocalizations': ios}
for label, tags in sources.items():
    for other_label, other in sources.items():
        if label < other_label:
            for tag in sorted(tags - other):
                errors.append('%s has %s but %s does not' % (label, tag, other_label))
            for tag in sorted(other - tags):
                errors.append('%s has %s but %s does not' % (other_label, tag, label))

for layer in layers:
    for tag in sorted((set(listed) | {'en'}) - files[layer]):
        errors.append('shared/strings/%s/%s.json does not exist but %s is listed in shared/languages.json' % (layer, tag, tag))

rules = open('shared/src/commonMain/kotlin/app/vibeyfin/i18n/PluralRules.kt').read()
for tag in sorted(set(listed)):
    base = tag.split('-')[0]
    if '"%s"' % base not in rules:
        errors.append('PluralRules.kt has no rule for %s' % base)

suffix = re.compile(r'_(zero|one|two|few|many|other)$')
placeholder = re.compile(r'\{\{(\w+)\}\}')


def read_layer(layer, tag):
    path = 'shared/strings/%s/%s.json' % (layer, tag)
    if not os.path.exists(path):
        return {}
    text = open(path).read().strip()
    if not text:
        return {}
    try:
        return json.loads(text)
    except ValueError as problem:
        errors.append('%s is not valid JSON: %s' % (path, problem))
        return {}


english_layers = {layer: read_layer(layer, 'en') for layer in layers}
english = {}
allowed = {}
owner = {}
for layer in layers:
    for key, value in english_layers[layer].items():
        if key in english and english[key] == value:
            errors.append('%s is in %s and %s with the same English text: keep it in one layer (a duplicate must override with different text)' % (key, owner[key], layer))
        english[key] = value
        owner[key] = layer
        base = suffix.sub('', key)
        allowed.setdefault(base, set()).update(placeholder.findall(value))
        allowed[base].add('count') if suffix.search(key) else None

warnings = []
for layer in layers:
    for tag in sorted(files[layer] - {'en'}):
        translation = read_layer(layer, tag)
        for key, value in translation.items():
            base = suffix.sub('', key)
            if base not in allowed or (key not in english and not suffix.search(key)):
                warnings.append('%s/%s.json has %s, which the English layers do not' % (layer, tag, key))
            elif value and not set(placeholder.findall(value)) <= allowed[base]:
                errors.append('%s/%s.json %s uses placeholders the English text does not: %s' % (layer, tag, key, sorted(set(placeholder.findall(value)) - allowed[base])))

for warning in warnings:
    print('warning: ' + warning)
if errors:
    print('\n'.join(errors))
    sys.exit(1)
PY
