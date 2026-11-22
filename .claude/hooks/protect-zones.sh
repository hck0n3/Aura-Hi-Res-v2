#!/usr/bin/env bash
# PreToolUse hook: AGENTS.md rule 6 made mechanical.
# Protected zones: the equalizer (com/music/echo/eq/), the Superpowered engine (app/src/main/cpp/)
# and the subscription gate (com/music/echo/license/).
#  - Edit/Write/NotebookEdit on a protected file -> "ask": the owner must approve that exact edit.
#  - Bash command that writes/deletes/moves inside a protected zone -> "deny" (use Edit so the owner is asked).
# Reading those files is always allowed. Pure bash + grep + sed so it also runs on Windows (Git Bash).

input="$(cat)"
# Only look at the tool input, with JSON-escaped Windows backslashes turned into forward slashes.
tool_input="$(printf '%s' "$input" | sed 's/.*"tool_input"//' | sed 's#\\\\#/#g')"
tool_name="$(printf '%s' "$input" | grep -o '"tool_name"[[:space:]]*:[[:space:]]*"[^"]*"' | head -n1 | sed 's/.*"\([^"]*\)"$/\1/')"

# A zone directory followed by "/", a quote, a space or the end: also catches "rm -rf app/src/main/cpp".
# The hook and its settings are protected too, so an agent cannot switch the guard off.
zones="(music/echo/(eq|license)|main/cpp|\.claude/hooks|\.claude/settings\.json)([/\"' ;|&]|\$)"

emit() { # $1 = ask|deny, $2 = reason
  printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"%s","permissionDecisionReason":"%s"}}\n' "$1" "$2"
  exit 0
}

case "$tool_name" in
  Edit|Write|MultiEdit|NotebookEdit)
    path="$(printf '%s' "$tool_input" | grep -o '"\(file_path\|notebook_path\)"[[:space:]]*:[[:space:]]*"[^"]*"' | head -n1 | sed 's/.*"\([^"]*\)"$/\1/')"
    if printf '%s' "$path" | grep -Eq "$zones"; then
      emit ask "ZONA PROTEGIDA (AGENTS.md regla 6): $path pertenece al ecualizador, a Superpowered o a la licencia. Solo se toca si el dueño lo pidió explícitamente. Aprueba solo si tú pediste este cambio."
    fi
    ;;
  Bash)
    # A redirect counts only when it points INTO a zone ("> app/src/main/cpp/x"), so a ">" elsewhere in the
    # command (e.g. "<noreply@...>" in a commit message) does not block `git add`/`git commit`.
    redirect_into_zone="$(printf '%s' "$tool_input" | grep -Eo ">>?[[:space:]]*[^[:space:]]*" | grep -E "$zones")"
    if [ -n "$redirect_into_zone" ]; then
      emit deny "ZONA PROTEGIDA (AGENTS.md regla 6): este comando escribe en el ecualizador, Superpowered, la licencia o el propio hook. Si el dueño lo pidió explícitamente, usa la herramienta Edit para que él lo apruebe."
    fi
    if printf '%s' "$tool_input" | grep -Eq "$zones"; then
      if printf '%s' "$tool_input" | grep -Eq '(sed[^|;&]* -i|perl[^|;&]* -i|\btee\b|\brm\b|\bmv\b|\bcp\b|\btruncate\b|\bpatch\b|git (checkout|restore|apply|rm|mv|reset|stash)|Set-Content|Out-File|Remove-Item|Move-Item|Copy-Item)'; then
        emit deny "ZONA PROTEGIDA (AGENTS.md regla 6): este comando modifica el ecualizador, Superpowered o la licencia. No se cambian desde la shell. Si el dueño pidió este cambio explícitamente, hazlo con la herramienta Edit para que él lo apruebe."
      fi
    fi
    ;;
esac
exit 0
