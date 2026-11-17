#!/bin/bash
# Fails when the shipped languages disagree: shared/languages.json, Android's locales_config.xml and iOS's CFBundleLocalizations
# must list the same tags, and each shipped language needs a strings file and a plural rule. A strings file that is not listed yet
# is a language still being translated: it may be empty, {} or partial, and it needs no plural rule until it ships. Every file
# that has text must use only {{placeholders}} that en.json has; keys en.json lacks are only warned about (English may have
# dropped them since the translation was last synced).
set -e
cd "$(dirname "$0")/.."
python3 -I - <<'PY'
import glob, json, os, plistlib, re, sys

errors = []

files = {os.path.basename(p)[:-5] for p in glob.glob('shared/strings/*.json')}
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

for tag in sorted(set(listed) - files):
    errors.append('shared/languages.json lists %s but shared/strings/%s.json does not exist' % (tag, tag))

rules = open('shared/src/commonMain/kotlin/app/vibeyfin/i18n/PluralRules.kt').read()
for tag in sorted(set(listed)):
    base = tag.split('-')[0]
    if '"%s"' % base not in rules:
        errors.append('PluralRules.kt has no rule for %s' % base)

suffix = re.compile(r'_(zero|one|two|few|many|other)$')
placeholder = re.compile(r'\{\{(\w+)\}\}')
english = json.load(open('shared/strings/en.json'))
allowed = {}
for key, value in english.items():
    base = suffix.sub('', key)
    allowed.setdefault(base, set()).update(placeholder.findall(value))
    allowed[base].add('count') if suffix.search(key) else None

warnings = []
for tag in sorted(files - {'en'}):
    text = open('shared/strings/%s.json' % tag).read().strip()
    if not text:
        continue
    try:
        translation = json.loads(text)
    except ValueError as problem:
        errors.append('%s.json is not valid JSON: %s' % (tag, problem))
        continue
    for key, value in translation.items():
        base = suffix.sub('', key)
        if base not in allowed or (key not in english and not suffix.search(key)):
            warnings.append('%s.json has %s, which en.json does not' % (tag, key))
        elif value and not set(placeholder.findall(value)) <= allowed[base]:
            errors.append('%s.json %s uses placeholders en.json does not: %s' % (tag, key, sorted(set(placeholder.findall(value)) - allowed[base])))

for warning in warnings:
    print('warning: ' + warning)
if errors:
    print('\n'.join(errors))
    sys.exit(1)
PY
