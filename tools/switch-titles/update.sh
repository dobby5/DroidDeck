#!/usr/bin/env bash
# Rebuilds app/src/main/assets/switch-titles.tsv from the newest titledb commit (or the one given).
# Usage: tools/switch-titles/update.sh [titledb-commit-sha]
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
sha=${1:-$(curl -fsSL https://api.github.com/repos/blawar/titledb/commits/master | python3 -c 'import json,sys; print(json.load(sys.stdin)["sha"])')}
[[ $sha =~ ^[0-9a-f]{40}$ ]] || { echo "not a commit: $sha" >&2; exit 1; }
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
for region in US.en.json GB.en.json; do
    curl -fsSL --retry 3 -o "$work/$region" "https://raw.githubusercontent.com/blawar/titledb/$sha/$region"
done
python3 "$here/build_index.py" "$sha" "$work/US.en.json" "$work/GB.en.json"
sed -i "s/commit [0-9a-f]\{40\}/commit $sha/" "$root/app/src/main/assets/switch-titles.LICENSE.txt"
echo "switch-titles.tsv now from titledb $sha"
