#!/usr/bin/env bash
set -ex
for i in target/sqlite-native/*/*/*; do
  if [[ -f "$i" ]] && [[ $i != "*.sha256" ]]; then
    echo "$i.sha256"
    shasum -a 256 "$i" | head -c 64 >"$i.sha256"
  fi
done
