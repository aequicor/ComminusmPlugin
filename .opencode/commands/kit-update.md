---
description: "Update kit-managed files to the latest version of this kit. Re-runs `kit-setup verify` then `kit-setup generate` against the current `.aikit/manifest.yaml`. Skip-list ensures user-authored content under `vault/specs/`, `.planning/CURRENT.md`, and `.planning/tasks/**` is never overwritten."
---
# /kit-update

Update kit-managed files to the latest version of this kit. Re-runs `kit-setup verify` then `kit-setup generate` against the current `.aikit/manifest.yaml`. Skip-list ensures user-authored content under `vault/specs/`, `.planning/CURRENT.md`, and `.planning/tasks/**` is never overwritten.


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
Update kit-managed files to the latest version of this kit. Re-runs `kit-setup verify` then `kit-setup generate` against the current `.aikit/manifest.yaml`. Skip-list ensures user-authored content under `vault/specs/`, `.planning/CURRENT.md`, and `.planning/tasks/**` is never overwritten.

You are upgrading an ai-agent-kit installation. The binary `kit-setup` is the source of truth for rendering; this command runs it and reports what changed.

## Step 1 — Pre-flight

1. Locate `.aikit/manifest.yaml`. If absent → STOP. Output: "Manifest not found. Run `kit-setup generate <path>` first."

2. Run `kit-setup verify .aikit/manifest.yaml`. If it errors → STOP, surface the JSON output to the user. Manifest must be valid before re-generation.

3. Read `kit_version` from manifest. If a newer kit is available (the user upgraded the binary), prompt: "Current kit_version: <current>; binary version: <binary>. Re-render with binary version?" Wait for `/kit-approve`.

## Step 2 — Re-generate

4. Run `kit-setup generate .aikit/manifest.yaml`.
5. Capture the JSON output: `{"ok": bool, "generated": [...], "errors": [...]?}`.
6. If `ok: false` → STOP, surface errors. Do not partial-apply.

## Step 3 — Report

7. Output:
   - Files written: count + per-file list (from `generated` array).
   - Files unchanged: any kit-managed file the binary did not regenerate.
   - User-authored content preserved: confirm `vault/specs/features/**`, `vault/specs/guidelines/**`, `vault/specs/tech-debt/**`, `.planning/CURRENT.md`, `.planning/tasks/**` are untouched.
   - Recommended next steps: `git diff`, review the diff for unexpected changes, commit.

8. If `kit_version` was bumped during this run, surface the change.

## Safety rules

- **Never modify files outside the project root.**
- **Never delete user files.** The binary's `generated` list is the only thing this command touches.
- **Never touch `vault/specs/features/**`, `vault/specs/guidelines/**`, `vault/specs/tech-debt/**`** — user content. The binary's path-allowlist enforces this.
- **Never touch `.planning/CURRENT.md` (local pointer), `.planning/tasks/*.md`, `.planning/tasks/done/*.md`, `.planning/DECISIONS.md`** — runtime state.
- **If manifest contains a literal-looking API key** → STOP and warn user before proceeding. The binary's secret-scan also catches this and refuses to render.
</workflow>
