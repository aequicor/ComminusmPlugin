---
description: Run detekt static analysis, check compiler warnings and fix all issues
---

Run detekt static analysis, check Kotlin compiler warnings and fix all detected issues.

Steps:
1. Run detekt: `./gradlew detekt`
2. Run compiler warnings check: `./gradlew compileKotlin -PwarningsAsErrors=true`
3. Review both report outputs
4. For each issue found, fix the code to comply with Kotlin style rules
5. After all fixes, run both checks again to verify all issues are resolved
6. Report summary of fixed issues to user

Focus on fixing:
- Code style violations
- Complexity issues
- Naming conventions
- Potential bugs
- Empty blocks
- Documentation issues
- Kotlin compiler warnings

(End of file - total 24 lines)