
# Hytale Commands

This document provides a comprehensive guide to creating custom commands in Hytale plugins. It covers various command types, argument handling, permissions, and advanced features like command variants and collections.

## Command Types

Hytale offers several base classes for creating commands, each suited for different use cases:

- **`AbstractAsyncCommand`**: A basic command that runs on its own background thread. It's not tied to any specific world, making it suitable for global commands like displaying server rules.
- **`AbstractPlayerCommand`**: Tied to a specific player and world, allowing interaction with the player and world state. It runs on the world thread, so long-running operations should be avoided.
- **`AbstractTargetPlayerCommand`**: Extends `AbstractPlayerCommand` by adding a `--player <value>` argument, allowing the command to target a specific player.
- **`AbstractTargetEntityCommand`**: Uses a raycast to target the entity the player is looking at, allowing for context-sensitive commands.

## Command Arguments

Commands can accept arguments to customize their behavior. The following argument types are available:

- **`withRequiredArg`**: A mandatory argument that must be provided.
- **`withOptionalArg`**: An optional argument specified with a `--<key> <value>` pair.
- **`withDefaultArg`**: An argument with a default value if not provided.
- **`withFlagArg`**: A boolean switch (e.g., `--debug`).

Arguments can be of various types, including `STRING`, `INTEGER`, `BOOLEAN`, `FLOAT`, `DOUBLE`, and `UUID`.

## Argument Validators

Validators can be added to arguments to ensure the input is valid before the command is executed. Common validators include `greaterThan`, `lessThan`, and `range`. Custom validators can also be created by implementing the `Validator` interface.

## Permissions

Commands can be restricted to players with specific permissions using the `requirePermission` method. This allows for fine-grained control over who can use certain commands.

## Command Variants and Aliases

- **Variants**: You can create variants of a command using `addUsageVariant`. This allows a single command to have different behaviors based on the arguments provided.
- **Aliases**: Shorthand versions of a command can be created using `addAliases`.

## Subcommands and Command Collections

Complex command structures can be created by grouping related commands into an `AbstractCommandCollection`. This allows for a hierarchical command system (e.g., `/admin user teleport`).

## Registration

Commands are registered in the plugin's `setup` method using `getCommandRegistry().registerCommand()`.

## Example: Heal Command

```java
public class HealPlayerCommand extends AbstractTargetPlayerCommand {
    private final DefaultArg<Float> healthArg;
    private final OptionalArg<String> messageArg;
    private final FlagArg debugArg;

    public HealPlayerCommand() {
        super("healplayer", "Healing a player for an <input> amount of HP (default: 100)");

        this.healthArg = this.withDefaultArg("health", "Amount to heal player", ArgTypes.FLOAT, (float)100, "Desc of Default: 100");
        this.messageArg = this.withOptionalArg("message", "Message to print while healing", ArgTypes.STRING);
        this.debugArg = this.withFlagArg("debug", "Add debug logs");
    }

    @Override
    protected void execute(@NonNullDecl CommandContext commandContext, @NullableDecl Ref<EntityStore> ref, @NonNullDecl Ref<EntityStore> ref1, @NonNullDecl PlayerRef playerRef, @NonNullDecl World world, @NonNullDecl Store<EntityStore> store) {
        if (this.debugArg.get(commandContext)) {
            commandContext.sendMessage(Message.raw("We are debugging"));
        }

        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        int healthIdx = DefaultEntityStatTypes.getHealth();
        stats.addStatValue(healthIdx, healthArg.get(commandContext));
    }
}
```
