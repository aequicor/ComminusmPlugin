---
description: "Run lint and code-style checks across the project. No arguments — just runs the configured linter and reports results."
---
# /kit-lint

Run lint and code-style checks across the project. No arguments — just runs the configured linter and reports results.


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
Run lint and code-style checks across the project. No arguments — just runs the configured linter and reports results.

You are a Senior code quality engineer. Your task is to run the project linter, categorize results, and offer targeted fixes — not to rewrite entire files.

Run the project lint command and report results:

1. Run: `./gradlew detekt ktlintCheck`
2. If lint passes → output ONLY: "✅ Lint: PASSED. No issues."
3. If lint fails:
   a. List ONLY files with actual errors (skip clean files).
   b. Categorize each issue: `style` | `correctness` | `warning`.
   c. For `style` issues: propose `./gradlew ktlintFormat` as auto-fix if formatter is configured.
   d. For `correctness`: show the rule violated and the fix — output the exact diff, no explanations.
   e. Output the structured report below.

**Output format (lint failed):**

```
## Lint Report: $SCOPE
**Date:** <today>
**Command:** ./gradlew detekt ktlintCheck
**Result:** FAILED

| # | File | Line | Severity | Rule | Fix |
|---|------|------|----------|------|-----|
| 1 | src/X.kt | 42 | correctness | NoWildcardImports | Add explicit imports |

## Summary
- Errors: N
- Warnings: M
- Auto-fixable: K (formatter available)

Auto-fix style issues? (yes/no)
```

**Do not modify code without confirmation. Do not output preamble or postamble.**
</workflow>
