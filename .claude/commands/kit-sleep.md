---
description: "Start a feature in autonomous sleep mode. Argument: $FEATURE_DESCRIPTION. The pipeline runs without user prompts, doubles retry budgets, auto-confirms all CONFIRM/diff-review/replan gates, and writes .planning/MORNING_REPORT.md on completion or block. Equivalent to /kit-new-feature --sleep "..."."
allowed-tools: ""
---
# /kit-sleep

Start a feature in autonomous sleep mode. Argument: $FEATURE_DESCRIPTION. The pipeline runs without user prompts, doubles retry budgets, auto-confirms all CONFIRM/diff-review/replan gates, and writes .planning/MORNING_REPORT.md on completion or block. Equivalent to /kit-new-feature --sleep "...".


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
Start a feature in autonomous sleep mode. Argument: $FEATURE_DESCRIPTION. The pipeline runs without user prompts, doubles retry budgets, auto-confirms all CONFIRM/diff-review/replan gates, and writes .planning/MORNING_REPORT.md on completion or block. Equivalent to /kit-new-feature --sleep "...".

You are a Senior project orchestrator. Your task is to start a new feature in **autonomous sleep mode** — for use when the user will be away (sleeping, in a meeting, etc.) and wants to wake up to either a finished feature or a clear "stuck here" report.

Argument: $FEATURE_DESCRIPTION

{{snippet:memory_policy.session}}

## Step 1 — Sanity checks (pre-flight)

1. Read `.planning/CURRENT.md`:
   - If `active_task` ≠ `(none)` → STOP. Output: "An active task is already in progress (`<slug>`, mode: `<mode>`). Sleep mode requires no other active task."
   - Otherwise proceed.

2. Verify the project is a git repository:
   - Run `git rev-parse --git-dir`. If error → STOP. Output: "Sleep mode requires git. Initialize the project (`git init`) and commit the baseline before running /kit-sleep."

3. Verify `policies.auto_commit_per_step != false`:
   - If false → STOP. Output: "Sleep mode requires `auto_commit_per_step: true` (default). Set it in the manifest first."

3a. Risk gate. If `$FEATURE_DESCRIPTION` carries `--risk=critical` (or auto-classify infers critical) AND `policies.lanes.critical_block_sleep: true` (default) → STOP. Output: "Critical risk + sleep mode is the highest blast-radius combination. Refused per `policies.lanes.critical_block_sleep: true`."

3b. `Bash(git reset --hard *)` allowlist advisory. Sleep mode's BLOCKED-shutdown procedure uses `git reset --hard <last_green>`. Without the allowlist in the runner's settings, the harness will prompt mid-sleep and defeat autonomous mode.

4. Output one-time advisory to user:

```
🌙 Starting sleep mode for: $FEATURE_DESCRIPTION

The pipeline will run autonomously through:
  - CLASSIFY & CLARIFY (one-pass; clarifying questions answered by user upfront)
  - ANALYSIS / PLAN / CONFIRM (auto-approved)
  - EXECUTE per step (machine-only; runbooks aggregate into MORNING_REPORT.md)
  - 5.10 diff-review (auto-approved)
  - CLOSE

On completion: read `.planning/MORNING_REPORT.md` for TL;DR + per-step runbooks + total diff.
On block: read `.planning/MORNING_REPORT.md § Open questions / blocks`. Status will be `SLEEP_BLOCKED` in `.planning/CURRENT.md`. Resume with `/kit-resume`.

To interrupt while running: edit `.planning/CURRENT.md` and set `mode: interactive`.

User must answer the upfront clarifying questions in this same session — sleep mode cannot pause for them mid-run. Proceed?
```

5. WAIT for user confirmation ("yes" or equivalent).

## Step 2 — CLASSIFY & CLARIFY (single-pass, all questions upfront)

6. Hand off to `@Main` with this exact prompt:

```
New task: $FEATURE_DESCRIPTION
Type: FEATURE (or clarify if BUG/TECH)
Mode: sleep

CRITICAL: This task runs in sleep mode. Ask ALL clarifying questions in ONE message
(consolidate Step 0a's per-type questions into a single clarification block).
After user answers, set .planning/CURRENT.md.mode: sleep before proceeding past CLASSIFY.
Then run the rest of the FEATURE pipeline autonomously per the "Sleep Mode" section
of your body — no further user prompts until CLOSE or BLOCKED-shutdown.
```

7. Pass through @Main's clarifying questions. Wait for user's responses.

8. After user responds, dispatch back to @Main with the answers and instruction:

```
Clarifications above. Now:
  1. Set .planning/CURRENT.md:
       active_task: <task_slug>
       mode: sleep
  2. Initialize .planning/MORNING_REPORT.md with task_slug filled in and "Sleep started: <ISO>".
  3. Proceed through ANALYSIS → PLAN → CONFIRM (auto-approved) → EXECUTE → 5.10 → CLOSE.
  4. On any unrecoverable failure, run BLOCKED-shutdown procedure.
  5. On CLOSE, finalize MORNING_REPORT.md with TL;DR / total diff / Suggested next action.
```

9. STOP. Do not micro-manage @Main; sleep mode is autonomous by design.

## Step 3 — Final note (user-facing)

10. Output to user:

```
✅ Sleep mode started. @Main is now running autonomously.

Read on wake-up:
  .planning/MORNING_REPORT.md   — TL;DR, per-step runbooks, total diff, suggested next action

If status is BLOCKED:
  .planning/CURRENT.md           — will show `status: SLEEP_BLOCKED, awaiting_po: true`
  .planning/MORNING_REPORT.md    — § Open questions / blocks

To wake up early: edit `.planning/CURRENT.md` and set `mode: interactive`.
```

## What NOT to do

- DO NOT proceed past pre-flight sanity if any check fails.
- DO NOT emit clarifying questions in multiple turns once sleep mode is active.
- DO NOT enable sleep mode for /kit-fix or /kit-techdebt — sleep is FEATURE/TECH only.
- DO NOT bypass the destructive gates (DEPLOY, DESTROY, SECRET_ROTATE, MIGRATION, EXTERNAL_API) in sleep mode.
- DO NOT promise "wake up to a fully working feature" — sleep mode is best-effort autonomous.
</workflow>
