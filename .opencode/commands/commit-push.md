---
description: Commit all changes and push to remote
---

Commit all changes and push to remote. Follow the Git Safety Protocol:
1. Run git status to see all untracked and modified files
2. Run git diff to review staged and unstaged changes
3. Run git log --oneline -5 to see recent commit messages
4. Analyze changes and draft a commit message (1-2 sentences, focus on "why")
5. Run git add with relevant files (avoid adding files with secrets like .env, credentials.json)
6. Run git commit -m with the message
7. Run git push to push to remote
8. If commit fails due to pre-commit hook, fix the issue and create a new commit (NEVER amend)
9. If push fails, report the error to user

Never use git commit --amend unless user explicitly requests it.
Never run destructive commands without explicit user request.