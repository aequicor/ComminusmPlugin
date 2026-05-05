import re
import sys

SUBS = {
    "{{PROJECT_NAME}}": "ComminusmPlugin",
    "{{VAULT_PATH}}": "vault",
    "{{STACK_DESCRIPTION}}": "kotlin stack",
    "{{PROVIDER_ID}}": "ollama_cloud",
    "{{DEFAULT_MODEL}}": "kimi-k2.6:cloud",
    "{{CODER_MODEL}}": "deepseek-v4-flash:cloud",
    "{{REVIEWER_MODEL}}": "deepseek-v4-pro:cloud",
    "{{DESIGNER_MODEL}}": "glm-5.1:cloud",
    "{{SMALL_MODEL}}": "deepseek-v4-flash:cloud",
    "{{LANG}}": "ru",
    "{{KIT_VERSION}}": "2.2.0",
    "{{KIT_REPO}}": "aequicor/ai-agent-kit",
    "{{ISO_TIMESTAMP_PLACEHOLDER}}": "2026-05-05",
    "{{BUILD_COMMAND}}": "./gradlew",
    "{{COMPILE_COMMAND}}": "./gradlew compileKotlin",
    "{{LINT_COMMAND}}": "./gradlew detekt ktlintCheck",
    "{{TEST_COMMAND_TEMPLATE}}": "./gradlew :[module]:test",
    "{{MODULE_0_NAME}}": "comminusm",
    "{{MODULE_0_GRADLE}}": "—",
    "{{MODULE_0_SOURCE}}": "src/main/kotlin/ru/kyamshanov/comminusm/",
    "{{MODULE_0_TEST}}": "src/test/kotlin/ru/kyamshanov/comminusm/",
    "{{MODULE_0_DOCS}}": "vault/comminusm/",
    "{{MODULE_0_RESPONSIBILITY}}": "Minecraft Paper plugin — communism-themed gameplay mechanics",
    "{{OPPENCODE_LANG}}": "ru",
    # Add table variables that are typically expanded during setup
    "{{MODULE_TABLE}}": """| Module  | Gradle module | Docs              | Responsibility                                  |
|---------|---------------|-------------------|-------------------------------------------------|
| comminusm | —           | vault/comminusm/  | Minecraft Paper plugin — communism-themed gameplay mechanics |""",
    "{{MODULE_SOURCE_TABLE}}": "**comminusm**: `src/main/kotlin/ru/kyamshanov/comminusm/`",
    "{{MODULE_TEST_TABLE}}": "**comminusm**: `src/test/kotlin/ru/kyamshanov/comminusm/`",
    "{{MODULE_BUILD_COMMANDS}}": """```bash
./gradlew compileKotlin
./gradlew :comminusm:test
```""",
}

infile = sys.argv[1]
outfile = sys.argv[2]

with open(infile, "r") as f:
    content = f.read()

for key, val in SUBS.items():
    content = content.replace(key, val)

# Write remaining undefined vars as-is but warn
undefined = set(re.findall(r"\{\{[A-Z_0-9]+\}\}", content))
if undefined:
    print(f"WARN: undefined vars in {outfile}: {undefined}", file=sys.stderr)

with open(outfile, "w") as f:
    f.write(content)

print(f"OK: {outfile}")
