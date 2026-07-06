#!/usr/bin/env bash
set -euo pipefail

ANALYSIS_FOLDERS=("AdvanceCircos" "GenomeCompare" "MultipleCompare")
DATA_ROOT="${1:-}"

convert_java_properties_value() {
    local input="$1"
    local output=""
    local i=0
    local len=${#input}
    local ch=""

    while (( i < len )); do
        ch="${input:i:1}"
        if [[ "$ch" == "\\" && $((i + 1)) -lt len ]]; then
            ((i++))
            ch="${input:i:1}"
            case "$ch" in
                n) output+=$'\n' ;;
                r) output+=$'\r' ;;
                t) output+=$'\t' ;;
                *) output+="$ch" ;;
            esac
        else
            output+="$ch"
        fi
        ((i++))
    done

    printf '%s' "$output"
}

get_data_root_from_config() {
    local config_path="$HOME/.SimpleGenomeHub/SimpleGenomeHub.config"
    local line=""

    [[ -f "$config_path" ]] || {
        echo "Config file not found: $config_path" >&2
        return 1
    }

    while IFS= read -r line || [[ -n "$line" ]]; do
        [[ -z "$line" ]] && continue
        [[ "${line:0:1}" == "#" || "${line:0:1}" == "!" ]] && continue
        if [[ "$line" == data.root.directory=* ]]; then
            convert_java_properties_value "${line#data.root.directory=}"
            return 0
        fi
    done < "$config_path"

    echo "data.root.directory was not found in $config_path" >&2
    return 1
}

next_available_path() {
    local base_path="$1"
    local index=2
    local candidate=""

    if [[ ! -e "$base_path" ]]; then
        printf '%s' "$base_path"
        return 0
    fi

    while true; do
        candidate="${base_path}_migrated_${index}"
        if [[ ! -e "$candidate" ]]; then
            printf '%s' "$candidate"
            return 0
        fi
        ((index++))
    done
}

move_directory_contents() {
    local source_dir="$1"
    local target_dir="$2"
    local child=""
    local child_name=""
    local target_path=""

    mkdir -p "$target_dir"
    shopt -s nullglob dotglob
    for child in "$source_dir"/*; do
        [[ -e "$child" ]] || continue
        child_name="$(basename "$child")"
        target_path="$target_dir/$child_name"
        if [[ -e "$target_path" ]]; then
            target_path="$(next_available_path "$target_path")"
        fi
        mv "$child" "$target_path"
        printf 'Moved: %s -> %s\n' "$child" "$target_path"
    done
    shopt -u nullglob dotglob

    rmdir "$source_dir" 2>/dev/null || true
}

move_analysis_folder() {
    local species_dir="$1"
    local folder_name="$2"
    local source_dir="$species_dir/FunctionalAnnotation/$folder_name"
    local genome_analysis_dir="$species_dir/GenomeAnalysis"
    local target_dir="$genome_analysis_dir/$folder_name"

    [[ -d "$source_dir" ]] || return 1

    mkdir -p "$genome_analysis_dir"
    if [[ -d "$target_dir" ]]; then
        move_directory_contents "$source_dir" "$target_dir"
    else
        mv "$source_dir" "$target_dir"
        printf 'Moved: %s -> %s\n' "$source_dir" "$target_dir"
    fi
}

if [[ -z "$DATA_ROOT" ]]; then
    DATA_ROOT="$(get_data_root_from_config)"
fi

[[ -d "$DATA_ROOT" ]] || {
    echo "Data root directory does not exist: $DATA_ROOT" >&2
    exit 1
}

moved_count=0
for species_dir in "$DATA_ROOT"/*; do
    [[ -d "$species_dir" ]] || continue
    for folder_name in "${ANALYSIS_FOLDERS[@]}"; do
        if move_analysis_folder "$species_dir" "$folder_name"; then
            ((moved_count++))
        fi
    done
done

printf '\nFinished. Migrated %d analysis folder(s).\n' "$moved_count"
