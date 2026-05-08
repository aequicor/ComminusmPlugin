---
description: "Show all open tasks and team WIP. Who is working on what, current stages, blockers. Surfaces rolling gate signal_ratio over the last N tasks (deprecation candidates highlighted) when evals/runs/<kit_version>/ exists."
---
# /kit-status

Show all open tasks and team WIP. Who is working on what, current stages, blockers. Surfaces rolling gate signal_ratio over the last N tasks (deprecation candidates highlighted) when evals/runs/<kit_version>/ exists.


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
Show all open tasks and team WIP. Who is working on what, current stages, blockers. Surfaces rolling gate signal_ratio over the last N tasks (deprecation candidates highlighted) when evals/runs/<kit_version>/ exists.

You are a project state reporter. Read `.planning/`, git state, and `evals/runs/<kit_version>/` if present, then output a concise team status. No code, no analysis, no tool calls beyond what is listed below.

Execute strictly:

1. Read `.planning/CURRENT.md` → note `active_task` (this session's active task).
2. List all files in `.planning/tasks/` (exclude `done/` subdirectory).
3. For each task file found:
   a. Read the file.
   b. Extract: Type, Module, Description (from header), and the **last** Timeline entry (DONE / NEXT / BLOCKED).
4. Run `git branch` to list local branches.
5. Run `git log --oneline -5` for recent commits context.
6. If `evals/runs/<kit_version>/gates.csv` exists, compute rolling gate signal_ratio:
   - Read manifest for `kit_version`, `policies.telemetry.evaluation_window_tasks` (default 30), `policies.telemetry.signal_ratio_threshold` (default 0.05).
   - Read gates.csv rows. Filter to last `evaluation_window_tasks` distinct `task_slug` values.
   - Group by `gate`. For each gate compute:
       fires = row count
       blocks = count where `verdict == block`
       signal_ratio = blocks / fires (0 if fires == 0)
       false_negatives = count of defects in defects.csv (same window) whose origin maps to this gate AND a `pass` row exists for the same (task, step) earlier
   - Order: by signal_ratio ASC (lowest first — deprecation candidates surface).
   - Highlight rows where `signal_ratio < signal_ratio_threshold` AND `false_negatives == 0` as `🟡 deprecation_candidate`.
   - Highlight rows where `false_negatives > 0` as `🔴 missed_defects (<count>)`.
7. If `evals/runs/<kit_version>/defects.csv` exists, compute origin histogram over the same window:
   - Group by `origin` column; count rows. Show top 5.

Output EXACTLY this format:

```
## Team Status — <current date>

Active in this session: <active_task or "(none)">

### Open Tasks

| Task | Type | Module | Last Done | Next | Blocked? |
|------|------|--------|-----------|------|----------|
| <slug> | FEATURE/BUG/TECH | <module> | <DONE line> | <NEXT line> | — or reason |
... one row per open task file

### Recent Commits
<last 5 git log lines>

### Branches
<git branch output>
```

Append, only if `evals/runs/<kit_version>/` exists:

```
### Gate signal — rolling over last <N> tasks

| Gate | Fires | Blocks | Signal ratio | False negatives | Status |
|------|-------|--------|--------------|-----------------|--------|
| <gate> | <fires> | <blocks> | <ratio> | <fn count> | <emoji + label or empty> |
... rows ordered by signal_ratio ASC

Threshold: <signal_ratio_threshold> (policies.telemetry.signal_ratio_threshold)
Window: last <evaluation_window_tasks> tasks (policies.telemetry.evaluation_window_tasks)

Deprecation candidates: <count of 🟡 rows>
Gates that missed defects: <count of 🔴 rows>
```

Append, only if `evals/runs/<kit_version>/defects.csv` exists:

```
### Defect origin distribution — last <N> tasks

| Origin | Count |
|--------|-------|
| <origin> | <count> |
... top 5 rows

Total defects in window: <total>
```

If no open task files AND no eval data → output: "No open tasks. Run `/kit-new-feature` to start."

If open tasks but no eval data → omit the "Gate signal" and "Defect origin" sections silently (no warning — telemetry is opt-in).

Do not do anything else after the output:
- DO NOT analyze gate-signal rows. Surface the data; user decides.
- DO NOT recommend deprecations in chat. The emoji + label is the recommendation.
- DO NOT modify gates.csv or defects.csv. Read-only.
</workflow>
