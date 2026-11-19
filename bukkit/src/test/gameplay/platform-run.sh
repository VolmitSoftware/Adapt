#!/usr/bin/env bash
set -euo pipefail

main() {

if [[ $# -lt 2 || "$1" == "--help" ]]; then
    echo 'Usage: bash bukkit/src/test/gameplay/platform-run.sh <paper|folia|fabric|forge|neoforge> <minecraft version> [scenario] [case,case,...]'
    echo 'Creates a burner <prefix><platform>-* Multiplexor instance with Adapt and the gameplay fixture, runs one scenario'
    echo '(default adaptation-smoke: every adaptation contract, incomplete coverage reported), and deletes the instance.'
    echo 'Artifacts are taken as built: Bukkit from build/libs, mods from adapters/<loader>/build/<version>/{libs,gameplay}.'
    echo 'Overrides: ADAPT_QA_ADAPT_JAR, ADAPT_QA_FIXTURE_JAR, ADAPT_QA_MULTIPLEXOR, ADAPT_QA_VIAPROXY_JAR, ADAPT_QA_HEAP (default 4G),'
    echo 'ADAPT_QA_PREFIX (instance name prefix, default g1-adapt-), ADAPT_QA_JVM_ARGS (extra server JVM flags),'
    echo 'ADAPT_QA_INSTANCE_TAG (added to the instance name, e.g. s2 for a suite shard), ADAPT_QA_CONTRACT_SLICE=1 (the case list'
    echo 'is a slice of the full contract run: Gloss stays absent as in the full run instead of being installed for discovery-insight).'
    echo 'Instance names are <prefix><platform>-<mc>[-<tag>]-<epoch>-<pid>. Locks in TMPDIR serialize Multiplexor create/start calls'
    echo '(no two runs pick the same server port) and server boots up to "Done" (boots are CPU heavy; concurrent runs boot one at a time).'
    echo 'Forge/NeoForge installer creates take the create-<loader> lock (one installer per loader at a time: a create prunes the other cached installers of its loader). ADAPT_QA_TEMPLATE_INSTANCE=<installed instance of the'
    echo 'same prefix and loader> clones it (Multiplexor instance clone) instead of running the installer (platform-suite.sh does this).'
    echo 'Every bot connect goes through bot-connect.mjs: a connect stuck in the configuration phase (ViaProxy stall) is reconnected'
    echo 'before the scenario sees it, at most 3 attempts, each printed as "RETRY connect [<step>] <name>: ...".'
    echo 'Paper/Folia only: Iris (+ native-terrain pack) is installed from ../Iris build/libs when the case list is empty or names'
    echo 'axe-iris-feller; Gloss (../Gloss, Paper only) only when the case list names discovery-insight, because it reroutes golem'
    echo 'status labels. Override with ADAPT_QA_IRIS_JAR, ADAPT_QA_GLOSS_JAR. ADAPT_QA_EXTRA_PLUGINS'
    echo 'adds comma-separated plugin jars. React (../React/React/build/libs, ADAPT_QA_REACT_JAR) is installed for experience-awards.'
    echo 'Plugin library caches persist under build/gameplay/dependencies.'
    echo 'The bots are plain 26.1 mineflayer clients without the Adapt mod; every version but 26.1.2 goes through a local ViaProxy.'
    echo 'Below 26 the server runs on its own Java (1.21.1: 21, 1.20.1: 17), set per instance with Multiplexor runtime settings set-java:'
    echo 'ADAPT_QA_JAVA, else JAVA_HOME_<major>, /opt/homebrew/opt/openjdk@<major>, java_home -v <major>, ~/.gradle/jdks.'
    echo 'Jars: Bukkit = newest "Adapt v* [CraftBukkit] <low>-<high>.jar" covering the version in ADAPT_QA_BUKKIT_LIBS (colon-separated'
    echo 'directories, default build/libs and build/bukkit-<mc>/libs), fixture beside it; mods = "Adapt v* [<Loader>] <mc>+*.jar" in adapters/<loader>/build/<mc>/libs'
    echo '(NeoForge 1.20.1 also takes the Forge 1.20.1 jar), fixture in ../gameplay. ADAPT_QA_BARE=1 installs neither (server reach checks).'
    echo 'ADAPT_QA_VERSION_CATALOG=<json|tsv>: adaptations a version disables report N/A with the catalog reason (see version-catalog.mjs).'
    echo 'Unset: core/src/test/resources/platform-support/<mc>.tsv when core has one (1.21.1, 1.20.1); set it empty to use no catalog.'
    echo 'Scenario steps that need a disabled adaptation or content the version lacks print "[N/A] <step>: <reason>" under CASES.'
    echo 'The scenario argument may also be a plan of comma-separated steps on the same instance: <scenario>, <scenario>=<command>'
    echo 'or restart (full server stop and start). @<scenario> in a command is replaced by that earlier step'"'"'s report JSON,'
    echo 'e.g. all-skills,restart,retained-skills=@all-skills. Each step prints "STEP <scenario> <pass|fail>"; a repeated scenario'
    echo 'reports as <scenario>-2.json, -3.json, and @<scenario> always names the first run.'
    exit 0
fi

platform="$1"
minecraft_version="$2"
scenario="${3:-adaptation-smoke}"
cases="${4:-}"
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
gameplay="$project_root/bukkit/src/test/gameplay"
multiplexor_root="${ADAPT_QA_MULTIPLEXOR:-$(dirname "$project_root")/ServerMultiplexor}"
harness="$multiplexor_root/MultiplexorApp/tool/mineflayer/src/cli.mjs"
case "$platform" in
    paper|folia) consumer=plugin; content=plugins ;;
    fabric|forge|neoforge) consumer="$platform"; content=mods ;;
    *) echo "Unknown platform: $platform" >&2; exit 2 ;;
esac
[[ -x "$multiplexor_root/start.sh" ]] || { echo "Multiplexor missing: $multiplexor_root" >&2; exit 2; }
[[ -f "$harness" ]] || { echo "Mineflayer harness missing: $harness (run: start.sh gameplay setup)" >&2; exit 2; }
IFS=',' read -r -a steps <<< "$scenario"
for step in "${steps[@]}"; do
    [[ "$step" == restart || -f "$gameplay/${step%%=*}.mjs" ]] || { echo "Unknown scenario: ${step%%=*}" >&2; exit 2; }
done

version_profile="$(node "$gameplay/version-profile.mjs" shell "$minecraft_version")" || exit 2
eval "$version_profile"
export ADAPT_QA_MINECRAFT_VERSION="$minecraft_version"
bare="${ADAPT_QA_BARE:-0}"
if [[ "$bare" == 1 ]]; then
    adapt_jar=''
    fixture_jar=''
elif [[ -n "${ADAPT_QA_ADAPT_JAR:-}" ]]; then
    adapt_jar="$ADAPT_QA_ADAPT_JAR"
    if [[ "$consumer" == plugin ]]; then
        fixture_jar="${ADAPT_QA_FIXTURE_JAR:-$project_root/build/libs/AdaptGameplayFixture.jar}"
    else
        fixture_jar="${ADAPT_QA_FIXTURE_JAR:-$project_root/adapters/$platform/build/$minecraft_version/gameplay/AdaptGameplayFixture.jar}"
    fi
else
    artifacts="$(node "$gameplay/version-profile.mjs" artifacts "$minecraft_version" "$platform" "$project_root")" || exit 2
    adapt_jar="$(sed -n 1p <<< "$artifacts")"
    fixture_jar="${ADAPT_QA_FIXTURE_JAR:-$(sed -n 2p <<< "$artifacts")}"
fi
if [[ "$bare" != 1 ]]; then
    [[ -f "$adapt_jar" ]] || { echo "Adapt artifact missing for $platform $minecraft_version: $adapt_jar" >&2; exit 2; }
    [[ -f "$fixture_jar" ]] || { echo "Fixture artifact missing: $fixture_jar" >&2; exit 2; }
fi
# Server Java for versions below 26 (26.x keeps the Multiplexor default, Java 25 on PATH).
java_executable() {
    local major="$1" home_variable="JAVA_HOME_$1" candidate system_home gradle_home
    system_home="$(/usr/libexec/java_home -v "$major" 2>/dev/null || true)"
    gradle_home="$(ls -d "$HOME"/.gradle/jdks/*-"$major"-*/*/Contents/Home 2>/dev/null | head -1 || true)"
    for candidate in "${ADAPT_QA_JAVA:-}" "${!home_variable:+${!home_variable}/bin/java}" "/opt/homebrew/opt/openjdk@$major/bin/java" \
        "${system_home:+$system_home/bin/java}" "${gradle_home:+$gradle_home/bin/java}"; do
        [[ -n "$candidate" && -x "$candidate" ]] || continue
        "$candidate" -version 2>&1 | grep -qE "version \"$major[.\"]" && { echo "$candidate"; return 0; }
    done
    echo "No Java $major found: set ADAPT_QA_JAVA or JAVA_HOME_$major" >&2
    return 1
}
server_java=''
if [[ "$profile_java_major" != 25 ]]; then server_java="$(java_executable "$profile_java_major")" || exit 2; fi

prefix="${ADAPT_QA_PREFIX:-g1-adapt-}"
case "$prefix" in *-adapt-) ;; *) echo "ADAPT_QA_PREFIX must end in -adapt-: $prefix" >&2; exit 2 ;; esac
instance="$prefix$platform-${minecraft_version//./}${ADAPT_QA_INSTANCE_TAG:+-$ADAPT_QA_INSTANCE_TAG}-$(date +%s)-$$"
output="$project_root/build/gameplay/reports/$instance"
mkdir -p "$output"
admin="G1Admin$((RANDOM % 900 + 100))"
mux() { (cd "$multiplexor_root" && ./start.sh --consumer "$consumer" "$@"); }
owned_instance_exists() {
    with_lock multiplexor mux instance list 2>/dev/null | sed 's/ (active)//; s/^[[:space:]]*//; s/[[:space:]]*$//' | grep -qx "$instance"
}
dependencies="$project_root/build/gameplay/dependencies"
source "$gameplay/qa-lock.sh"
copy_dependency_cache() {
    local relative
    for relative in plugins/Gloss/.libs plugins/Iris/cache/libraries libraries/org/lwjgl; do
        if [[ -d "$1/$relative" ]]; then mkdir -p "$2/$relative" && cp -R "$1/$relative/." "$2/$relative/"; fi
    done
}
selected() { [[ ",$scenario," =~ ,(adaptation-smoke|all-adaptations)[,=] ]] && [[ -z "$cases" || ",$cases," == *",$1,"* ]]; }
planned() { [[ ",$scenario," =~ ,$1[,=] ]]; }
plugin_jar() {
    local name="$1" configured="$2"
    if [[ -n "$configured" ]]; then
        [[ -f "$configured" ]] || { echo "$name jar missing: $configured" >&2; return 1; }
        echo "$configured"
        return 0
    fi
    local found
    found="$(ls -t "$(dirname "$project_root")/$name/build/libs/$name v"*.jar "$(dirname "$project_root")/$name/$name/build/libs/$name v"*.jar 2>/dev/null | head -1 || true)"
    [[ -n "$found" ]] || { echo "Build $name or set ADAPT_QA_$(echo "$name" | tr '[:lower:]' '[:upper:]')_JAR" >&2; return 1; }
    echo "$found"
}
# A plugin built for a newer Bukkit API than this server's refuses to load; its integration is not applicable here.
plugin_api_newer() {
    local api
    api="$(unzip -p "$1" plugin.yml paper-plugin.yml 2>/dev/null | sed -n "s/^api-version:[[:space:]]*[\"']\{0,1\}\([0-9][0-9.]*\).*/\1/p" | head -1)"
    [[ -n "$api" ]] && node --input-type=module -e "import { compareVersions } from '$gameplay/version-profile.mjs'; process.exit(compareVersions(process.argv[1], process.argv[2]) > 0 ? 0 : 1)" "$api" "$minecraft_version" \
        && echo "$api"
}
created=false
instance_path=''
proxy_pid=''
proxy_dir=''
cleanup() {
    local result=$?
    trap - EXIT
    release_own_locks
    if [[ -n "$proxy_pid" ]]; then kill "$proxy_pid" 2>/dev/null || true; wait "$proxy_pid" 2>/dev/null || true; fi
    if [[ -n "$proxy_dir" ]]; then cp "$proxy_dir/via.log" "$output/viaproxy.log" 2>/dev/null || true; rm -rf "$proxy_dir"; fi
    if [[ "$created" == true ]]; then
        case "$instance" in "$prefix"*) ;; *) echo "Refusing to touch $instance" >&2; exit 1 ;; esac
        if owned_instance_exists; then
            with_lock multiplexor mux runtime stop "$instance" > "$output/stop.log" 2>&1 || result=1
            [[ -f "$instance_path/logs/latest.log" ]] && cp "$instance_path/logs/latest.log" "$output/server.log"
            [[ "$content" == plugins && -d "$instance_path/plugins" ]] && { with_lock dependencies copy_dependency_cache "$instance_path" "$dependencies" || result=1; }
            if owned_instance_exists; then
                with_lock multiplexor mux instance delete "$instance" > "$output/delete.log" 2>&1 || result=1
            fi
        fi
        if owned_instance_exists; then echo "Instance still present: $instance" >&2; result=1; fi
    fi
    echo "Gameplay evidence: $output"
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'echo "platform-run failed at line $LINENO" >&2' ERR

property() {
    python3 - "$instance_path/server.properties" "$1" "$2" <<'PYTHON'
import sys
from pathlib import Path

path, key, value = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
lines = [line for line in path.read_text().splitlines() if not line.startswith(key + '=')] if path.exists() else []
path.write_text('\n'.join(lines + [f'{key}={value}']) + '\n')
PYTHON
}

if owned_instance_exists; then echo "Instance name already in use: $instance" >&2; exit 1; fi
create_started=$SECONDS
template="${ADAPT_QA_TEMPLATE_INSTANCE:-}"
if [[ -n "$template" ]]; then
    case "$template" in "$prefix$platform-"*) ;; *) echo "Template is not a $prefix$platform- instance: $template" >&2; exit 2 ;; esac
    created=true
    clone_template() {
        local started=$SECONDS
        mux instance clone "$template" "$instance" > "$output/create.log" 2>&1
        clone_seconds=$((SECONDS - started))
    }
    with_lock multiplexor clone_template
    echo "Instance created in $((SECONDS - create_started)) s (clone of $template: copy $clone_seconds s, lock wait $((SECONDS - create_started - clone_seconds)) s)"
else
    create_lock=multiplexor
    installer_loader "$platform" && create_lock="create-$platform"
    source_args=()
    while IFS= read -r argument; do source_args+=("$argument"); done < <(server_source_args "$platform" "$minecraft_version" "$project_root")
    if ! with_lock "$create_lock" mux server create "$instance" "${source_args[@]}" --isolated > "$output/create.log" 2>&1; then
        created=true
        with_lock "$create_lock" mux server create "$instance" "${source_args[@]}" --isolated --auto-build >> "$output/create.log" 2>&1
    fi
    echo "Instance created in $((SECONDS - create_started)) s"
fi
created=true
owned_instance_exists || { echo "Instance was not created: $instance" >&2; tail -5 "$output/create.log" >&2; exit 1; }
instance_path="$(with_lock multiplexor mux instance path "$instance" | tail -1)"
[[ -d "$instance_path" ]] || { echo "Instance path unknown: $instance_path" >&2; exit 1; }
if [[ -n "$server_java" ]]; then
    with_lock multiplexor mux runtime settings set-java "$server_java" --instance "$instance" > "$output/java.log" 2>&1 \
        || { echo "Could not select Java $profile_java_major ($server_java); see $output/java.log" >&2; exit 1; }
fi
mkdir -p "$instance_path/$content"
if [[ "$bare" != 1 ]]; then
    cp "$adapt_jar" "$instance_path/$content/Adapt.jar"
    cp "$fixture_jar" "$instance_path/$content/AdaptGameplayFixture.jar"
fi
unset ADAPT_QA_IRIS_WORLD ADAPT_QA_ABSENT_INTEGRATIONS ADAPT_QA_REACT ADAPT_QA_REACT_ABSENT
if [[ "$content" == plugins ]]; then
    if selected axe-iris-feller; then
        iris_jar="$(plugin_jar Iris "${ADAPT_QA_IRIS_JAR:-}")" || exit 2
        if iris_api="$(plugin_api_newer "$iris_jar")"; then
            echo "Iris needs Bukkit API $iris_api; not installed on Minecraft $minecraft_version" >&2
        else
            cp "$iris_jar" "$instance_path/plugins/Iris.jar"
            mkdir -p "$instance_path/plugins/Iris/packs"
            cp -R "$(dirname "$project_root")/Iris/adapters/bukkit/plugin/src/test/gameplay/native-terrain-pack" "$instance_path/plugins/Iris/packs/native-terrain"
            export ADAPT_QA_IRIS_WORLD='name=adapt_gameplay_iris type=native-terrain seed=78264193'
        fi
    fi
    if selected discovery-insight && [[ "$platform" == folia ]]; then
        export ADAPT_QA_ABSENT_INTEGRATIONS='gloss=Gloss 3.3.0 touches hologram entities off their owning region on Folia (ViewerDisplayBudget hideEntity, RoleSnapshotPendingException)'
    elif selected discovery-insight && [[ -z "$cases" || "${ADAPT_QA_CONTRACT_SLICE:-0}" == 1 ]]; then
        export ADAPT_QA_ABSENT_INTEGRATIONS='gloss=Gloss reroutes golem and servant status labels, so it is installed only when the case list names discovery-insight'
    elif selected discovery-insight; then
        gloss_jar="$(plugin_jar Gloss "${ADAPT_QA_GLOSS_JAR:-}")" || exit 2
        if gloss_api="$(plugin_api_newer "$gloss_jar")"; then
            export ADAPT_QA_ABSENT_INTEGRATIONS="gloss=the Gloss plugin needs Bukkit API $gloss_api; not applicable on Minecraft $minecraft_version"
        else
            cp "$gloss_jar" "$instance_path/plugins/Gloss.jar"
        fi
    fi
    if planned experience-awards; then
        react_jar="$(plugin_jar React "${ADAPT_QA_REACT_JAR:-}")" || exit 2
        if react_api="$(plugin_api_newer "$react_jar")"; then
            export ADAPT_QA_REACT_ABSENT="the React plugin needs Bukkit API $react_api; not applicable on Minecraft $minecraft_version"
        else
            cp "$react_jar" "$instance_path/plugins/React.jar"
            export ADAPT_QA_REACT=1
        fi
    fi
    if [[ "$platform" == folia && ",$scenario," =~ ,golem-folia[,=] ]]; then
        golem_fixture="${ADAPT_QA_GOLEM_FOLIA_FIXTURE_JAR:-$project_root/build/libs/AdaptGolemFoliaFixture.jar}"
        [[ -f "$golem_fixture" ]] || { echo "Folia golem fixture missing: $golem_fixture" >&2; exit 2; }
        cp "$golem_fixture" "$instance_path/plugins/AdaptGolemFoliaFixture.jar"
    fi
    if [[ -n "${ADAPT_QA_EXTRA_PLUGINS:-}" ]]; then
        IFS=',' read -r -a extra_plugins <<< "$ADAPT_QA_EXTRA_PLUGINS"
        for extra in "${extra_plugins[@]}"; do
            [[ -f "$extra" ]] || { echo "Extra plugin missing: $extra" >&2; exit 2; }
            cp "$extra" "$instance_path/plugins/"
        done
    fi
    with_lock dependencies copy_dependency_cache "$dependencies" "$instance_path"
else
    [[ -z "${ADAPT_QA_EXTRA_PLUGINS:-}" ]] || { echo "ADAPT_QA_EXTRA_PLUGINS applies to paper and folia only" >&2; exit 2; }
    export ADAPT_QA_ABSENT_INTEGRATIONS="gloss=needs the Gloss plugin, which exists only for Bukkit servers (not applicable on $platform)"
fi
with_lock multiplexor mux gameplay prepare "$instance" > "$output/prepare.log" 2>&1
property use-native-transport false
if [[ "$content" == mods || "$platform" == folia ]]; then
    level="$(grep "^level-name=" "$instance_path/server.properties" 2>/dev/null | cut -d= -f2 || true)"
    pack="$instance_path/${level:-world}/datapacks/adaptqa"
    mkdir -p "$pack"
    cp -R "$project_root/adapters/mod-common/src/gameplayFixture/datapack/data" "$pack/"
    node "$gameplay/version-profile.mjs" pack-meta "$minecraft_version" "$project_root/gradle/targets.json" > "$pack/pack.mcmeta"
    property level-seed 424242
    property level-type minecraft:flat
    property generator-settings '{"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":3}],"lakes":false,"features":false,"structure_overrides":[]}'
    property initial-enabled-packs 'vanilla,file/adaptqa'
fi
node -e '
const { createHash } = require("node:crypto")
const name = process.argv[1]
const hash = createHash("md5").update(`OfflinePlayer:${name}`).digest()
hash[6] = (hash[6] & 0x0f) | 0x30
hash[8] = (hash[8] & 0x3f) | 0x80
const hex = hash.toString("hex")
const uuid = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
require("node:fs").writeFileSync(process.argv[2], JSON.stringify([{ uuid, name, level: 4, bypassesPlayerLimit: false }], null, 2))
' "$admin" "$instance_path/ops.json"

log="$instance_path/logs/latest.log"
launch_server() {
    JVM_ARGS="-XX:+UseG1GC ${ADAPT_QA_JVM_ARGS:-}" HEAP_SIZE="${ADAPT_QA_HEAP:-4G}" with_lock multiplexor mux runtime start "$instance" --no-console >> "$output/start.log" 2>&1 \
        || { echo "Runtime start failed; see $output/start.log" >&2; exit 1; }
}
boot_slots='boot boot-2'
boot_hold="${ADAPT_QA_BOOT_HOLD:-180}"
server_state() {
    if [[ ! -f "$log" ]]; then echo silent
    elif grep -q 'Done (' "$log"; then echo ready
    elif grep -qE 'Crash report|---- Minecraft Crash|Exception in server tick loop|Stopping server' "$log"; then echo crashed
    else echo booting
    fi
}
# Waits up to $1 seconds; a crash, or no log at all by then, fails the run. A boot still in progress returns so the
# caller can release its boot slot: one slow or dead boot can hold a slot for at most ADAPT_QA_BOOT_HOLD seconds.
await_server() {
    local state _
    for _ in $(seq 1 $(($1 * 5))); do
        state="$(server_state)"
        [[ "$state" == ready || "$state" == crashed ]] && break
        sleep 0.2
    done
    case "$state" in
        crashed) echo "Server did not start; see $log" >&2; exit 1 ;;
        silent) echo "Server wrote no log within $1 s; see $output/start.log" >&2; exit 1 ;;
    esac
    return 0
}
finish_boot() {
    [[ "$(server_state)" == ready ]] && return 0
    await_server 600
    [[ "$(server_state)" == ready ]] || { echo "Server did not start; see $log" >&2; exit 1; }
}
start_server() {
    launch_server
    await_server "$boot_hold"
}
wait_for_adapt() {
    [[ "$bare" == 1 ]] && return 0
    for _ in $(seq 1 600); do grep -q 'Adapt core started\|Enabling Adapt\|Adapt.*enabled' "$log" && break; sleep 0.2; done
}
# Proxy-side compression stays off. With it on, ViaProxy 3.4.14 sends its own login compression to the bot and
# re-enables reads from the server on the bot connection's thread while the login handler disables them on the
# server connection's thread. The two share an unsynchronized auto-read stack; about 1 in 320 logins loses the
# race and the server connection never reads again (bot stuck in configuration, server "Timed out" after 30 s)
# or logs "Race condition detected: Auto read has been enabled somewhere else".
launch_proxy() {
    proxy_dir="$(mktemp -d "${TMPDIR:-/tmp}/${prefix}viaproxy.XXXXXX")"
    (cd "$proxy_dir" && exec java -jar "$proxy_jar" cli --bind-address "127.0.0.1:$bot_port" --target-address "127.0.0.1:$port" \
        --target-version "$minecraft_version" --auth-method NONE --compression-threshold -1 > via.log 2>&1) &
    proxy_pid=$!
}
await_proxy() {
    for _ in $(seq 1 450); do grep -q 'Binding proxy server' "$proxy_dir/via.log" 2>/dev/null && break; sleep 0.2; done
    grep -q 'Binding proxy server' "$proxy_dir/via.log" || { echo "ViaProxy did not start" >&2; exit 1; }
}
start_proxy() {
    launch_proxy
    await_proxy
}
stop_proxy() {
    kill "$proxy_pid" 2>/dev/null || true
    wait "$proxy_pid" 2>/dev/null || true
    proxy_pid=''
    cp "$proxy_dir/via.log" "$output/viaproxy-$(date +%s).log" 2>/dev/null || true
    rm -rf "$proxy_dir"
    proxy_dir=''
}
restart_server() {
    with_lock multiplexor mux runtime stop "$instance" >> "$output/stop.log" 2>&1
    [[ -f "$log" ]] && mv "$log" "$output/restart-$(date +%s)-server.log"
    with_lock "$boot_slots" start_server
    finish_boot
    wait_for_adapt
    local restarted_port
    restarted_port="$(grep '^server-port=' "$instance_path/server.properties" | cut -d= -f2)"
    if [[ "$restarted_port" != "$port" ]]; then
        port="$restarted_port"
        if [[ -n "$proxy_pid" ]]; then
            stop_proxy
            start_proxy
        else
            bot_port="$port"
        fi
    fi
}
proxy_jar="${ADAPT_QA_VIAPROXY_JAR:-$project_root/build/gameplay/tools/ViaProxy-3.4.14.jar}"
if [[ "$profile_proxy" == true && ! -f "$proxy_jar" ]]; then
    mkdir -p "$(dirname "$proxy_jar")"
    curl -fsSL -o "$proxy_jar" https://github.com/ViaVersion/ViaProxy/releases/download/v3.4.14/ViaProxy-3.4.14.jar
fi
first_boot() {
    launch_server
    port="$(grep '^server-port=' "$instance_path/server.properties" | cut -d= -f2)"
    bot_port="$port"
    if [[ "$profile_proxy" == true ]]; then
        bot_port="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')"
        launch_proxy
    fi
    await_server "$boot_hold"
}
with_lock "$boot_slots" first_boot
finish_boot
[[ -n "$proxy_pid" ]] && await_proxy
wait_for_adapt

login_stall() {
    node -e '
const report = JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8"))
const spawned = new Set((report.actors ?? []).map(actor => actor.username?.toLowerCase()))
const message = report.errors?.[0]?.message ?? ""
const match = /^([A-Za-z0-9_]{1,16}) (?:disconnected: socketClosed|spawn timed out after \d+ms)$/.exec(message)
if (!match || spawned.has(match[1].toLowerCase()) || (report.steps ?? []).length > 0) process.exit(1)
console.log(match[1])
' "$1" 2>/dev/null
}
run_step() {
    local name="$1" command="$2" status=0 label="$1" round=1
    while [[ -e "$output/$label.json" ]]; do round=$((round + 1)); label="$name-$round"; done
    local scenario_module="$output/$label.scenario.mjs"
    printf "import scenario from '%s'\nimport { guardedScenario } from '%s'\n\nexport default guardedScenario(scenario)\n" \
        "file://$gameplay/$name.mjs" "file://$gameplay/operator-guard.mjs" > "$scenario_module"
    local -a options=(--scenario "$scenario_module" --host 127.0.0.1 --port "$bot_port" --instance "$instance"
        --instance-directory "$instance_path" --username "$admin" --auth offline --version "$profile_client_version"
        --timeout 43200 --connect-timeout 90 --assertion-timeout 15 --artifacts "$output" --log-path "$log" --no-viewer --json)
    [[ -n "$command" ]] && options+=(--command "$command")
    local marker="$output/.started" attempt stalled
    touch "$marker"
    for attempt in 1 2 3; do
        status=0
        ADAPT_QA_RESILIENT_CONNECT=1 node --import "file://$gameplay/bot-connect.mjs" "$harness" run "${options[@]}" \
            > "$output/$label.json" 2> "$output/$label.stderr.log" || status=$?
        sed -n "s/^RETRY connect /RETRY connect [$label] /p" "$output/$label.stderr.log"
        [[ $status -ne 0 && $attempt -lt 3 ]] || break
        stalled="$(login_stall "$output/$label.json")" || break
        echo "RETRY $name: $stalled never spawned (login stall), attempt $attempt kept as $label.stall-$attempt.*"
        mv "$output/$label.json" "$output/$label.stall-$attempt.json"
        mv "$output/$label.stderr.log" "$output/$label.stall-$attempt.stderr.log"
    done
    cp "$log" "$output/$label-server.log"
    for directory in $(find "$project_root/build/gameplay/reports" -maxdepth 1 -type d -name 'cases-*' -newer "$marker"); do
        grep -q "\"serverDirectory\": \"$instance_path\"" "$directory/manifest.json" 2>/dev/null && mv "$directory" "$output/"
    done
    rm -f "$marker"
    local passed failed na
    passed="$(grep -c '^\[PASS\]' "$output/$label.stderr.log" || true)"
    failed="$(grep -c '^\[FAIL\]' "$output/$label.stderr.log" || true)"
    na="$(sed -n 's/^\[N\/A\] \([^:]*\):.*/\1/p' "$output/$label.stderr.log" | paste -sd, -)"
    if [[ $passed -gt 0 || $failed -gt 0 || -n "$na" ]]; then
        echo "CASES $label passed=$passed failed=$failed not-applicable=${na:-none}"
        grep -E '^\[(FAIL|N/A)\]' "$output/$label.stderr.log" || true
    fi
    return "$status"
}
result=0
for step in "${steps[@]}"; do
    if [[ "$step" == restart ]]; then
        restart_server
        continue
    fi
    name="${step%%=*}"
    command="$cases"
    if [[ "$step" == *=* ]]; then
        command="${step#*=}"
        while [[ "$command" =~ @([a-z0-9-]+) ]]; do
            command="${command//@${BASH_REMATCH[1]}/$output/${BASH_REMATCH[1]}.json}"
        done
    fi
    if run_step "$name" "$command"; then echo "STEP $name pass"; else echo "STEP $name fail"; result=1; fi
done
exit "$result"
}

main "$@"
