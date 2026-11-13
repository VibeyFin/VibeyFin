#!/bin/bash
# Fails when the shipped languages disagree: shared/strings/*.json, shared/languages.json, Android's locales_config.xml and
# iOS's CFBundleLocalizations must list the same tags, each needs a plural rule, and every translation must use only keys
# and {{placeholders}} that en.json has.
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

sources = {'shared/strings/*.json': files, 'shared/languages.json': set(listed), 'locales_config.xml': android, 'CFBundleLocalizations': ios}
for label, tags in sources.items():
    for other_label, other in sources.items():
        if label < other_label:
            for tag in sorted(tags - other):
                errors.append('%s has %s but %s does not' % (label, tag, other_label))
            for tag in sorted(other - tags):
                errors.append('%s has %s but %s does not' % (other_label, tag, label))

rules = open('shared/src/commonMain/kotlin/app/vibeyfin/i18n/PluralRules.kt').read()
for tag in sorted(files):
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

for tag in sorted(files - {'en'}):
    translation = json.load(open('shared/strings/%s.json' % tag))
    for key, value in translation.items():
        base = suffix.sub('', key)
        if base not in allowed or (key not in english and not suffix.search(key)):
            errors.append('%s.json has %s, which en.json does not' % (tag, key))
        elif not set(placeholder.findall(value)) <= allowed[base]:
            errors.append('%s.json %s uses placeholders en.json does not: %s' % (tag, key, sorted(set(placeholder.findall(value)) - allowed[base])))

if errors:
    print('\n'.join(errors))
    sys.exit(1)
PY
