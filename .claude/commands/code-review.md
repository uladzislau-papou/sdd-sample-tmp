---
description: Review the diff since a base ref (default: merge-base with main) for logic errors, architecture drift, and security issues
argument-hint: [<base-ref>] [--paths=<glob>[,<glob>...]]
---

Invoke the `code-review` skill with `$ARGUMENTS`.

See `.claude/skills/code-review/SKILL.md` for the full procedure. This command
is a thin alias so the review sits alongside the project's other commands in
`.claude/commands/` — the skill file is the single source of truth for the
checklist and reporting behavior; do not duplicate it here.
