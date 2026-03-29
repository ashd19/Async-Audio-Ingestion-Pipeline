# Mandates for Gemini CLI

- **Explicit Approval Required:** For any file edit, modification, or creation, the agent MUST first present a specific, detailed plan of the changes and wait for the user to provide an explicit "Go" or "Proceed" instruction.
- **No Autonomous Edits:** The agent must NEVER perform edits based on assumptions or as part of a multi-step execution without stopping for confirmation at each modification step.
- **Directives Only:** Treat all user requests as Inquiries unless they are explicit Directives. Even for Directives, if they involve file system changes, always confirm the final plan before executing.
