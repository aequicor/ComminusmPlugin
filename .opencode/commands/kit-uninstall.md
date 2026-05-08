---
description: "Remove all ai-agent-kit-managed files from the current project. Reads the manifest, confirms with the user, then deletes every rendered config file/directory the binary produced. Optionally deletes the vault directory."
---
# /kit-uninstall

Remove all ai-agent-kit-managed files from the current project. Reads the manifest, confirms with the user, then deletes every rendered config file/directory the binary produced. Optionally deletes the vault directory.


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
Remove all ai-agent-kit-managed files from the current project. Reads the manifest, confirms with the user, then deletes every rendered config file/directory the binary produced. Optionally deletes the vault directory.

You are uninstalling ai-agent-kit from this project.

## Step 1 — Locate the manifest

1. Locate `.aikit/manifest.yaml`. If absent → STOP. Output: "Manifest not found. Cannot determine which files to remove. Delete `.aikit/`, `.claude/`, `.cursor/`, `.opencode/`, etc. by hand if you wish."

## Step 2 — Build the inventory

2. From `render_targets` in the manifest, list every target adapter's `config_dir`, `instruction_file`, `settings_file`, plus per-artifact paths (agents, skills, commands, rules, user_prompts).
3. Add: `.planning/` directory (kit-managed runtime state — but ASK before deletion since user may have ongoing work), `.aikit/manifest.yaml` itself.
4. Show user the complete list and ask:
   ```
   The following kit-managed paths will be deleted:
     - .claude/agents/*.md, .claude/skills/, .claude/commands/, .claude/settings.json, CLAUDE.md
     - .cursor/rules/*.mdc, .cursor/mcp.json
     - opencode.json, AGENTS.md
     - .aider.conf.yml, CONVENTIONS.md
     - .aikit/manifest.yaml
     - .planning/  ← contains your runtime task state. Keep or delete?
     - vault/specs/  ← contains user-authored specs/plans. Keep by default.

   Confirm:
     YES K  — delete kit files, KEEP .planning/ and vault/specs/
     YES P  — delete kit files AND .planning/, keep vault/specs/
     YES F  — delete EVERYTHING including vault/specs/ (full wipe; cannot undo)
     anything else — abort
   ```

## Step 3 — Delete

5. On confirmation, delete the inventory according to the user's choice. For each path:
   - Verify it's inside the project root.
   - Verify it matches the inventory list (do NOT delete anything not on the list).
   - Delete (file or empty directory).

6. Verify deletion. Report any paths that could not be deleted (permission errors, etc.).

## Step 4 — Report

7. Output summary:
   - Paths deleted: count + list.
   - Paths skipped: count + reason.
   - Vault status: kept / deleted.
   - Next steps: `git diff` to review, commit the removal.

## Safety rules

- **Never delete files outside the project root.**
- **Never delete anything not explicitly listed by the inventory.**
- **Never delete the vault root without explicit user confirmation (YES F).**
- **Never delete a nested AGENTS.md or CLAUDE.md that does not contain kit markers** — it may be hand-written by the user.
- **Stop immediately if user does not confirm.**
</workflow>
