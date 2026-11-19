# Machine-wide TMPDIR locks shared by every lane's gameplay runs. Sourced by platform-run.sh and platform-suite.sh.
# create-<loader> guards a Forge/NeoForge installer run (minutes) without blocking other loaders, so it may wait longer. It is
# per loader, not per version: every create prunes the cached installers of its loader that no instance uses yet, so a
# concurrent create of another version could lose its installer jar mid-install.
# Boots take one of two slots: "boot" (the name older harness copies still use) or "boot-2", and hold it only for a bounded time.
lock_wait_seconds() {
    case "$1" in
        create-*) echo 1800 ;;
        *) echo 600 ;;
    esac
}

# with_lock "<name> [<name> ...]" command...: runs the command holding the first free lock of the list.
with_lock() {
    local names="$1" wait holder name lock _
    wait="$(lock_wait_seconds "${names%% *}")"
    shift
    for _ in $(seq 1 $((wait * 5))); do
        for name in $names; do
            lock="${TMPDIR:-/tmp}/adapt-qa-$name.lock"
            if mkdir "$lock" 2>/dev/null; then
                echo "$$" > "$lock/pid"
                local status=0
                "$@" || status=$?
                rm -rf "$lock"
                return "$status"
            fi
            holder="$(cat "$lock/pid" 2>/dev/null || true)"
            if [[ -n "$holder" ]] && ! kill -0 "$holder" 2>/dev/null; then rm -rf "$lock"; fi
        done
        sleep 0.2
    done
    echo "Lock held too long: ${TMPDIR:-/tmp}/adapt-qa-{${names// /,}}.lock (waited $wait s)" >&2
    return 1
}

release_own_locks() {
    local lock
    for lock in "${TMPDIR:-/tmp}"/adapt-qa-*.lock; do
        [[ "$(cat "$lock/pid" 2>/dev/null)" == "$$" ]] && rm -rf "$lock"
    done
    return 0
}

installer_loader() {
    [[ "$1" == forge || "$1" == neoforge ]]
}

# Multiplexor server source for a create: "--type <platform> --mc <mc>", one argument per line. NeoForge 1.20.1 is
# net.neoforged:forge 47.1.x, which the Multiplexor's NeoForge resolver does not list (it reads net.neoforged:neoforge
# only), so its installer is downloaded once into build/gameplay/tools and passed with --jar (the installer path).
server_source_args() {
    local platform="$1" minecraft="$2" project_root="$3"
    printf '%s\n' --type "$platform" --mc "$minecraft"
    [[ "$platform" == neoforge && "$minecraft" == 1.20.1 ]] || return 0
    local version=1.20.1-47.1.106
    local jar="${ADAPT_QA_NEOFORGE_1201_INSTALLER:-$project_root/build/gameplay/tools/neoforge-$version-installer.jar}"
    if [[ ! -f "$jar" ]]; then
        mkdir -p "$(dirname "$jar")"
        curl -fsSL -o "$jar.part" "https://maven.neoforged.net/releases/net/neoforged/forge/$version/forge-$version-installer.jar" && mv "$jar.part" "$jar" || return 1
    fi
    printf '%s\n' --jar "$jar"
}
