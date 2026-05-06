# Troubleshooting

## Common issues

- Failed to apply Custom UI HUD commands: syntax error in .ui. Enable Diagnostic Mode in Hytale settings.
- Could not find document for Custom UI Append: wrong path or file not under Common/UI/Custom/.
- Unknown node type: unsupported or misspelled element type.
- Page stuck on Loading: missing sendUpdate in interactive page event handling.
- Client disconnect when opening UI: UI work not on world thread.
- Texture missing: wrong path or missing @2x.png suffix.
- Events not firing: selector does not match element ID.
- **"failed to load customui documents" crash on player join**: Any .ui file parse error crashes ALL UI loading for every joining player — not just the broken page. All .ui files are parsed at join time, not on open. Check every .ui file for syntax errors.
- **Element IDs cannot contain underscores.** Use alphanumeric camelCase only. `#Row0Name` is valid; `#Row_0_Name` is NOT. Underscore IDs silently fail to parse and cause the "failed to load customui documents" crash.
- **BOM in .ui files causes a parse crash.** Always write .ui files with UTF-8 encoding without BOM. In PowerShell, use `[System.IO.File]::WriteAllText(path, content, (New-Object System.Text.UTF8Encoding $false))` instead of `Set-Content`.
- **PowerShell here-string interpolation corrupts .ui files.** If your .ui contains `$Common` or `$C` variables, use single-quote here-strings `@'...'@` (non-interpolating). Double-quote `@"..."@` silently expands `$Common` to an empty string.
