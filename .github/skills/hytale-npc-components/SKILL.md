---
name: hytale-npc-components
version: 1
source: https://hytalemodding.com/official-documentation/npc/
authors:
  - name: "HytaleModding"
    url: "https://github.com/HytaleModding"
  - name: "Hypixel Studios"
    url: "https://hytale.com/"
tags: [hytale, npc, components, reusable, instruction, sensor, template]
---

# Hytale NPC Reusable Components

JSON-level reusable instruction and sensor components. Covers the Component type, parameter passing via `_ImportStates`/`_ExportStates`, and all built-in `Component_Instruction_*` and `Component_Sensor_*` entries.

> This skill covers **JSON-defined reusable components**. For Java-side custom sensor/action implementation see `hytale-npc-custom-components`.

## Triggers

- NPC component
- Component_Instruction
- Component_Sensor
- reusable component
- Intelligent_Chase
- Soft_Leash
- Standard_Detection
- Damage_Check
- Component_Instruction_State_Timeout
- Component_Instruction_Play_Animation
- Component_Instruction_Play_Animation_In_State_For_Duration
- Component_Sensor_Lost_Target_Detection
- Component structure
- _ImportStates
- _ExportStates
- ParentState
- Modify component
- Reference component
- component content
- component parameters

---

## Reusable Components

Components are reusable chunks of instruction/sensor logic defined with `"Type": "Component"`.

### Component Structure

```json
{
  "Type": "Component",
  "Class": "Instruction",
  "Parameters": {
    "_ImportStates": ["Main"],
    "Animation": {
      "Value": "",
      "Description": "The animation to play"
    },
    "Duration": {
      "Value": [3, 5],
      "Description": "The amount of time to wait before transitioning"
    }
  },
  "Content": {
    "Continue": true,
    "Instructions": [
      {
        "Reference": "Component_Instruction_State_Timeout",
        "Modify": {
          "_ExportStates": ["Main"],
          "Delay": { "Compute": "Duration" }
        }
      },
      {
        "Reference": "Component_Instruction_Play_Animation",
        "Modify": {
          "Animation": { "Compute": "Animation" }
        }
      }
    ]
  }
}
```

### Using Components (Reference + Modify)

```json
{
  "Reference": "Component_Instruction_Intelligent_Idle_Motion_Follow_Path"
}
```

With parameter overrides:

```json
{
  "Reference": "Component_Instruction_State_Timeout",
  "Modify": {
    "_ExportStates": ["Idle.Default"],
    "Delay": [30, 45]
  }
}
```

### State Import/Export Pattern

- `_ImportStates`: Declares named state slots a component expects from the caller.
- `_ExportStates`: Provides concrete state names to fill those slots when referencing the component.
- `ParentState` action: Uses the imported state name to switch states.

```json
// In component:
"_ImportStates": ["Main"],
"Actions": [
  { "Type": "ParentState", "State": "Main" }
]

// When referencing:
"Modify": {
  "_ExportStates": ["Idle.Default"]
}
```

### Common Built-In Components

| Component | Class | Purpose |
|-----------|-------|---------|
| `Component_Instruction_Intelligent_Idle_Motion_Follow_Path` | Instruction | Follow a path marker for idle guard behavior |
| `Component_Instruction_Intelligent_Chase` | Instruction | Smart chase behavior with pathfinding and lost-target handling |
| `Component_Instruction_Soft_Leash` | Instruction | Return home if NPC goes too far from spawn |
| `Component_Instruction_Damage_Check` | Instruction | React to incoming damage |
| `Component_Instruction_Play_Animation` | Instruction | Play a named animation |
| `Component_Instruction_State_Timeout` | Instruction | Wait then switch to a parent state |
| `Component_Instruction_Play_Animation_In_State_For_Duration` | Instruction | Play animation for a random duration then switch state |
| `Component_Sensor_Standard_Detection` | Sensor | Sight + hearing detection with attitude filtering |
| `Component_Sensor_Lost_Target_Detection` | Sensor | Detect a previously-seen target |

---

## Related Skills

- `hytale-npc-templates` — Core template structure, states, parameters
- `hytale-npc-sensors` — Standard_Detection and Damage_Check sensor details
- `hytale-npc-combat` — Intelligent_Chase, Soft_Leash usage in combat states
- `hytale-npc-custom-components` — Java-side custom sensor/action implementation
