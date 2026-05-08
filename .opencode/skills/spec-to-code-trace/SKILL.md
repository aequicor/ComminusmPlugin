---
name: "spec-to-code-trace"
description: "Verify every acceptance criterion in spec.md has at least one test."
---
<skill name="spec-to-code-trace">

<purpose>
Verify every acceptance criterion in spec.md has at least one test.
</purpose>


<when_to_invoke>
Before the DoD gate at workflow CLOSE.
</when_to_invoke>



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



<procedure>
1. Parse `vault/features/{module}/{feature}/spec.md`. Extract every `AC-N: <text>` line.
2. Parse `test-cases.md`. Each TC row should reference an AC in its Notes column.
3. Build coverage matrix: rows = ACs, columns = (covered_by, status).
4. Output:
   - PASS if every AC has at least one test with status PASS.
   - FAIL if any AC has zero tests OR any covering test has status FAIL.
</procedure>


<output_format>
```
TRACE REPORT
============
AC-1: covered by [TC-3, TC-7] — PASS
AC-2: covered by [TC-4]       — PASS
AC-3: covered by []           — FAIL (no tests)
AC-4: covered by [TC-5]       — FAIL (TC-5 status is FAIL)

VERDICT: FAIL (2/4 ACs uncovered or failing)
```
</output_format>


</skill>
