#!/usr/bin/env bash
set -euo pipefail

mode="${1:-full}"
if [[ "$mode" == "--help" ]]; then
    echo 'Usage: bash src/test/gameplay/run.sh [full|smoke|skills] [Multiplexor workspace]'
    echo 'full requires positive behavior and feedback evidence for every adaptation; missing cases fail.'
    echo 'smoke checks implemented adaptation cases and reports incomplete coverage explicitly.'
    echo 'skills verifies natural XP earnings, payout, reconnect, and full server restart retention for all skills.'
    echo 'All modes build, create an isolated Paper instance, retain reports, and delete the instance.'
    echo 'ADAPT_QA_MC_VERSION selects the server version (default: 26.1.2); the gameplay harness must support its protocol.'
    echo 'Full mode also loads local Iris and Gloss builds; override with ADAPT_QA_IRIS_JAR and ADAPT_QA_GLOSS_JAR.'
    echo 'Full mode runs the native 26.2 client suite and requires matching-artifact Rubber Soul physics and feedback proof.'
    echo 'Integration dependency caches are retained under build/gameplay/dependencies for repeat runs.'
    exit 0
fi
case "$mode" in full|smoke|skills) ;; *) echo "Unknown mode: $mode" >&2; exit 2 ;; esac

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
multiplexor_root="${2:-$(dirname "$(dirname "$project_root")")/[Minecraft Server]}"
if [[ ! -x "$multiplexor_root/start.sh" ]]; then
    echo "Multiplexor entrypoint missing: $multiplexor_root/start.sh" >&2
    exit 2
fi
instance="adapt-acceptance-$(date +%s)-$$"
minecraft_version="${ADAPT_QA_MC_VERSION:-26.1.2}"
output="$project_root/build/gameplay/reports/$instance"
mkdir -p "$output"
created=false
instance_path=''
mux() { (cd "$multiplexor_root" && ./start.sh --consumer plugin "$@"); }
copy_dependency_cache() {
    python3 - "$1" "$2" <<'PYTHON'
import shutil
import sys
from pathlib import Path

source, target = map(Path, sys.argv[1:])
for relative in ('Gloss/.libs', 'Iris/cache/libraries'):
    directory = source / relative
    if directory.is_dir():
        shutil.copytree(directory, target / relative, dirs_exist_ok=True)
PYTHON
}
cleanup() {
    result=$?
    trap - EXIT
    if [[ "$created" == true ]]; then
        if ! mux runtime stop "$instance" --graceful > "$output/stop.log" 2>&1; then result=1; fi
        if [[ -f "$instance_path/logs/latest.log" ]]; then
            if ! cp "$instance_path/logs/latest.log" "$output/server.log"; then result=1; fi
        fi
        if [[ "$mode" == full ]] && ! copy_dependency_cache "$instance_path/plugins" "$project_root/build/gameplay/dependencies"; then result=1; fi
        if ! mux instance delete "$instance" > "$output/delete.log" 2>&1; then result=1; fi
    fi
    echo "Gameplay evidence: $output"
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
(cd "$project_root" && ./gradlew build prepareGameplay) > "$output/build.log" 2>&1
mux gameplay doctor --json > "$output/doctor.json"
mux server create "$instance" --type paper --mc "$minecraft_version" --auto-build --isolated
created=true
instance_path="$(mux instance path "$instance")"
cp "$project_root/build/gameplay/plugins/Adapt.jar" "$instance_path/plugins/"
cp "$project_root/build/gameplay/plugins/AdaptGameplayFixture.jar" "$instance_path/plugins/"
if [[ "$mode" == full ]]; then
    copy_dependency_cache "$project_root/build/gameplay/dependencies" "$instance_path/plugins"
    python3 - "$project_root" "$instance_path" <<'PY'
import os
import shutil
import sys
from pathlib import Path

project, instance = map(Path, sys.argv[1:])
for plugin in ('Iris', 'Gloss'):
    configured = os.environ.get(f'ADAPT_QA_{plugin.upper()}_JAR')
    candidates = [Path(configured)] if configured else list((project.parent / plugin / 'build/libs').glob(f'{plugin}*.jar'))
    candidates = [path for path in candidates if path.is_file() and not any(suffix in path.name for suffix in ('-sources', '-javadoc', '-api', '-packed', '-xz', 'unpacked'))]
    if not candidates:
        raise SystemExit(f'Build {plugin} or set ADAPT_QA_{plugin.upper()}_JAR before running full acceptance.')
    source = max(candidates, key=lambda path: path.stat().st_mtime)
    shutil.copy2(source, instance / 'plugins' / f'{plugin}.jar')
pack = project.parent / 'Iris/adapters/bukkit/plugin/src/test/gameplay/native-terrain-pack'
shutil.copytree(pack, instance / 'plugins/Iris/packs/native-terrain')
PY
fi
python3 - "$instance_path/server.properties" <<'PYTHON'
import sys
from pathlib import Path

path = Path(sys.argv[1])
lines = [line for line in path.read_text().splitlines() if not line.startswith('use-native-transport=')]
path.write_text('\n'.join(lines + ['use-native-transport=false']) + '\n')
PYTHON
mux gameplay prepare "$instance"
run_scenario() {
    local scenario="$1"
    local -a options=()
    if [[ $# -gt 1 ]]; then options+=(--command "$2"); fi
    local result=0
    node "$project_root/src/test/gameplay/evidence-manifest.mjs" "$instance_path" "$output/$scenario-inputs-before.json" "$scenario-before-start"
    mux gameplay run "$project_root/src/test/gameplay/$scenario.mjs" "$instance" \
        --start --stop-after --timeout 7200 --json "${options[@]}" > "$output/$scenario.json" || result=$?
    node "$project_root/src/test/gameplay/evidence-manifest.mjs" "$instance_path" "$output/$scenario-inputs-after.json" "$scenario-after-stop" || result=1
    cp "$instance_path/logs/latest.log" "$output/$scenario-server.log"
    if ! node "$project_root/src/test/gameplay/runtime-log.mjs" "$output/$scenario-server.log" > "$output/$scenario-errors.log"; then
        echo "Server errors detected: $output/$scenario-errors.log" >&2
        result=1
    fi
    return "$result"
}
if [[ "$mode" != smoke ]]; then
    run_scenario all-skills
    run_scenario retained-skills "$output/all-skills.json"
fi
case "$mode" in
    full)
        run_scenario feedback-calibration
        (cd "$project_root" && python3 src/test/client/run.py --skip-build --output "$output/native-client") > "$output/native-client.log" 2>&1
        run_scenario all-adaptations "$output/native-client/report.json"
        ;;
    smoke) run_scenario feedback-calibration; run_scenario adaptation-smoke ;;
esac
