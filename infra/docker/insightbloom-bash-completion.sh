#!/usr/bin/env bash
# bash completion for the InsightBloom CLI.
# Installed in the standard bash-completion directory by the IDE images.

_insightbloom_complete_values() {
    local current="${COMP_WORDS[COMP_CWORD]}"
    local -a matches=()
    read -ra matches <<< "$(compgen -W "$1" -- "$current")"
    COMPREPLY=("${matches[@]}")
}

_insightbloom_complete_path() {
    local current="${COMP_WORDS[COMP_CWORD]}"
    COMPREPLY=()
    while IFS= read -r match; do
        COMPREPLY+=("$match")
    done < <(compgen -f -- "$current")
}

_insightbloom_completion() {
    local previous="${COMP_WORDS[COMP_CWORD-1]:-}"
    local command="${COMP_WORDS[1]:-}"

    if [[ "$COMP_CWORD" -eq 1 ]]; then
        _insightbloom_complete_values "app-publish app-revoke ask login logout mentor publish revoke --help --version"
        return 0
    fi

    case "$command" in
        login)
            _insightbloom_complete_values "--help --username"
            ;;
        logout)
            _insightbloom_complete_values "--help"
            ;;
        publish)
            case "$previous" in
                --root) _insightbloom_complete_path ;;
                *) _insightbloom_complete_values "--conference-id --help --root --token --token-prompt --token-stdin" ;;
            esac
            ;;
        revoke|app-revoke|app-publish)
            case "$previous" in
                --token|--conference-id) ;;
                *) _insightbloom_complete_values "--conference-id --help --token --token-prompt --token-stdin" ;;
            esac
            ;;
        mentor|ask)
            case "$previous" in
                --file) _insightbloom_complete_path ;;
                *) _insightbloom_complete_values "--conference-id --file --help --token --token-prompt" ;;
            esac
            ;;
    esac
}

complete -F _insightbloom_completion insightbloom
