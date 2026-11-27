#!/usr/bin/env bash
set -euo pipefail

# Runs the example-geology scenario on a burner Multiplexor server through Adapt's gameplay runner.
# Usage: examples/qa/run.sh <paper|fabric|forge|neoforge> <minecraft version> <example jar>
# The runner boots once without the example, stages the jar, restarts, runs the live checks, removes the jar,
# restarts, checks the kept save, puts the jar back, restarts and checks the progress returns. It deletes the instance.

platform="$1"
minecraft_version="$2"
example_jar="$(cd "$(dirname "$3")" && pwd)/$(basename "$3")"
examples="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
gameplay="$examples/../bukkit/src/test/gameplay"
scenario="example-geology-$platform"
link="$gameplay/$scenario.mjs"

[[ -f "$example_jar" ]] || { echo "Example jar missing: $example_jar" >&2; exit 2; }
[[ -e "$link" ]] && { echo "Scenario link already present: $link" >&2; exit 2; }
ln -s "$examples/qa/example-geology.mjs" "$link"
trap 'rm -f "$link"' EXIT

EXAMPLE_GEOLOGY_JAR="$example_jar" ADAPT_QA_MULTIPLEXOR="${ADAPT_QA_MULTIPLEXOR:?set ADAPT_QA_MULTIPLEXOR to the ServerMultiplexor folder}" ADAPT_QA_PREFIX="${ADAPT_QA_PREFIX:-l11-adapt-}" \
    bash "$gameplay/platform-run.sh" "$platform" "$minecraft_version" \
    "$scenario=stage,restart,$scenario=live,restart,$scenario=removed,restart,$scenario=restored"
