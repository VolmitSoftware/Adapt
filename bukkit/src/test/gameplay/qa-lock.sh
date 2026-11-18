# Machine-wide TMPDIR locks shared by every lane's gameplay runs. Sourced by platform-run.sh and platform-suite.sh.
# create-<loader>-<mc> guards a Forge/NeoForge installer run (minutes) without blocking other loaders, so it may wait longer.
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
