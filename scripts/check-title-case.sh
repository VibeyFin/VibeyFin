#!/bin/bash
# Compiles and runs the pure-Swift checks for String.titleCased(). Needs swiftc only, not Xcode.
set -e
cd "$(dirname "$0")/.."
OUT="${TMPDIR:-/tmp}/vibeyfin-title-case-checks"
swiftc -o "$OUT" iosApp/iosApp/TitleCase.swift iosApp/Checks/main.swift
"$OUT"
