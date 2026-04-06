# Layout Reference

The layout system determines how UI elements are positioned and sized on screen. Understanding layout is crucial for creating well-structured, responsive interfaces.

## Layout Fundamentals

Every UI element has four key layout concepts:

1.  **Container Rectangle** - The space allocated by the parent element
2.  **Anchor** - How the element positions and sizes itself within the container
3.  **Padding** - Inner spacing that affects where children are positioned
4.  **LayoutMode** - How the element arranges its children (if it is a container)

Visual representation:

```
+-------------------------------------+
|  Container Rectangle (from parent)  |
|  +-------------------------------+  |
|  |  Anchored Rectangle           |  |
|  |  +-------------------------+  |  |
|  |  | Padding                 |  |  |
|  |  |  +-------------------+  |  |  |
|  |  |  |  Content Area     |  |  |  |
|  |  |  +-------------------+  |  |  |
|  |  +-------------------------+  |  |
|  +-------------------------------+  |
+-------------------------------------+
```

---

## Anchor

Anchor controls how an element positions and sizes itself within its container rectangle.

### Anchor Properties

| Property | Type | Description |
|----------|------|-------------|
| `Left` | int | Distance from container's left edge |
| `Right` | int | Distance from container's right edge |
| `Top` | int | Distance from container's top edge |
| `Bottom` | int | Distance from container's bottom edge |
| `Width` | int | Fixed width in pixels |
| `Height` | int | Fixed height in pixels |
| `MinWidth` | int | Minimum width constraint |
| `MaxWidth` | int | Maximum width constraint |
| `MinHeight` | int | Minimum height constraint |
| `MaxHeight` | int | Maximum height constraint |

### Shorthand Properties

| Shorthand | Expands To |
|-----------|------------|
| `Full` | Left, Top, Right, Bottom (all sides) |
| `Horizontal` | Left and Right |
| `Vertical` | Top and Bottom |

### Fixed Size

Creates an element with explicit dimensions:

```ui
Button {
    Anchor: (Width: 200, Height: 40);
}
```

### Positioning

Position an element at specific offsets from the container edges:

```ui
Label {
    Anchor: (Top: 10, Left: 20, Width: 100, Height: 30);
}
```

### Anchoring to Edges

Anchor to bottom-right corner:

```ui
Button {
    Anchor: (Bottom: 10, Right: 10, Width: 100, Height: 30);
}
```

### Stretching

Stretches to fill the entire container:

```ui
Group {
    Anchor: (Top: 0, Bottom: 0, Left: 0, Right: 0);
}
```

Shorthand:

```ui
Group {
    Anchor: (Full: 0);
}
```

### Mixed Anchoring

Combine fixed dimensions with stretching:

```ui
Panel {
    Anchor: (Top: 10, Bottom: 10, Left: 20, Width: 300);
}
```

-   Fixed width of 300 pixels
-   Stretches vertically between top and bottom edges (10px margin)
-   20 pixels from the left

---

## Padding

`Padding` creates inner spacing, affecting where children are positioned.

### Uniform Padding

```ui
Group {
    Padding: (Full: 20);
}
```

### Directional Padding

```ui
Group {
    Padding: (Top: 10, Bottom: 20, Left: 15, Right: 15);
}
```

### Shorthand

```ui
// Horizontal and vertical
Group {
    Padding: (Horizontal: 20, Vertical: 10);
}
```

---

## LayoutMode

`LayoutMode` determines how a container arranges its children.

### `Top` (Vertical Stack)

Children stack vertically from top to bottom. Use `Anchor.Bottom` on a child to add spacing below it.

```ui
Group {
    LayoutMode: Top;
    Button { Anchor: (Height: 30, Bottom: 10); } // 10px gap after this
    Button { Anchor: (Height: 30); }
}
```

### `Bottom` (Vertical Stack, Bottom-Aligned)

Children stack vertically but are aligned to the bottom edge of the parent.

### `Left` (Horizontal Stack)

Children arrange horizontally from left to right. Use `Anchor.Right` for spacing.

### `Right` (Horizontal Stack, Right-Aligned)

Children arrange horizontally but are aligned to the right edge of the parent.

### `Center`

Centers children horizontally.

### `Middle`

Centers children vertically.

### `CenterMiddle` (Horizontal Stack, Fully Centered)

Children stack horizontally and the group is centered both horizontally and vertically.

### `MiddleCenter` (Vertical Stack, Fully Centered)

Children stack vertically and the group is centered both horizontally and vertically.

### `Full`

Children use absolute positioning via their `Anchor` properties. This is the default if no `LayoutMode` is specified.

### `TopScrolling` / `BottomScrolling`

Like `Top` or `Bottom`, but adds a vertical scrollbar if content overflows. Requires `ScrollbarStyle`.

### `LeftScrolling` / `RightScrolling`

Like `Left` or `Right`, but adds a horizontal scrollbar if content overflows.

### `LeftCenterWrap` (Wrapping Horizontal Stack)

Children flow left to right, wrapping to a new row when space runs out. Each row is horizontally centered.

---

## FlexWeight

`FlexWeight` distributes remaining space among children within a `LayoutMode`.

```ui
Group {
    LayoutMode: Left;
    Anchor: (Width: 400);

    Button { Anchor: (Width: 100); }
    Group  { FlexWeight: 1; } // Takes all remaining space (200px)
    Button { Anchor: (Width: 100); }
}
```

If multiple children have `FlexWeight`, space is distributed proportionally. A total weight of 4 (`1+2+1`) with 600px of space:
-   `FlexWeight: 1` gets 150px (1/4 of 600)
-   `FlexWeight: 2` gets 300px (2/4 of 600)

---

## Visibility

An element's `Visible` property controls its display.

```ui
Button #HiddenButton {
    Visible: false;
}
```

-   `Visible: false`: The element and its children are not displayed and are **excluded from layout calculations** (they take up no space).
-   `Visible: true`: The element is displayed and participates in layout.

---

Source: https://hytalemodding.dev/en/docs/official-documentation/custom-ui/layout