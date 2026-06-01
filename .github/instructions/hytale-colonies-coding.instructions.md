---
applyTo: "**/*.java"
---

# HytaleColonies Naming Conventions

Method names must describe the primary action(s) performed, not what triggered the method or who called it.

- Bad: `onBlockBroken`, `executeDispatchToClearingOnWorldThread`, `handleItemsRetrieved`
- Good: `claimAndStartClearing`, `startBuilding`

**Prefer splitting** methods that perform multiple actions into smaller, focused methods — each with a clear, descriptive name. When a method genuinely does multiple things and cannot be split, use a long compound name that lists the actions (e.g., `saveProgressAndNotifyColonists`). Do not abbreviate or obscure what the method does to keep the name short.

`world.execute()` callbacks with meaningful multi-line logic must be extracted into named methods.
