---
description: Review a GitHub pull request by URL (or number) for logic errors, architecture drift, and security issues
argument-hint: <PR-URL-or-number>
---

Invoke the `pr-review` skill with `$ARGUMENTS`.

See `.claude/skills/pr-review/SKILL.md` for the full procedure. This command is
a thin alias so the review sits alongside the project's other commands in
`.claude/commands/` — the skill file is the single source of truth for scope
resolution, the checklist, and reporting behavior; do not duplicate it here.
