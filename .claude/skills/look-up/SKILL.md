---
name: "look-up"
description: "Proactively look up documentation when working with an unfamiliar library, new instruction set, or any topic where knowledge is missing or uncertain. Searches the local knowledge index first; if nothing is found, researches the internet and indexes the result for future use."
---
<skill name="look-up">

<purpose>
Proactively look up documentation when working with an unfamiliar library, new instruction set, or any topic where knowledge is missing or uncertain. Searches the local knowledge index first; if nothing is found, researches the internet and indexes the result for future use.
</purpose>




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
Proactively look up documentation when working with an unfamiliar library, new instruction set, or any topic where knowledge is missing or uncertain. Searches the local knowledge index first; if nothing is found, researches the internet and indexes the result for future use.

# LOOKUP

Skill for proactive knowledge gap resolution. Use this skill before writing code or giving advice whenever you encounter something unfamiliar or uncertain.

## When to trigger

Activate this skill when **any** of the following is true:

- You are about to use a library, framework, or API you have not used in this project before
- You encounter an instruction, pattern, or convention you are unfamiliar with
- You are unsure about the correct API, version-specific behavior, or best practice
- You lack enough context to confidently complete the task
- You recognize that your training knowledge may be outdated for the topic at hand

**Do not skip this skill** and proceed on assumptions — an incorrect guess wastes more session tokens than a lookup.

## Input

| Field | Required | Description |
|-------|----------|-------------|
| `query` | Yes | The library name, concept, or question — free-form text |
| `module` | No | Target module if known. Used as a frontmatter filter (`fm.module`) when set |
| `type` | No | Document type: `documentation`, `guideline`, `specification`, `tutorial`, `reference`, `recipe`. Default `documentation`. Maps to frontmatter filter `fm.type`. |

## Algorithm

The protocol for searching and writing knowledge is defined in the memory
section above. Apply the lookup-specific policy below on top of that.

{{snippet:memory_policy.lookup}}

### Document type → filter mapping

| `type` input | Filter applied |
|------|----------------|
| `documentation` | `{"fm.type": "documentation"}` |
| `guideline` | `{"fm.type": "guideline"}` |
| `specification` | `{"fm.type": "specification"}` |
| `tutorial` | `{"fm.type": "tutorial"}` |
| `reference` | `{"fm.type": "reference"}` |
| `recipe` | `{"fm.type": "recipe"}` |

If `module` is provided, AND it into the filter map: `{"fm.type": "...", "fm.module": "<module>"}`.

If type is unclear, use `documentation`.

## Principles

- **Look up first, code second.** Never assume API signatures, configuration keys, or behavior.
- **One lookup per unknown.** If multiple unknowns appear in one task, resolve them all before starting implementation.
- **Trust the index.** If a document was found, use it as the primary source — but check `verified_at` if present; treat docs older than 6 months on fast-moving libraries (frontend frameworks, LLM SDKs, cloud APIs) as needing re-verification.
- **Index what you learn.** When you research something manually, persist it so future lookups are instant. The next session will not have your context — leave a trail.
- **Wikilinks for relationships.** When the topic relates to existing docs, embed `[[other-doc]]` references in the body so retrieval auto-surfaces them.

## Error Handling

| Situation | Action |
|-----------|--------|
| KnowledgeOS is enabled but `search_docs` errors / times out | Log it, fall back to filesystem grep over `vault/specs/guidelines/**` for one attempt, then proceed with WebSearch as if no index existed. Do not block. |
| WebSearch returns irrelevant results | Refine query: add language name, version, or specific API. Try 2 more queries with different keywords. If still irrelevant → report `LOOKUP FAILED`. Proceed with caution — flag uncertainty in output. |
| WebFetch fails (site blocks, timeout, 403) | Skip that source, try the next search result. If all fail → fall back to summarizing what you know and mark it uncertain. |
| `write_doc` fails (KnowledgeOS unreachable mid-task) | Save the researched content as a local file at the filesystem path `vault/specs/guidelines/<module>/lookup-<topic>.md`. The file watcher will index it when KnowledgeOS comes back. Report the path. |
| Found document is outdated (deprecated API, old version pin) | Note the version mismatch. Research the current version via WebSearch. `update_doc` with `preserve_frontmatter: true`, bumping `verified_at`. If KnowledgeOS is disabled, edit the local file and re-stage. |
</procedure>



</skill>
