# TabNavigation Reference

`TabNavigation` is a UI element that provides a set of tabs for switching between different views.

## Example

A `TabNavigation` element requires two properties to function: `Tabs` and `Style`.

```ui
TabNavigation #MyTabNavigation {
    SelectedTab: "TabOne";
    Style: @CustomTopTabStyle;
    Tabs: [
        ( Id: "TabOne", Text: "Tab One", ),
        ( Id: "TabTwo", Text: "Tab Two", )
    ];
}
```

-   `SelectedTab`: The `Id` of the tab that is selected by default.
-   `Style`: The style to apply to the tabs. See the known issue below.
-   `Tabs`: A list of tab definitions, each with an `Id` and `Text`.

## Known Issue

There is a known issue with the default tab navigation styles provided in `Common.ui`.

-   **Bug Report**: [HytaleModding/suggestions#83](https://github.com/HytaleModding/suggestions/discussions/83)
-   **Problem**: The default styles `@TopTabsStyle` and `@HeaderTabsStyle` have a syntax error in a child tab style. They use `TabStateStyle` instead of the correct `TabStyleState`. This can cause the UI to fail to load or crash.
-   **Workaround**: At present, developers should use `@TopTabsStyle` and `@HeaderTabsStyle` only as a reference for creating their own custom tab styles. Do not use them directly.

Additionally, the default texture assets for tabs (e.g., `TabOverlay@2x.png`, `TabSelectedOverlay@2x.png`) are missing and would need to be copied into your project from the Hytale install directory.
