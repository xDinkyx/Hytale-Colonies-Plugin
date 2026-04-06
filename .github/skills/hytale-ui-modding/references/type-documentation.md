# Type Documentation Reference

The type documentation is a generated index of all UI elements, property types, and enums available in markup. Use it as your reference when you need exact property names, types, or valid enum values.

Source: https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation

---

## Common Base Properties

All or most elements share these universal properties:

| Property | Type | Description |
|----------|------|-------------|
| `Anchor` | Anchor | How the element positions and sizes itself in its container |
| `Padding` | Padding | Inner spacing; affects child layout, not background |
| `FlexWeight` | Integer | Distributes remaining space proportionally among siblings |
| `Visible` | Boolean | Hides the element and removes it from layout calculations |
| `HitTestVisible` | Boolean | Forces this element to be returned on a HitTest (e.g., for hover effects on containers) |
| `Background` | PatchStyle / String | Background image or solid color |
| `MaskTexturePath` | UIPath (String) | Clipping mask texture (no 9-patch support yet) |
| `OutlineColor` | Color | Outline color to render around the element |
| `OutlineSize` | Float | Outline width in pixels |
| `ContentWidth` | Integer | If set, enables a horizontal scrollbar |
| `ContentHeight` | Integer | If set, enables a vertical scrollbar |
| `AutoScrollDown` | Boolean | Auto-scrolls to bottom; stops if user scrolls up |
| `KeepScrollPosition` | Boolean | Preserves scroll position after remount |
| `MouseWheelScrollBehaviour` | MouseWheelScrollBehaviourType | Mouse wheel scroll behavior |
| `Overscroll` | Boolean | Extends scroll area by the element's own size |
| `TooltipText` | String | Text tooltip shown on hover |
| `TooltipTextSpans` | List\<LabelSpan\> | Rich-text tooltip on hover |
| `TextTooltipStyle` | TextTooltipStyle | Style for the text tooltip |
| `TextTooltipShowDelay` | Float | Seconds before tooltip appears |

---

## Elements

Elements are the building blocks of UI. Each element type has specific properties on top of the common base properties.

### Commonly Used Elements

| Element | Accepts Children | Purpose |
|---------|-----------------|---------|
| `Group` | Yes | Generic container for layout |
| `Panel` | Yes | Same as Group; semantic distinction only |
| `Label` | No | Text display (plain or rich spans) |
| `Button` | Yes | Clickable container-button |
| `TextButton` | No | Clickable text-only button |
| `TextField` | No | Single-line text input |
| `MultilineTextField` | No | Multi-line text input |
| `NumberField` | No | Numeric text input |
| `Slider` | No | Integer range slider (draggable handle) |
| `FloatSlider` | No | Float range slider |
| `CheckBox` | No | Boolean toggle |
| `LabeledCheckBox` | No | Checkbox with inline label |
| `DropdownBox` | Yes | Dropdown selection |
| `ProgressBar` | No | Linear progress display |
| `CircularProgressBar` | No | Circular/ring progress display |
| `Sprite` | No | Spritesheet-based animation |
| `ItemIcon` | No | Display an item icon by ID |
| `ItemSlot` | No | Full item slot with quality background, quantity, durability |
| `ItemSlotButton` | Yes | Clickable item slot with auto tooltip handling |
| `ItemGrid` | No | Scrollable grid of item slots |
| `TabNavigation` | Yes | Tab bar that shows/hides child content groups |
| `TimerLabel` | No | Label that counts up or down from a seconds value |
| `AssetImage` | No | Display a UI texture asset |
| `ActionButton` | No | Button with icon and optional alignment |
| `ToggleButton` | No | Two-state toggle button |
| `SceneBlur` | No | Blurs the rendered scene beneath this element |

### Group / Panel

Container for layout. `Panel` is identical to `Group` in behavior.

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `LayoutMode` | LayoutMode | How children are arranged |
| `ScrollbarStyle` | ScrollbarStyle | Scrollbar appearance (required for scrolling modes) |

**Event Callbacks:** `Validating`, `Dismissing`, `Scrolled`

### Label

Displays text (plain `String` or rich `List<LabelSpan>`).

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `Text` | String | Plain text content |
| `TextSpans` | List\<LabelSpan\> | Rich text spans with per-span formatting |
| `Style` | LabelStyle | Font, size, color, alignment |

**Event Callbacks:** `LinkActivating`, `TagMouseEntered`

### Button

A clickable container. Can contain any children. Use `TextButton` for a simpler text-only button.

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `Disabled` | Boolean | If true, click events are not fired |
| `Style` | ButtonStyle | Visual states (Default, Hovered, Pressed, Disabled) |
| `LayoutMode` | LayoutMode | How children are arranged |
| `ScrollbarStyle` | ScrollbarStyle | Scrollbar for scrolling children |

**Event Callbacks:** `Activating`, `DoubleClicking`, `RightClicking`, `MouseEntered`, `MouseExited`

### TextButton

Text-only button. Does not accept children. Style via `TextButtonStyle`.

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `Text` | String | Button label text |
| `TextSpans` | List\<LabelSpan\> | Rich text button label |
| `Disabled` | Boolean | If true, click events are not fired |
| `Style` | TextButtonStyle | Visual style with state variants |

**Event Callbacks:** `Activating`, `DoubleClicking`, `RightClicking`, `MouseEntered`, `MouseExited`

### TextField

Single-line text input.

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `Value` | String | Current text value |
| `PlaceholderText` | String | Text shown when the field is empty |
| `PasswordChar` | Char | If set, replaces all characters with this char (e.g., `"*"` for passwords) |
| `Style` | InputFieldStyle | Text style |
| `PlaceholderStyle` | InputFieldStyle | Style for placeholder text |
| `Decoration` | InputFieldDecorationStyle | Field border/background style |
| `AutoFocus` | Boolean | Automatically focuses this field when mounted |
| `AutoSelectAll` | Boolean | Selects all text on mount (requires `AutoFocus: true`) |
| `IsReadOnly` | Boolean | Prevents editing |
| `MaxLength` | Integer | Maximum character count |

**Event Callbacks:** `RightClicking`, `Validating`, `Dismissing`, `FocusLost`, `FocusGained`, `ValueChanged`

### Slider

Integer range input with a draggable handle.

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `Value` | Integer | Current value |
| `Min` | Integer | Minimum allowed value |
| `Max` | Integer | Maximum allowed value |
| `Step` | Integer | Increment/decrement amount |
| `IsReadOnly` | Boolean | Prevents dragging |
| `Style` | SliderStyle | Slider track and handle appearance |

**Event Callbacks:** `MouseButtonReleased`, `ValueChanged`

### CheckBox

Boolean toggle input.

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `Value` | Boolean | Current checked state |
| `Disabled` | Boolean | Prevents interaction |
| `Style` | CheckBoxStyle | Visual style |

**Event Callbacks:** `ValueChanged`

### DropdownBox

Dropdown selector. Can accept child `DropdownEntry` elements.

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `Entries` | IReadOnlyList | Dynamic entry list (set from Java) |
| `Value` | String | Currently selected value |
| `SelectedValues` | List\<String\> | All selected values (multi-select) |
| `Disabled` | Boolean | Prevents interaction |
| `IsReadOnly` | Boolean | Prevents interaction |
| `Style` | DropdownBoxStyle | Visual style |
| `PanelTitleText` | String | Title text in the dropdown panel |
| `MaxSelection` | Integer | Maximum number of selectable items |
| `ShowSearchInput` | Boolean | Shows a search input inside the dropdown |
| `ShowLabel` | Boolean | Shows the current selection label |
| `ForcedLabel` | String | Overrides the displayed label text |
| `NoItemsText` | String | Text shown when no entries exist |
| `DisplayNonExistingValue` | Boolean | Shows the value even if it is not in `Entries` |

**Event Callbacks:** `ValueChanged`, `DropdownToggled`

### ProgressBar

Linear progress indicator.

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `Value` | Float | Current progress (0.0 – 1.0) |
| `Bar` | PatchStyle / String | Bar fill appearance |
| `BarTexturePath` | UIPath | Bar fill texture |
| `EffectTexturePath` | UIPath | Animated effect overlay texture |
| `EffectWidth` | Integer | Width of the effect region |
| `EffectHeight` | Integer | Height of the effect region |
| `EffectOffset` | Integer | Offset of the effect region |
| `Alignment` | ProgressBarAlignment | `Horizontal` or `Vertical` |
| `Direction` | ProgressBarDirection | `Start` or `End` (fill direction) |

### CircularProgressBar

Circular/ring progress indicator.

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `Value` | Float | Current progress (0.0 – 1.0) |
| `MaskTexturePath` | UIPath | Mask texture that defines the ring shape |

### Sprite

Spritesheet-based animation.

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `TexturePath` | UIPath | The spritesheet texture file |
| `Frame` | SpriteFrame | Layout of the sprite sheet (columns, rows, etc.) |
| `FramesPerSecond` | Integer | Animation playback speed |
| `IsPlaying` | Boolean | Whether the animation is currently playing |
| `AutoPlay` | Boolean | Starts playing automatically when mounted |
| `Angle` | Float | Rotates the element by this many degrees |
| `RepeatCount` | Integer | Number of times to repeat; `0` = infinite loop |

### ItemIcon

Displays an item icon by asset ID. No quantity shown. For full item slots (with quality background, quantity, durability), use `ItemSlot` inside an `ItemSlotButton`.

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `ItemId` | String | Item asset ID |
| `ShowItemTooltip` | Boolean | Shows the full item tooltip on hover. Do not set when inside an `ItemSlotButton` (parent handles tooltips). |

### ItemGrid

Scrollable grid of item slots.

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `ItemStacks` | ClientItemStack[] | The item data for each slot |
| `Slots` | ItemGridSlot[] | Slot configuration overrides |
| `SlotsPerRow` | Integer | Number of columns |
| `ShowScrollbar` | Boolean | Enables the scrollbar |
| `ScrollbarStyle` | ScrollbarStyle | Scrollbar appearance |
| `Style` | ItemGridStyle | Grid slot visual style |
| `RenderItemQualityBackground` | Boolean | Renders quality-colored backgrounds |
| `InfoDisplay` | ItemGridInfoDisplayMode | How item info is displayed |
| `AdjacentInfoPaneGridWidth` | Integer | Width for the adjacent info pane |
| `AreItemsDraggable` | Boolean | Allows drag-and-drop |
| `InventorySectionId` | Integer | Links this grid to a player inventory section |
| `AllowMaxStackDraggableItems` | Boolean | Allows dragging full stacks |
| `DisplayItemQuantity` | Boolean | Shows quantity numbers on slots |

**Event Callbacks:** `SlotDoubleClicking`, `DragCancelled`, `SlotMouseEntered`, `SlotMouseExited`

### TabNavigation

Tab bar that controls visibility of child content groups.

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `Tabs` | Tab[] | Array of tab definitions — each tab has `Id` (String) and `Text` (String) |
| `SelectedTab` | String | The `Id` of the currently active tab |
| `AllowUnselection` | Boolean | Allows deselecting all tabs |
| `Style` | TabNavigationStyle | Tab bar appearance |

**Example:**
```ui
TabNavigation #MyTabNavigation {
    SelectedTab: "TabOne";
    Style: @CustomTopTabStyle;
    Tabs: [
        ( Id: "TabOne", Text: "Tab One", ),
        ( Id: "TabTwo", Text: "Tab Two" )
    ];
}
```

**Known Issue:** The default `@TopTabsStyle` and `@HeaderTabsStyle` in `Common.ui` have a syntax mismatch — the child tab style property should be `TabStyleState`, not `TabStateStyle`. Use them only as a reference. Also, the default texture assets (`TabOverlay@2x.png`, `TabSelectedOverlay@2x.png`) must be copied from the Hytale install directory. See `tab-navigation.md` for workarounds.

**Event Callbacks:** `SelectedTabChanged`

### TimerLabel

A label that counts up or down from a starting number of seconds.

**Additional Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `Seconds` | Integer | Starting value in seconds |
| `Direction` | TimerDirection | `Up` (count up) or `Down` (count down) |
| `Paused` | Boolean | Pauses the timer when true |
| `Text` | String | Static text prefix/suffix (optional) |
| `TextSpans` | List\<LabelSpan\> | Rich text prefix/suffix |
| `Style` | LabelStyle | Text appearance |

**Event Callbacks:** `LinkActivating`, `TagMouseEntered`

### Full Element List

- ActionButton
- AssetImage
- BackButton
- BlockSelector
- Button
- CharacterPreviewComponent
- CheckBox
- CheckBoxContainer
- CircularProgressBar
- CodeEditor
- ColorOptionGrid
- ColorPicker
- ColorPickerDropdownBox
- CompactTextField
- DropdownBox
- DropdownEntry
- DynamicPane
- DynamicPaneContainer
- FloatSlider
- FloatSliderNumberField
- Group
- HotkeyLabel
- ItemGrid
- ItemIcon
- ItemPreviewComponent
- ItemSlot
- ItemSlotButton
- Label
- LabeledCheckBox
- MenuItem
- MultilineTextField
- NumberField
- Panel
- ProgressBar
- ReorderableList
- ReorderableListGrip
- SceneBlur
- Slider
- SliderNumberField
- Sprite
- TabButton
- TabNavigation
- TextButton
- TextField
- TimerLabel
- ToggleButton

---

## Property Types

Property types define the structure of complex values used in element properties.

### LabelStyle

Controls text appearance for `Label`, `TextButton`, `TimerLabel`.

| Property | Type | Description |
|----------|------|-------------|
| `FontName` | Font Name (String) | `"Default"`, `"Secondary"`, or `"Mono"` |
| `FontSize` | Float | Font size |
| `TextColor` | Color | Text color |
| `OutlineColor` | Color | Text outline color |
| `LetterSpacing` | Float | Spacing between characters |
| `HorizontalAlignment` | LabelAlignment | `Left`, `Right`, `Center` |
| `VerticalAlignment` | LabelAlignment | `Left`, `Right`, `Center` |
| `Alignment` | LabelAlignment | Shorthand for both alignments |
| `Wrap` | Boolean | Whether text wraps to multiple lines |
| `RenderUppercase` | Boolean | Renders text in uppercase |
| `RenderBold` | Boolean | Bold rendering |
| `RenderItalics` | Boolean | Italic rendering |
| `RenderUnderlined` | Boolean | Underline rendering |

### LabelSpan

A run of text with individual formatting, used in `TextSpans` arrays.

| Property | Type | Description |
|----------|------|-------------|
| `Text` | String | The text content of this span |
| `Color` | Color | Text color for this span |
| `OutlineColor` | Color | Outline color for this span |
| `IsBold` | Boolean | Bold formatting |
| `IsItalics` | Boolean | Italic formatting |
| `IsUppercase` | Boolean | Uppercase rendering |
| `IsMonospace` | Boolean | Monospace font |
| `IsUnderlined` | Boolean | Underline |
| `Link` | String | Makes the span a clickable link (fires `LinkActivating`) |
| `Params` | Dictionary | Extra parameters |

### ButtonStyle

Visual states for `Button` elements.

| Property | Type | Description |
|----------|------|-------------|
| `Default` | ButtonStyleState | Normal state appearance |
| `Hovered` | ButtonStyleState | Hover state appearance |
| `Pressed` | ButtonStyleState | Pressed state appearance |
| `Disabled` | ButtonStyleState | Disabled state appearance |
| `Sounds` | ButtonSounds | Click/hover sound effects |

### PatchStyle

Nine-slice scalable background/image. Used for `Background` and bar textures.

| Property | Type | Description |
|----------|------|-------------|
| `TexturePath` | UIPath | Source texture |
| `Border` | Integer | Uniform border slice size |
| `HorizontalBorder` | Integer | Left/right border slice size |
| `VerticalBorder` | Integer | Top/bottom border slice size |
| `Color` | Color | Tint color |
| `Area` | Padding | Which area of the texture to use |
| `Anchor` | Anchor | How to anchor/scale the texture |

### ScrollbarStyle

Controls the appearance of scrollbars.

| Property | Type | Description |
|----------|------|-------------|
| `Size` | Integer | Scrollbar width/height in pixels |
| `Spacing` | Integer | Gap between scrollbar and content |
| `OnlyVisibleWhenHovered` | Boolean | Hides scrollbar until parent is hovered |
| `Background` | PatchStyle / String | Track background |
| `Handle` | PatchStyle / String | Thumb/handle appearance |
| `HoveredHandle` | PatchStyle / String | Thumb on hover |
| `DraggedHandle` | PatchStyle / String | Thumb while dragging |

### SpriteFrame

Defines the layout of a spritesheet texture.

### Tab

A tab entry used in `TabNavigation.Tabs`.

| Property | Type | Description |
|----------|------|-------------|
| `Id` | String | Unique identifier for this tab |
| `Text` | String | Display label |

### Padding

| Property | Type |
|----------|------|
| `Full` | Integer (uniform) |
| `Top` | Integer |
| `Bottom` | Integer |
| `Left` | Integer |
| `Right` | Integer |
| `Horizontal` | Integer (left+right) |
| `Vertical` | Integer (top+bottom) |

### Anchor

| Property | Type |
|----------|------|
| `Full` | Integer (all edges) |
| `Top` | Integer |
| `Bottom` | Integer |
| `Left` | Integer |
| `Right` | Integer |
| `Width` | Integer |
| `Height` | Integer |
| `MinWidth` | Integer |
| `MaxWidth` | Integer |
| `MinHeight` | Integer |
| `MaxHeight` | Integer |
| `Horizontal` | Integer (left+right) |
| `Vertical` | Integer (top+bottom) |

### Full Property Type List

- Anchor
- BlockSelectorStyle
- ButtonSounds
- ButtonStyle
- ButtonStyleState
- CheckBoxStyle
- CheckBoxStyleState
- ClientItemStack
- ColorOptionGridStyle
- ColorPickerDropdownBoxStateBackground
- ColorPickerDropdownBoxStyle
- ColorPickerStyle
- DropdownBoxSearchInputStyle
- DropdownBoxSounds
- DropdownBoxStyle
- InputFieldButtonStyle
- InputFieldDecorationStyle
- InputFieldDecorationStyleState
- InputFieldIcon
- InputFieldStyle
- ItemGridSlot
- ItemGridStyle
- LabeledCheckBoxStyle
- LabeledCheckBoxStyleState
- LabelSpan
- LabelStyle
- NumberFieldFormat
- Padding
- PatchStyle
- PopupStyle
- ScrollbarStyle
- SliderStyle
- SoundStyle
- SpriteFrame
- SubMenuItemStyle
- SubMenuItemStyleState
- Tab
- TabNavigationStyle
- TabStyle
- TabStyleState
- TextButtonStyle
- TextButtonStyleState
- TextTooltipStyle
- ToggleButtonStyle
- ToggleButtonStyleState

---

## Enums

Enums define valid values for certain properties.

### Commonly Used Enums

| Enum | Values | Purpose |
|------|--------|---------|
| `LayoutMode` | Top, Bottom, Left, Right, Center, Middle, CenterMiddle, MiddleCenter, Full, TopScrolling, BottomScrolling, LeftScrolling, RightScrolling, LeftCenterWrap, RightCenterWrap | How a container arranges children |
| `LabelAlignment` | Left, Right, Center | Text horizontal alignment |
| `ProgressBarDirection` | Start, End | Progress bar fill direction |
| `ProgressBarAlignment` | Horizontal, Vertical | Progress bar alignment axis |
| `TimerDirection` | Up, Down | Timer count direction |
| `ResizeType` | None, Horizontal, Vertical, Both | Resize behavior |

### Full Enum List

- ActionButtonAlignment
- CodeEditorLanguage
- ColorFormat
- DropdownBoxAlign
- InputFieldButtonSide
- InputFieldIconSide
- ItemGridInfoDisplayMode
- LabelAlignment
- LayoutMode
- MouseWheelScrollBehaviourType
- ProgressBarAlignment
- ProgressBarDirection
- ResizeType
- TimerDirection
- TooltipAlignment

---

## How to Use Type Documentation

When you need specific details:

1. **Find the element type** you're working with
2. **Look up its properties** in the element documentation
3. **Check the property type** to understand what structure is expected
4. **Reference enums** for valid values when a property expects an enum

### Example: ProgressBar

To create a progress bar:

```ui
ProgressBar #HealthBar {
    Anchor: (Width: 200, Height: 20);
    Value: 0.75;
    Direction: Start;       // ProgressBarDirection enum
    Alignment: Horizontal;  // ProgressBarAlignment enum
    Color: #22cc22;
}
```

---

## Online Reference

For the most up-to-date and detailed documentation, visit:
https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation

The online documentation includes:
- Complete property lists for each element
- Detailed descriptions of each property type
- All valid enum values with descriptions

---

Source: https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation
