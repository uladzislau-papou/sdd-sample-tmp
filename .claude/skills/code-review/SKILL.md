---
name: code-review
description: Reviews an increment or a PR along four axes — logical correctness, architecture drift, security, and conformance to the specification. Delegates each axis to the reviewer that owns it, runs them in parallel, and reports one verdict per axis with split authority. Read-only; never applies fixes.
argument-hint: [--increment | --pr <n> | --branch <name>]
---

# Increment Review

You are an **orchestrator**, not an analyst. Your value is one entry point, four axes and
one consolidated verdict — not new analysis. Three of the four axes are already covered by
tools that are maintained without you; reimplementing them would be worse and slower.

## The four axes

| Axis | Owned by | Authority |
|------|----------|-----------|
| architecture drift | `ddd-hex-reviewer` (subagent) | **blocks** |
| conformance to the spec | `conformance-reviewer` (subagent) | **blocks** |
| logical correctness | `mattpocock-skills:code-review` | reports |
| security | `security-review` | reports |

### Why authority is split rather than one verdict

A single `PASS`/`FAIL` over four axes looks tidier and works worse, because the axes have
very different reliability.

Architecture and conformance rest on executable rules and list-matching — ArchUnit rules,
tests that exist or do not, DoD boxes that point at artifacts or do not. False positives are
rare, so blocking is affordable and `ddd-hex-reviewer` already had that authority
(`CLAUDE.md`: "drift is never traded away for progress").

Logic and security are model judgement. False positives are ordinary. A blocking gate that
cries wolf gets switched off — **and it gets switched off entirely**, taking the two
reliable axes with it. Splitting authority is what keeps the strict half strict.

So "addressed" is the bar for the reporting axes: each finding is fixed, or explicitly
accepted with a reason. "Zero security findings" is not an achievable merge condition, and
stating it as one would make the gate a lie (`test.definition.md` § 7, gate 12).

### Note on the logic axis

This skill is named `code-review`, which **shadows the built-in skill of the same name**.
That was chosen knowingly; the consequence is that the built-in cannot be delegated to by
name from here. The logic axis therefore runs through `mattpocock-skills:code-review`, which
keeps its own namespace and stays reachable. The built-in remains available to a human who
invokes it directly with its own flags.

## Scope

| Argument | Reviews |
|----------|---------|
| `--increment` (default inside the loop) | the diff since the last GREEN commit |
| `--branch <name>` | the diff from the merge-base with `main` |
| `--pr <n>` | the PR's diff |

Inside the inner loop's Review & Document Phase, the scope is the increment and the verdict
blocks it. Outside, on a branch or a PR, the same axes run over a wider diff and the
blocking axes are advice to the reviewer — a merge is a human decision.

## Procedure

### 1. Establish the scope and the specification

```shell
git diff --stat <base>...HEAD
```

Identify the use case from the branch name, `tasks.md`, or the request. `conformance-reviewer`
needs it. If no single specification applies, say so — a confident conformance verdict about
the wrong document is worse than no verdict.

### 2. Fan out — one message, four tool calls

All four axes run in parallel. They do not depend on each other, and serialising them wastes
the only thing this skill costs.

- `ddd-hex-reviewer` — hand it the changed files and the scope
- `conformance-reviewer` — hand it the changed files and the specification
- `mattpocock-skills:code-review` — the logic axis, over the same diff
- `security-review` — the security axis, over the same diff

### 3. Consolidate

Report **per axis**, never as an average. Two blocking `PASS`es with fourteen logic findings
is a different situation from one blocking `DRIFT` with none, and a single score hides
exactly that difference.

Deduplicate across axes: the same defect often surfaces twice — a missing null guard is
both a logic finding and, if the spec named the case, a conformance finding. Report it once,
attributed to the axis with the strongest claim, and note the second sighting.

### 4. Emit findings

- **Logic and security findings** → `ReportFindings`, one entry each, most severe first,
  with `file`, `line`, `summary` and `failure_scenario`. Point findings have addresses and
  benefit from ranking.
- **Architecture and conformance verdicts** → prose. "AC-03 is covered by no test" is
  addressed by a specification, not by a line of code, and it fits a `file:line` field
  badly.

## What this skill never does

### It never applies fixes

There is no `--fix`, and this is not caution — it is a direct contradiction of two rules
this project is built on:

- `CLAUDE.md`: "`ddd-hex-reviewer` **reports**; it never edits."
- `CLAUDE.md`: "No production code without a failing test."

Auto-fixing architecture drift means restructuring code with no spec and no RED test, and
doing it in the moment a human asked for a report. Worse, a reviewer that fixes becomes the
acceptor of its own changes — the exact asymmetry that made `spec-reviewer` a separate agent
from `/spec-create`.

Fixing a finding goes the ordinary way: `/execute-task`, with a RED test that reproduces it
first. Slower, and it is the discipline the whole project exists to keep.

### It never posts to a PR

No `--comment`. External publication is a human decision, and the sibling skill
`/spec-create` is strictly read-only against Jira and Confluence for the same reason. Two
skills with different policies on outward-facing writes is how an accident happens. If
inline PR comments are wanted, the built-in review skill has them.

## Output

```
Scope              – base..HEAD, files changed, the specification identified

Verdict by axis
  architecture     – PASS | DRIFT      (blocks)
  conformance      – PASS | UNMET      (blocks)
  logic            – <n> findings      (reported → ReportFindings)
  security         – <n> findings      (reported → ReportFindings)

Blocking findings  – architecture and conformance, in full, with the document each cites
Duplicates         – defects seen on more than one axis, and where they are reported
Not checked        – any axis that could not run, and why
```

State plainly whether the increment is blocked. On two blocking `PASS`es with open reporting
findings, say that the increment may proceed **and** that the findings are outstanding — not
one or the other.

## Anti-patterns

- **One overall verdict.** It destroys the information the split exists to preserve.
- **Serialising the axes.** They are independent.
- **Reimplementing an axis** because delegating felt indirect. Three of these are maintained
  without you.
- **Reporting the same defect four times** because four axes found it.
- **Treating a security finding as a blocker**, or as noise. Neither: it is recorded, then
  fixed or explicitly accepted.
