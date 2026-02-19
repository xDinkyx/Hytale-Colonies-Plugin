
# Animated Block Textures

This guide explains how to attach a `.blockyanim` animation file to a custom block, making it visually dynamic. This is useful for effects like fire, glowing ores, or other animated environmental features.

> **Source:** <https://hytalemodding.dev/en/docs/guides/plugin/animated-block-textures>

---

## Overview

Animated blocks are defined similarly to static blocks but include additional properties in their JSON definition to specify the model, animation file, and texture. Instead of `DrawType: "Cube"`, animated blocks typically use `DrawType: "Model"` and provide custom assets.

---

## Key JSON Properties

To create an animated block, you will use these properties within the `BlockType` object of your item's JSON file:

| Property | Description | Example |
|---|---|---|
| `DrawType` | Must be set to `"Model"` to use a custom block model. | `"Model"` |
| `CustomModel` | Path to the `.blockymodel` file for the block. | `"VFX/Blue_Fire/Blue_Fire.blockymodel"` |
| `CustomModelAnimation` | Path to the `.blockyanim` animation file. | `"Blocks/Animations/Blue_Fire/Blue_Fire_Burn.blockyanim"` |
| `CustomModelTexture` | An array defining the texture(s) for the model. | `[{ "Texture": "VFX/Blue_Fire/Blue_Fire.png", "Weight": 1 }]` |
| `Looping` | A boolean that determines if the animation should loop. | `true` |
| `RequiresAlphaBlending` | A boolean for textures that require alpha blending. | `false` |

---

## Example: Animated Blue Fire Block

This example shows the minimal JSON required to create an animated blue fire block by referencing a custom model and its animation.

### JSON Definition (`Deco_Blue_Fire.json`)

```json
{
  "BlockType": {
    "DrawType": "Model",
    "CustomModel": "VFX/Blue_Fire/Blue_Fire.blockymodel",
    "CustomModelAnimation": "Blocks/Animations/Blue_Fire/Blue_Fire_Burn.blockyanim",
    "CustomModelTexture": [
      {
        "Texture": "VFX/Blue_Fire/Blue_Fire.png",
        "Weight": 1
      }
    ],
    "Looping": true,
    "RequiresAlphaBlending": false
  },
  "PlayerAnimationsId": "Block"
}
```

### Asset Folder Structure

The corresponding assets must be placed in the `Common` folder of your plugin's resources:

```
src/main/resources/
└── Common/
    ├── Blocks/
    │   └── Animations/
    │       └── Blue_Fire/
    │           └── Blue_Fire_Burn.blockyanim
    └── VFX/
        └── Blue_Fire/
            ├── Blue_Fire.blockymodel
            └── Blue_Fire.png
```

This setup ensures that the game can locate and apply the model, texture, and animation correctly to render the animated block in the world.
