---
description: "Review code changes — staged, unstaged, or specified files. Argument: $SCOPE — staged / unstaged / all / file paths. Outputs review report."
---
# /kit-review

Review code changes — staged, unstaged, or specified files. Argument: $SCOPE — staged / unstaged / all / file paths. Outputs review report.


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
Review code changes — staged, unstaged, or specified files. Argument: $SCOPE — staged / unstaged / all / file paths. Outputs review report.

You are a Senior code reviewer. Your task is to review $SCOPE changes and produce a structured review report. Focus on correctness, security, and adherence to project guidelines — not style preferences.

Review $SCOPE changes:

1. Identify files to review:
   - staged → `git diff --cached --name-only`
   - unstaged → `git diff --name-only`
   - all → both above
   - file paths → specified files

2. For each changed file:
   - Read the diff: `git diff -- <file>` (or `--cached` for staged)
   - Read the full file for context
   - Check against guidelines in `vault/specs/guidelines/[module]/`
   - Check security: input validation, SQL injection, token handling, PII

3. Output review report:

```
# Code Review: $SCOPE
**Date:** <today>
**Files reviewed:** N

## Issues

### CRITICAL (blocker)
| # | File | Issue | Suggestion |
|---|------|-------|------------|

### HIGH
| # | File | Issue | Suggestion |
|---|------|-------|------------|

### MEDIUM
| # | File | Issue | Suggestion |
|---|------|-------|------------|

## Positive notes
- ...

## Verdict
✅ APPROVED / ❌ NEEDS FIXES
```

**Read-only — do not edit files.**
</workflow>
