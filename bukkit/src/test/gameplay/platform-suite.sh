#!/usr/bin/env bash
set -uo pipefail

gameplay="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$gameplay/../../../.." && pwd)"
shards="1"
only=''
positional=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --shards) shards="${2:-}"; shift 2 ;;
        --only) only="${2:-}"; shift 2 ;;
        --help) node "$gameplay/suite-shards.mjs" usage; exit 0 ;;
        *) positional+=("$1"); shift ;;
    esac
done
[[ ${#positional[@]} -eq 3 && -z "$only" ]] && only="${positional[2]}" && positional=("${positional[@]:0:2}")
if [[ ${#positional[@]} -ne 2 || ! "$shards" =~ ^[1-9][0-9]?$ ]]; then
    node "$gameplay/suite-shards.mjs" usage >&2
    exit 2
fi
platform="${positional[0]}"
minecraft_version="${positional[1]}"
run_dir="$project_root/build/gameplay/reports/suite-$platform-${minecraft_version//./}-$(date +%s)-$$"
mkdir -p "$run_dir"
node "$gameplay/suite-shards.mjs" plan "$run_dir" "$platform" "$shards" "$only" || exit 2
echo "Suite run: $run_dir"

# Forge/NeoForge: run the installer once for the whole suite and let every unit clone the installed instance.
source "$gameplay/qa-lock.sh"
prefix="${ADAPT_QA_PREFIX:-g1-adapt-}"
multiplexor_root="${ADAPT_QA_MULTIPLEXOR:-$(dirname "$project_root")/ServerMultiplexor}"
mux() { (cd "$multiplexor_root" && ./start.sh --consumer "$platform" "$@"); }
template_exists() {
    with_lock multiplexor mux instance list 2>/dev/null | sed 's/ (active)//; s/^[[:space:]]*//; s/[[:space:]]*$//' | grep -qx "$template"
}
template=''
delete_template() {
    release_own_locks
    [[ -n "$template" ]] || return 0
    case "$template" in "$prefix$platform-"*) ;; *) echo "Refusing to touch $template" >&2; return 1 ;; esac
    template_exists && with_lock multiplexor mux instance delete "$template" >> "$run_dir/template.log" 2>&1
    if template_exists; then echo "Template still present: $template" >&2; return 1; fi
    template=''
}
if installer_loader "$platform" && [[ "$(wc -l < "$run_dir/plan.tsv")" -gt 1 ]]; then
    template="$prefix$platform-${minecraft_version//./}-tpl-$(date +%s)-$$"
    trap 'delete_template' EXIT
    started=$SECONDS
    lock="create-$platform"
    source_args=()
    while IFS= read -r argument; do source_args+=("$argument"); done < <(server_source_args "$platform" "$minecraft_version" "$project_root")
    if ! with_lock "$lock" mux server create "$template" "${source_args[@]}" --isolated > "$run_dir/template.log" 2>&1; then
        with_lock "$lock" mux server create "$template" "${source_args[@]}" --isolated --auto-build >> "$run_dir/template.log" 2>&1
    fi
    if template_exists && [[ -f "$(with_lock multiplexor mux instance path "$template" | tail -1)/.server-source" ]]; then
        echo "Template $template installed in $((SECONDS - started)) s; units clone it"
        export ADAPT_QA_TEMPLATE_INSTANCE="$template"
    else
        echo "Template install failed after $((SECONDS - started)) s (see $run_dir/template.log); units run the installer themselves"
        delete_template
    fi
fi

worker() {
    local shard="$1" index=0 status=0 child='' unit_shard group plan cases slice label
    trap - EXIT
    trap '[[ -n "$child" ]] && kill -TERM "$child" 2>/dev/null; [[ -n "$child" ]] && wait "$child"; exit 143' TERM
    while IFS=$'\t' read -r unit_shard group plan cases slice label; do
        if [[ "$unit_shard" == "$shard" ]]; then
            [[ "$cases" == - ]] && cases=''
            local tag=''
            [[ "$shards" -gt 1 ]] && tag="s$shard"
            ADAPT_QA_INSTANCE_TAG="$tag" ADAPT_QA_CONTRACT_SLICE="$slice" \
                bash "$gameplay/platform-run.sh" "$platform" "$minecraft_version" "$plan" $cases < /dev/null > "$run_dir/$index.log" 2>&1 &
            child=$!
            wait "$child"
            local unit_status=$?
            child=''
            echo "$unit_status" > "$run_dir/$index.exit"
            [[ $unit_status -eq 0 ]] || status=1
            node "$gameplay/suite-shards.mjs" block "$run_dir" "$index"
        fi
        index=$((index + 1))
    done < "$run_dir/plan.tsv"
    echo "$status" > "$run_dir/shard-$shard.exit"
}

workers=()
stop_workers() {
    trap - INT TERM
    kill -TERM "${workers[@]}" 2>/dev/null
    wait
    exit 130
}
trap stop_workers INT TERM
[[ -n "$template" ]] && trap 'delete_template' EXIT
for shard in $(cut -f1 "$run_dir/plan.tsv" | sort -nu); do
    worker "$shard" &
    workers+=($!)
done
wait
echo
node "$gameplay/suite-shards.mjs" summary "$run_dir" "$platform" "$minecraft_version" | tee "$run_dir/summary.txt"
status="${PIPESTATUS[0]}"
delete_template || status=1
exit "$status"
