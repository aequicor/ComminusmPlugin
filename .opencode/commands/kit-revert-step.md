---
description: "Revert the current step via `git revert` (non-destructive). Creates a reverse commit that undoes the step's changes; the original commit stays in history. Marks step_commits[N].superseded=true, restores [ ] checkbox in plan.md, sets current_step_idx back. Does NOT trip the harness destructive-action gate."
---
# /kit-revert-step

Revert the current step via `git revert` (non-destructive). Creates a reverse commit that undoes the step's changes; the original commit stays in history. Marks step_commits[N].superseded=true, restores [ ] checkbox in plan.md, sets current_step_idx back. Does NOT trip the harness destructive-action gate.


<project>ComminusmPlugin</project>
<stack>kotlin / paper-plugin, paper</stack>

<communication_language>
Communicate with the user in Russian (ru). All prose — questions, explanations, status updates, summaries, and reasoning addressed to the user — must be in Russian. Keep code, file paths, shell commands, identifiers, manifest keys, error codes, and other technical tokens verbatim in their original form.
</communication_language>




{{#if KNOWLEDGE_OS_ENABLED}}
## Memory (KnowledgeOS)

Long-term memory is in a KnowledgeOS vault accessed via MCP. Prefer these
over filesystem grep when you need context outside the current task:

- `search_docs(query, filters?)` — semantic + BM25 search. Filter shape:
  `{"fm.<key>": "<value>"}`. Use first when context is missing.
- `get_doc(path)` — fetch one document by vault-relative path.
- `list_docs(directory?)` — enumerate documents under a vault directory.
- `write_doc(path, content, frontmatter?)` — create. Use `[[other-doc]]`
  wikilinks for cross-refs (auto-loaded on retrieval). Frontmatter keys
  become `fm.<key>` filters.
- `update_doc(path, content, preserve_frontmatter?)` — modify existing.
  Pass `preserve_frontmatter: true` for body-only edits.

Logical key → frontmatter filter (matches manifest layout):
- feature → `{"fm.type": "domain", "fm.scope": "feature"}`
- subsystem → `{"fm.type": "reference", "fm.scope": "subsystem"}`
- decision → `{"fm.type": "decision"}`
- tech-debt → `{"fm.type": "tech-debt"}`
- documentation → `{"fm.type": "documentation"}`

If an MCP call errors or the server is unreachable, fall back to Read/Grep
on `vault/specs/`. Do not block the task on a memory failure — log it and
proceed.
{{/if}}
{{#if KNOWLEDGE_OS_DISABLED}}
## Memory (filesystem)

Long-term memory is plain markdown at `vault/specs/`:
- features → `vault/specs/features/<module>/<feature>/spec.md`
- subsystems → `vault/specs/subsystems/<name>.md`
- decisions → `vault/specs/DECISIONS.md`
- tech-debt → `vault/specs/tech-debt/<module>/<slug>.md`
- documentation → `vault/specs/guidelines/<module>/<topic>.md`

Read with Read/Grep. Write with the host's edit/write tools. KnowledgeOS
is not enabled — there is no semantic search, no wikilink expansion, no
reranking. List the vault before claiming a document is missing.
{{/if}}





<workflow>
Revert the current step via `git revert` (non-destructive). Creates a reverse commit that undoes the step's changes; the original commit stays in history. Marks step_commits[N].superseded=true, restores [ ] checkbox in plan.md, sets current_step_idx back. Does NOT trip the harness destructive-action gate.

You are a Senior project manager processing the user's request to undo the current step. Mechanism: `git revert` (creates a reverse commit, non-destructive). The user-visible semantics: the step is undone, plan.md restores the `[ ]` checkbox, you can replan or re-execute.

- Working copy is updated by a NEW commit (the revert), not by rewriting history.
- Original step commit stays in `git log` (audit trail).
- `step_commits[N]` keeps its sha but is marked `superseded: true`; a new entry with `kind: revert` is appended.
- Cascade revert (multiple steps) creates one revert commit per reverted step (in reverse order). Squash on merge cleans this up.

Argument: $TARGET_STEP (optional integer; default = `current_step_idx`). Allows reverting further back than just the most recent step.

## Step 1 — Resolve target

1. Read `.planning/CURRENT.md`:
   - If `active_task` is `(none)` → STOP.
   - If `mode: sleep` → STOP. Output: "Sleep mode is autonomous; /kit-revert-step is a user command."
   - If `status: SLEEP_BLOCKED` → STOP.

2. Read `.planning/tasks/<active_task>.md`:
   - If `current_step_idx` is missing or `0` → STOP.

3. Resolve target step:
   - If `$TARGET_STEP` provided → use it (must be > 0 and ≤ current_step_idx).
   - Else → target = `current_step_idx`.

4. Resolve commits to revert (the set of `step_commits[N].sha` for N in `[target..current_step_idx]`):
   - For each step in range, walk `step_commits[]` and pick the most recent entry with `superseded: false` and `kind != revert`.
   - If any step in range has no eligible commit → STOP.
   - If a step's commit is recorded as `(no-git)` → STOP. Output: "Cannot revert without a git anchor."

## Step 2 — Pre-flight git safety

5. Run `git status --porcelain`. If output is non-empty:
   - If any uncommitted file exists → STOP. Output:
     ```
     ⚠️ /kit-revert-step requires a clean working tree (revert creates a new commit on top of HEAD).
     Uncommitted changes:
       - <path>
     Stash, commit, or discard them first, then re-run /kit-revert-step.
     ```

6. Run `git rev-parse HEAD` and `git log --oneline <step_commits[target-1].sha-or-task_start_commit>..HEAD` to show user what will be reverted.

## Step 3 — Confirm with user

7. Output:

```
↩️  /kit-revert-step — non-destructive (creates revert commit)

Target: step <target> (and any subsequent steps <target+1>..<current_step_idx>).

The following commits will be reverted in reverse order (newest first):
<output of git log --oneline>

Mechanism: `git revert <sha>` per commit. Working copy and history both keep the
original commits — revert just adds inverse commits on top. Squash on merge will
clean this up.

Confirm with /kit-approve.
Cancel with anything else.
```

8. WAIT for user `/kit-approve`. Anything else → STOP.

## Step 4 — Apply

9. For each step in `[current_step_idx down to target]`:
   - Pick `sha = step_commits[N].sha`.
   - Run `git revert --no-edit <sha>`.
   - If conflict → ABORT the revert (`git revert --abort`) and STOP. Output:
     ```
     ⚠️ git revert hit a conflict on step <N> (<sha>).
     Options:
       (a) /kit-revert-step <N+1>..<current_step_idx>  — revert later steps first
       (b) Resolve manually, commit, then re-run /kit-revert-step.
     ```
   - On success: capture `revert_sha = git rev-parse HEAD`.
   - Append to `step_commits[]`:
     ```
     - step: <N>
       sha: <revert_sha>
       kind: revert
       reverts: <sha>
       goal: "Revert step <N>: <original goal>"
       changed_files: <files touched by this revert>
       superseded: false
     ```
   - Mark the original `step_commits[]` entry for step N as `superseded: true`.

10. After all reverts succeed, update `.planning/tasks/<active_task>.md`:
    - Set `current_step_idx = target - 1`.
    - Replace `Last-checkpoint:` line: `<ISO> — NEXT: step <target> reverted by user; replan or re-execute`.

11. Update `plan.md § Implementation plan`:
    - For each step in `[target..(highest step with [x])]`, replace `[x]` with `[ ]`.

12. Output to user:

```
✅ Reverted via git revert. Working copy at <new HEAD sha>.
   Reverse commits added: <count> (one per reverted step, newest first).
   step_commits[] updated: <count> entries appended (kind: revert).
   plan.md: steps <target>..<highest> restored to [ ].

Choose next:
  /kit-approve            — re-run step <target> with the same plan-step description
  /kit-rework <reason>    — pre-step replan
  /kit-revert-step <N>    — cascade revert further back
```

## What NOT to do

- DO NOT use `git reset --hard`. Use `git revert` (non-destructive).
- DO NOT use `git revert --no-commit`. We want one commit per reverted step.
- DO NOT cascade revert past `start_commit`.
- DO NOT touch spec.md or test-cases.md.
- DO NOT auto-invoke /kit-rework after the revert.
- DO NOT run in sleep mode.
- DO NOT proceed without /kit-approve confirmation.
</workflow>
