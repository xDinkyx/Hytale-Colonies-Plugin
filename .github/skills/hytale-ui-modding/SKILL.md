---
name: hytale-ui-modding
description: Comprehensive guidance for Hytale plugin UI modding using native .ui files, Common.ui styling, layout and markup rules, and the Java UI API (CustomUIHud, MultipleHUD, CustomUIPage, InteractiveCustomUIPage). Use when creating or updating custom HUDs/pages, writing .ui markup, binding UI events, or troubleshooting UI issues.
---

# Hytale Native UI Modding Skill

Use this skill for Hytale's native .ui system and the server-side Java UI API.

Important: use native UI only. Do not use HyUI.

## Quick start

1. Read the architecture overview first. See references/overview.md.
2. Put .ui files under src/main/resources/Common/UI/Custom/.
3. Import Common.ui when you want shared styles and components. See references/common-styling.md.
4. Build layout with Anchor, Padding, and LayoutMode. See references/layout.md.
5. Use markup patterns like named expressions, templates, and translations. See references/markup.md.
6. Bind UI events with UIEventBuilder and always sendUpdate after handling events. See references/java-api.md and references/events.md.
7. For assets, use @2x.png and set IncludesAssetPack in manifest. See references/assets-and-packaging.md.
8. If something fails at runtime, check references/troubleshooting.md.

## Reference library

- references/INDEX.md
- references/overview.md
- references/common-styling.md
- references/layout.md
- references/markup.md
- references/type-documentation.md
- references/java-api.md
- references/anchor-ui.md
- references/events.md
- references/assets-and-packaging.md
- references/examples.md
- references/troubleshooting.md
- references/tab-navigation.md

## Project conventions

- .ui base path: Common/UI/Custom/. Relative paths inside .ui are resolved from the file location.
- Use %translation.key in .ui and add the key to the language files under src/main/resources/Server/Languages.
- Use MultipleHUD for multiple HUDs per player. Do not rely on a single CustomUIHud instance.

## Official documentation

- https://hytalemodding.dev/en/docs/official-documentation/custom-ui/common-styling
- https://hytalemodding.dev/en/docs/official-documentation/custom-ui/layout
- https://hytalemodding.dev/en/docs/official-documentation/custom-ui/markup
- https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation

## Notes on recent doc updates

- The official type documentation is a generated index. Use it when you need the exact property name, type, or enum values for an element.
- Common.ui is the preferred source for cohesive styling. Import it and reference styles instead of duplicating them.

- **TabNavigation Known Issue**: The default styles for `TabNavigation` in `Common.ui` are broken. See `references/tab-navigation.md` for details and workarounds.

## Item HUD UI Support

`CustomUIHud` can now be attached to **any item**, not just players. This enables per-item HUDs that display when a specific item is held or active. Use the normal `CustomUIHud` API and associate the HUD with the item's lifecycle events.

Key use cases:
- Charge bar / cooldown indicator shown while holding a specific weapon
- Ammo counter rendered when a bow or gun is in hand
- Item-specific ability status overlays
