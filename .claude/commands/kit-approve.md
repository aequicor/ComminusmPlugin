---
description: "Approve the pending plan or action that @Main is waiting on. Signals user confirmation and continues the pipeline. Optional flag `--no-ground-truth` overrides the 5.6 ground-truth artefact gate (logged as technical debt in Defects log; do not use lightly)."
allowed-tools: ""
---
# /kit-approve

Approve the pending plan or action that @Main is waiting on. Signals user confirmation and continues the pipeline. Optional flag `--no-ground-truth` overrides the 5.6 ground-truth artefact gate (logged as technical debt in Defects log; do not use lightly).


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
Approve the pending plan or action that @Main is waiting on. Signals user confirmation and continues the pipeline. Optional flag `--no-ground-truth` overrides the 5.6 ground-truth artefact gate (logged as technical debt in Defects log; do not use lightly).

You are the user authorizing the next phase. Your task is to confirm approval and unblock the pipeline immediately — no re-summaries, no delays.

**User approval received.**

{{snippet:memory_policy.commit}}

You are @Main. You were waiting for user confirmation at a CONFIRM-class gate. Act now:

1. Read `.planning/CURRENT.md` → get `active_task`. Append to `.planning/tasks/<active_task>.md`:
   ```
   ## <ISO timestamp>
   - DONE: User approved via /kit-approve<flags>
   - NEXT: proceeding to next phase
   ```
   `<flags>` is `" --no-ground-truth"` if that flag was passed, empty otherwise.

2. Determine which gate is unblocked:
   - At step 4 CONFIRM (FEATURE/TECH) → continue to step 5 EXECUTE.
   - At step 5.6 CHECKPOINT 3-way fork → continue to step 5.5 UPDATE for next step (or RECONCILE if last step).
   - At step 5.10 DIFF-REVIEW → continue to step 6 CLOSE.
   - At a /kit-revert-step confirmation prompt → execute the revert.
   - At a /kit-defect re-open confirmation → re-enter EXECUTE at 5.2 WRITE.
   - At BUG pipeline → BUG has no CONFIRM step; if reached, report state and ask user.

3. `--no-ground-truth` flag handling (only meaningful at 5.6 CHECKPOINT):
   - If the flag was passed AND the 5.6 ground-truth gate was BLOCKED on missing artefact:
     - Append to test-cases.md § Defects log:
       ```
       - **GROUND-TRUTH-WAIVED step <N>** (severity: medium, status: OPEN, kind: technical-debt)
         - Reported: <ISO>
         - Source: ground-truth-waived (user override at 5.6)
         - Description: Step <N> approved without ground-truth artefact (type: <REQUIRED_TYPE>).
                        Class of defect this gate catches: UI/UX broken despite green AI checks;
                        API contract mismatch; backend logic missing edge-case under mutation.
                        Track here for retro and consider adding a regression test post-CLOSE.
       ```
     - Set `step_commits[N].ground_truth = {type: <REQUIRED_TYPE>, path: null, summary: "WAIVED by /kit-approve --no-ground-truth", waived: true}`.
     - Output: "⚠️  Ground-truth waived for step <N>. Logged as technical debt. Proceeding."
   - If the flag was passed but no 5.6 gate is pending → ignore flag, proceed normally.
   - If the flag was NOT passed AND 5.6 gate is BLOCKED on missing artefact → STOP. Output: "Step <N> ground-truth artefact still missing. Attach via /kit-attach <path>, or override with /kit-approve --no-ground-truth."

4. Continue immediately. Do not ask for additional confirmation. Do not re-summarize the plan.

> If no CONFIRM-class gate was pending — report the current state from the active task file and ask user what to do next.
</workflow>
