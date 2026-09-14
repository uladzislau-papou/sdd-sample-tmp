---
name: pr-review
description: Review a GitHub pull request by URL (or number) for logic errors, architecture drift, and security issues. Fetches the diff and metadata via `gh`, without checking out the PR branch, then runs the same three-axis review as the `code-review` skill and reports findings via ReportFindings. Use when asked to review a PR link, a PR number, or "review this pull request".
argument-hint: <PR-URL-or-number>
---

# PR Review

A thin front end over the `code-review` skill: it sources its review scope from
a **GitHub pull request** instead of a local diff, then hands off to
`code-review`'s own checklist and reporting. Read `.claude/skills/code-review/SKILL.md`
before starting — its Steps 2 through 4 are reused unmodified. This skill only
replaces Step 1 (scope resolution).

GitHub only — requires `gh` authenticated against the target repo. If `gh auth
status` fails, stop and say so rather than guessing at credentials.

## Argument

`<PR-URL-or-number>` — a full URL (`https://github.com/<owner>/<repo>/pull/<n>`)
or a bare number/`#<n>` run from inside a checkout of that repo. Pass it to `gh`
as-is; `gh pr view`/`gh pr diff` accept both forms.

---

## Step 1 — Resolve the PR (replaces code-review's Step 1)

**No checkout. No `git checkout`/`git switch`, ever, in this skill.** The
current working tree and branch must be left exactly as found.

1. `gh auth status` — stop if not authenticated.
2. `gh pr view <arg> --json number,title,url,baseRefName,headRefName,headRepositoryOwner,author,body,changedFiles,additions,deletions,mergeable,statusCheckRollup`
   Report title, author, base←head branches, and file/line counts before
   reviewing. If `mergeable` is `CONFLICTING`, say so — it does not block the
   review but the caller should know.
3. `gh pr diff <arg>` — this is the review scope. Unlike a local `git diff`, it
   already includes new files in full, so there is no separate untracked-files
   step.
4. **When a hunk's context isn't enough to judge a finding** (e.g. need to see
   the whole method or the file's imports), fetch the PR head without touching
   `HEAD`:
   ```bash
   git fetch origin pull/<n>/head:refs/pr/<n>
   git show refs/pr/<n>:<path/to/file>
   ```
   This only creates a local ref pointer — it never checks out or moves the
   current branch. Delete the ref when done (`git update-ref -d refs/pr/<n>`);
   it is disposable local state this skill created, not shared history.
5. The **base for architecture/doctrine framing is `baseRefName` from step 2**,
   not `main` — a PR can target a release branch or another feature branch.

Report the resolved scope (PR number, base←head, file count) before reviewing,
exactly as code-review's Step 1 would for a local diff.

---

## Steps 2–4 — Ground, review, report

Follow `code-review` SKILL.md's Step 2 (ground in project rules), Step 3
(logic / architecture drift / security checklist), and Step 4 (report via
`ReportFindings`) exactly as written, using the diff and files fetched above as
the review set.

---

## Output boundary

This skill only reports locally via `ReportFindings`. It never posts a comment,
review, or status check to the PR on GitHub — no `gh pr comment`, `gh pr
review`, or `gh api` write call. If the user wants the findings posted to the
PR, that is a separate, explicit ask each time, not something this skill does
on its own.

---

## Anti-patterns

- Checking out the PR branch, or otherwise moving the caller's `HEAD` — this
  skill must be safe to run with uncommitted local work present.
- Framing architecture findings against `main` when the PR targets a different
  base branch.
- Posting anything back to GitHub without being explicitly asked in the same
  request.
- Leaving `refs/pr/<n>` litter behind across many invocations — clean it up.
- Re-deriving code-review's checklist from memory instead of reading its
  SKILL.md — the checklist has one source of truth.
