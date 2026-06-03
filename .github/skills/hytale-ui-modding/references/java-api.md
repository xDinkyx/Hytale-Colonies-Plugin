# Java UI API Reference

This project uses the native Hytale UI Java API. This reference covers the core classes and patterns for building custom UIs.

> **Important**: This project uses native Hytale UI only. Do not use HyUI library.

---

## Class Overview

| Class | Package | Purpose |
|-------|---------|--------|
| `CustomUIHud` | `...player.hud` | Persistent overlay elements (always visible) |
| `HudManager` | `...player.hud` | Manages the player's HUD and built-in HUD components |
| `CustomUIPage` | `...player.pages` | Full-screen modal pages (static, display-only) |
| `BasicCustomUIPage` | `...player.pages` | Simplified `CustomUIPage` without `ref`/`store` in `build()` |
| `InteractiveCustomUIPage<T>` | `...player.pages` | Full-screen modal pages with event handling |
| `PageManager` | `...player.pages` | Opens and manages pages for a player |
| `UICommandBuilder` | `...ui.builder` | Builds UI commands (append, set, clear, remove) |
| `UIEventBuilder` | `...ui.builder` | Binds UI events to server-side handlers |
| `EventData` | `...ui.builder` | Record holding event key-value pairs for event bindings |

Full package prefix: `com.hypixel.hytale.server.core.entity.entities`

---

## CustomUIHud

`CustomUIHud` is used for persistent overlay elements (HUDs) that remain visible while the player plays.

Hytale supports **multiple** `CustomUIHud` layers per player via the keyed `HudManager` API (introduced in Update 5). Each HUD is identified by a unique string key and drawn in z-order.

### Implementation

```java
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public class MyHud extends CustomUIHud {

    public static final String KEY = "my_plugin:my_hud";

    public MyHud(@Nonnull PlayerRef playerRef) {
        super(KEY, playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Hud/MyHud.ui");
    }

    /** Called when this HUD layer is removed. Override to clean up state. */
    @Override
    protected void onRemove() {
        // optional cleanup
    }
}
```

### Showing and updating the HUD

```java
// Get the HudManager from the Player component
Player playerComponent = store.getComponent(ref, Player.getComponentType());
HudManager hudManager = playerComponent.getHudManager();

// Add a custom HUD layer (keyed; multiple HUDs can coexist)
hudManager.addCustomHud(playerRef, new MyHud(playerRef));

// Remove a custom HUD layer by key
hudManager.removeCustomHud(playerRef, MyHud.KEY);

// Get a HUD layer by key
MyHud existing = (MyHud) hudManager.getCustomHud(MyHud.KEY);

// Reset all HUD state to defaults (removes all custom HUDs + restores built-in components)
hudManager.resetHud(playerRef);
```

### Sending incremental HUD updates

```java
// Full rebuild from scratch (calls build() and sends with clear=true)
hud.show();

// Send incremental changes without full rebuild
UICommandBuilder commands = new UICommandBuilder();
commands.set("#Status.Text", "Updated text");
hud.update(false, commands); // false = do not clear before applying
```

`CustomUIHud` API:
- `show()` — calls `build()` then sends with `clear=true`. Use to (re)display the HUD.
- `update(boolean clear, UICommandBuilder commandBuilder)` — sends the commands directly.

---

## HudManager

`HudManager` controls the player's HUD — both the one allowed `CustomUIHud` and which built-in HUD components are visible. Access via `player.getHudManager()`.

### Custom HUD methods

```java
Player playerComponent = store.getComponent(ref, Player.getComponentType());
HudManager hudManager = playerComponent.getHudManager();

// Add a custom HUD layer by key
hudManager.addCustomHud(playerRef, new MyHud(playerRef));

// Remove a custom HUD layer by key
hudManager.removeCustomHud(playerRef, MyHud.KEY);

// Get a specific HUD layer by key
CustomUIHud currentHud = hudManager.getCustomHud(MyHud.KEY);

// Reset all HUD state to defaults (removes all custom HUDs + restores built-in components)
hudManager.resetHud(playerRef);

// Full UI state reset (sends ResetUserInterfaceState packet)
hudManager.resetUserInterface(playerRef);
```

### Controlling built-in HUD components

```java
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;

// Replace entire visible set
hudManager.setVisibleHudComponents(playerRef,
    HudComponent.Hotbar, HudComponent.Health, HudComponent.Chat);

// Add components without clearing others
hudManager.showHudComponents(playerRef, HudComponent.Compass);

// Remove specific components
hudManager.hideHudComponents(playerRef, HudComponent.Reticle, HudComponent.Speedometer);

// Get current visible components
Set<HudComponent> visible = hudManager.getVisibleHudComponents();
```

### HudComponent enum

All built-in HUD components:

| Value | Description |
|-------|-------------|
| `Hotbar` | Item hotbar |
| `StatusIcons` | Status effect icons |
| `Reticle` | Crosshair |
| `Chat` | Chat area |
| `Requests` | Friend/group request notifications |
| `Notifications` | General notifications |
| `KillFeed` | Kill feed messages |
| `InputBindings` | Key binding hints |
| `PlayerList` | Player list (tab) |
| `EventTitle` | Event title display |
| `Compass` | Direction compass |
| `ObjectivePanel` | Objective tracker panel |
| `PortalPanel` | Portal info panel |
| `BuilderToolsLegend` | Builder tools legend |
| `Speedometer` | Movement speed display |
| `UtilitySlotSelector` | Utility slot selector UI |
| `BlockVariantSelector` | Block variant picker |
| `BuilderToolsMaterialSlotSelector` | Builder material slot |
| `Stamina` | Stamina bar |
| `AmmoIndicator` | Ammo count display |
| `Health` | Health bar |
| `Mana` | Mana bar |
| `Oxygen` | Oxygen bar |
| `Sleep` | Sleep indicator |

Default visible set (restored by `resetHud`): Hotbar, StatusIcons, Reticle, Chat, Notifications, KillFeed, InputBindings, EventTitle, Compass, ObjectivePanel, PortalPanel, BuilderToolsLegend, Speedometer, UtilitySlotSelector, BlockVariantSelector, Stamina, AmmoIndicator, Health, Mana, Oxygen, Sleep.

---

## CustomUIPage

`CustomUIPage` is used for static full-screen modal pages (display-only, no event callbacks).

### Implementation

```java
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;

public class MyPage extends CustomUIPage {

    public MyPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commandBuilder,
                      UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append("Pages/MyPage.ui");
    }
}
```

### Opening and closing a page

```java
Player playerComponent = store.getComponent(ref, Player.getComponentType());
// Use openCustomPage, NOT setPage (setPage takes a Page enum, not a CustomUIPage)
playerComponent.getPageManager().openCustomPage(ref, store, new MyPage(playerRef));

// Close the page
playerComponent.getPageManager().setPage(ref, store, Page.None);
```

### Page lifetime options

```java
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;

CustomPageLifetime.CantClose                      // Player cannot close the page
CustomPageLifetime.CanDismiss                     // Player can close with ESC
CustomPageLifetime.CanDismissOrCloseThroughInteraction  // ESC or specific interaction closes it
```

### Sending updates

```java
// Rebuild and resend the full page content
rebuild();

// Send partial changes (does not clear existing content)
UICommandBuilder commands = new UICommandBuilder();
commands.set("#Status.Text", "Processing...");
sendUpdate(commands);

// Send with explicit clear flag
sendUpdate(commands, true);  // true = clear all existing content first
```

---

## BasicCustomUIPage

`BasicCustomUIPage` is a simpler variant of `CustomUIPage`. Override `build(UICommandBuilder)` instead of the full 4-argument form — useful when you don't need `ref` or `store` context at build time.

```java
import com.hypixel.hytale.server.core.entity.entities.player.pages.BasicCustomUIPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class MySimplePage extends BasicCustomUIPage {

    public MySimplePage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss);
    }

    @Override
    public void build(UICommandBuilder commandBuilder) {
        commandBuilder.append("Pages/MySimplePage.ui");
    }
}
```

---

## InteractiveCustomUIPage\<T\>

`InteractiveCustomUIPage<T>` handles pages with event callbacks. The type parameter `T` is a user-defined class decoded from the event data sent by the client.

### Implementation (real-world pattern from WarpListPage in server source)

```java
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.ui.builder.EventData;

public class MyPage extends InteractiveCustomUIPage<MyPage.PageEventData> {

    public MyPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageEventData.CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commandBuilder,
                      UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append("Pages/MyPage.ui");

        // Bind a button click — sends "Action": "confirm" when clicked
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating, "#ConfirmButton",
            EventData.of("Action", "confirm"), false);

        // Bind a text field value — "@" prefix reads the current UI element value
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged, "#SearchInput",
            EventData.of("@Query", "#SearchInput.Value"), false);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
                                PageEventData data) {
        if ("confirm".equals(data.action)) {
            // Handle confirm — close the page
            // Do NOT call sendUpdate when closing
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            playerComponent.getPageManager().setPage(ref, store, Page.None);
        } else if (data.query != null) {
            // Handle search — rebuild the result list and send update
            UICommandBuilder commands = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            commands.clear("#ResultList");
            // ... populate results and rebind events ...
            sendUpdate(commands, events, false);
        }
    }

    // User-defined event data class — keys must match EventData keys used in build()
    public static class PageEventData {
        public static final BuilderCodec<PageEventData> CODEC = BuilderCodec
            .builder(PageEventData.class, PageEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING),
                    (e, s) -> e.action = s, e -> e.action)
            .add()
            .append(new KeyedCodec<>("@Query", Codec.STRING),
                    (e, s) -> e.query = s, e -> e.query)
            .add()
            .build();

        String action;
        String query;
    }
}
```

### Event data key rules

| Pattern | Meaning |
|---------|---------|
| `EventData.of("Action", "confirm")` | Sends constant `"Action": "confirm"` |
| `EventData.of("@Query", "#SearchInput.Value")` | `@` prefix: reads `#SearchInput.Value` from the UI at event time |
| Key in `KeyedCodec<>("Action", ...)` | Must match the key used in `EventData.of()` exactly |

### When to call `sendUpdate`

- Call `sendUpdate(commands, events, false)` when updating content on an **open** page.
- Do **not** call `sendUpdate` when closing the page — just call `setPage(ref, store, Page.None)`.

### Closing a Page

```java
Player playerComponent = store.getComponent(ref, Player.getComponentType());
playerComponent.getPageManager().setPage(ref, store, Page.None);
```

---

## UICommandBuilder

UICommandBuilder is used to construct UI modifications.

### Methods

```java
UICommandBuilder commands = new UICommandBuilder();

// Append .ui file content
commands.append("Pages/MyPage.ui");              // Append to root
commands.append("#Container", "Pages/Item.ui"); // Append to selector

// Append inline UI content
// IMPORTANT: Text values MUST be quoted in inline .ui syntax
commands.appendInline("#List", "Label { Text: \"Item\"; }");

// Insert before element
commands.insertBefore("#Target", "Pages/Header.ui");
commands.insertBeforeInline("#Target", "Label { Text: \"Before\"; }");

// Set properties
commands.set("#Label.Text", "Hello World");
commands.set("#Label.Visible", true);
commands.set("#Slider.Value", 50);
commands.set("#Progress.Value", 0.75f);

// Set complex objects
commands.setObject("#Element.Anchor", new Anchor().setWidth(Value.of(200)));
commands.setObject("#Grid.Slots", new ItemGridSlot[]{ new ItemGridSlot(itemStack) });

// Set with value reference (reference styles from Common.ui)
commands.set("#Button.Style", Value.ref("Common.ui", "DefaultButtonStyle"));

// Remove/clear
commands.remove("#Element");     // Remove element
commands.clear("#Container");    // Clear children
commands.setNull("#Label.Text"); // Set to null
```

### Selector Syntax

Selectors target elements by their ID and optionally their properties:

| Selector | Meaning |
|----------|----------|
| `#ElementId` | Target element by ID |
| `#ElementId.Property` | Target element's property |
| `#Parent #Child` | Nested element selection |
| `#List[0]` | First child of element "List" (indexed access) |
| `#List[0] #Title` | Element "Title" within the first child of "List" |

---

## UIEventBuilder

UIEventBuilder is used to bind UI events to handler methods.

### Basic Event Binding

```java
UIEventBuilder events = new UIEventBuilder();

// Basic event binding (no data)
events.addEventBinding(CustomUIEventBindingType.Activating, "#Button");

// With data payload
events.addEventBinding(
    CustomUIEventBindingType.Activating,
    "#Button",
    EventData.of("Action", "save"),
    false  // locksInterface
);

// Value reference (gets value from UI element)
events.addEventBinding(
    CustomUIEventBindingType.ValueChanged,
    "#TextField",
    EventData.of("@Value", "#TextField.Value"),  // @ prefix = UI value reference
    false
);
```

### Event Binding Parameters

| Parameter | Description |
|-----------|-------------|
| Event type | Type of event to listen for (see events.md) |
| Selector | Element ID to attach the event to |
| Data | Event data to send when triggered |
| locksInterface | If true, locks the UI during event processing |

See [events.md](events.md) for the complete list of event types.

---

## Threading

**UI operations MUST run on the world thread.**

### In commands (use `world.execute`)

```java
World world = store.getExternalData().getWorld();
world.execute(() -> {
    Player playerComponent = store.getComponent(ref, Player.getComponentType());
    // openCustomPage, not setPage — setPage takes a Page enum
    playerComponent.getPageManager().openCustomPage(ref, store, new MyPage(playerRef));
});
```

### In `handleDataEvent`

`handleDataEvent` is already called on the world thread by the engine — no `world.execute` needed there.

### HUD updates from async contexts

```java
World world = store.getExternalData().getWorld();
world.execute(() -> {
    Player playerComponent = store.getComponent(ref, Player.getComponentType());
    playerComponent.getHudManager().addCustomHud(playerRef, new MyHud(playerRef));
});
```

---

## Value Objects Reference

### ItemGridSlot

```java
new ItemGridSlot()
    .setItemStack(new ItemStack(itemId, quantity))
    .setBackground(Value.of(patchStyle))
    .setOverlay(Value.of(overlayStyle))
    .setIcon(Value.of(iconStyle))
    .setName("Custom Name")
    .setDescription("Custom description")
    .setItemIncompatible(false)
    .setActivatable(true)
    .setItemUncraftable(false);
```

### DropdownEntryInfo

```java
new DropdownEntryInfo(LocalizableString.fromString("Option 1"), "value1")
```

### LocalizableString

```java
// Plain string
LocalizableString.fromString("Hello World")

// Localization key
LocalizableString.fromMessageId("server.ui.myKey")

// With parameters
LocalizableString.fromMessageId("server.ui.greeting", Map.of("name", playerName))
```

---

## Checklist

1. Place `.ui` files in `resources/Common/UI/Custom/`
2. Add `"IncludesAssetPack": true` to `manifest.json`
3. Image files must end with `@2x.png`
4. Run UI operations on the world thread
5. Use `openCustomPage(ref, store, page)` to open custom pages — NOT `setPage(...)` (that takes a `Page` enum)
6. Use `setPage(ref, store, Page.None)` to close pages
7. Call `sendUpdate(commands, events, false)` when updating an open interactive page
8. Do NOT call `sendUpdate` when closing — just call `setPage(Page.None)`
9. Event data keys in `EventData.of(...)` must match `KeyedCodec` keys in the `BuilderCodec`
10. `@` key prefix in `EventData` = live UI element value read at event time
