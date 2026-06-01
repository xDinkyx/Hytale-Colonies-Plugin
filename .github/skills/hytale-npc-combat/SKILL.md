---
name: hytale-npc-combat
version: 1
source: https://hytalemodding.com/official-documentation/npc/
authors:
  - name: "HytaleModding"
    url: "https://github.com/HytaleModding"
  - name: "Hypixel Studios"
    url: "https://hytale.com/"
tags: [hytale, npc, combat, attack, beacon, leash, return-home]
---

# Hytale NPC Combat

Combat AI patterns, attack interactions, inter-NPC communication via beacons, and the ReturnHome/Search state loop.

Load when building combat-capable NPCs, implementing attack chaining, configuring beacon alerts, or adding leash-based return-home behavior.

## Triggers

- NPC combat
- combat state
- Alerted state
- Chase substate
- attack
- Attack action
- attack chaining
- Root Interaction
- InteractionVars
- melee attack
- attack interaction
- beacon
- Beacon communication
- inter-NPC
- NPC group
- AttitudeGroup
- DefaultPlayerAttitude
- Intelligent_Chase
- Soft_Leash
- leash
- LeashDistance
- HardLeashDistance
- ReturnHome
- Search state
- return home
- combat parameters
- AttackDistance
- AttackPauseRange
- CombatRelativeTurnSpeed
- spawn beacon
- edible critter
- NPC spawning beacon
- TriggerSpawnBeacon
- WarnGroups

---

## Combat System

### Alerted State Pattern

Transitional state between detection and combat:

```json
{
  "Sensor": { "Type": "State", "State": "Alerted" },
  "Instructions": [
    {
      "Reference": "Component_Instruction_Play_Animation",
      "Modify": { "Animation": "Alerted" }
    },
    {
      "Continue": true,
      "Sensor": {
        "Type": "Target",
        "Range": { "Compute": "AlertedRange" },
        "Filters": [ { "Type": "LineOfSight" } ]
      },
      "HeadMotion": { "Type": "Watch" }
    },
    {
      "Sensor": { "Type": "Target", "Range": { "Compute": "AlertedRange" } },
      "ActionsBlocking": true,
      "Actions": [
        { "Type": "Timeout", "Delay": [1, 1] },
        { "Type": "State", "State": "Combat" }
      ]
    },
    {
      "Actions": [ { "Type": "State", "State": "Idle" } ]
    }
  ]
}
```

### Combat State with Chase Substate

```json
{
  "Sensor": { "Type": "State", "State": "Combat" },
  "Instructions": [
    {
      "Sensor": { "Type": "State", "State": ".Chase" },
      "Instructions": [
        {
          "Sensor": {
            "Type": "Target",
            "Range": { "Compute": "AttackDistance" },
            "Filters": [ { "Type": "LineOfSight" } ]
          },
          "Actions": [ { "Type": "State", "State": ".Default" } ]
        },
        {
          "Reference": "Component_Instruction_Soft_Leash",
          "Modify": {
            "_ExportStates": ["ReturnHome"],
            "LeashDistance": { "Compute": "LeashDistance" },
            "LeashMinPlayerDistance": { "Compute": "LeashMinPlayerDistance" },
            "LeashTimer": { "Compute": "LeashTimer" },
            "HardLeashDistance": { "Compute": "HardLeashDistance" }
          }
        },
        {
          "Reference": "Component_Instruction_Intelligent_Chase",
          "Modify": {
            "_ExportStates": ["Search", "Search", "ReturnHome"],
            "ViewRange": { "Compute": "AlertedRange * 2" },
            "HearingRange": { "Compute": "HearingRange * 2" },
            "StopDistance": 0.1,
            "RelativeSpeed": 0.5
          }
        }
      ]
    },
    {
      "$Comment": "NPC melee attack",
      "Sensor": {
        "Type": "Target",
        "Range": { "Compute": "AttackDistance" },
        "Filters": [ { "Type": "LineOfSight" } ]
      },
      "ActionsBlocking": true,
      "Actions": [
        {
          "Type": "Attack",
          "Attack": { "Compute": "Attack" },
          "AttackPauseRange": { "Compute": "AttackPauseRange" }
        },
        { "Type": "Timeout", "Delay": [0.2, 0.2] }
      ],
      "HeadMotion": {
        "Type": "Aim",
        "RelativeTurnSpeed": { "Compute": "CombatRelativeTurnSpeed" }
      }
    },
    {
      "Actions": [ { "Type": "State", "State": ".Chase" } ]
    }
  ]
}
```

### Key Combat Parameters

```json
"Attack": {
  "Value": "Root_NPC_Goblin_Ogre_Attack",
  "Description": "The attack to use."
},
"AttackDistance": {
  "Value": 2,
  "Description": "The distance at which an NPC will execute attacks"
},
"AttackPauseRange": {
  "Value": [1.5, 2],
  "Description": "Absolute minimum time before a second attack"
},
"CombatRelativeTurnSpeed": {
  "Value": 1.5,
  "Description": "Turn speed modifier in combat"
},
"LeashDistance": {
  "Value": 20,
  "Description": "Range after which NPC starts wanting to return"
},
"HardLeashDistance": {
  "Value": 60,
  "Description": "Absolute maximum from leash position"
}
```

---

## Attack Interactions

### Root Interaction (Chaining Attacks)

```json
{
  "Interactions": [
    {
      "Type": "Chaining",
      "ChainId": "Slashes",
      "ChainingAllowance": 15,
      "Next": [
        "Goblin_Ogre_Swing_Left",
        "Goblin_Ogre_Swing_Right",
        "Goblin_Ogre_Swing_Down"
      ]
    }
  ],
  "Tags": {
    "Attack": ["Melee"]
  }
}
```

NPCs attack in sequence: first `Swing_Left`, then `Swing_Right` (if within 15s), then `Swing_Down`.

### Individual Attack Interaction

```json
{
  "Type": "Simple",
  "Effects": {
    "ItemPlayerAnimationsId": "Goblin_Club",
    "ItemAnimationId": "SwingLeft"
  },
  "RunTime": 0.2,
  "Next": {
    "Type": "Selector",
    "RunTime": 0.25,
    "Selector": {
      "Id": "Horizontal",
      "Direction": "ToLeft",
      "TestLineOfSight": true,
      "ExtendTop": 0.5,
      "ExtendBottom": 2,
      "StartDistance": 0.1,
      "EndDistance": 3.5,
      "Length": 60,
      "RollOffset": 0,
      "YawStartOffset": -30
    },
    "HitEntity": {
      "Interactions": [
        {
          "Parent": "DamageEntityParent",
          "DamageCalculator": {
            "BaseDamage": { "Physical": 8 },
            "RandomPercentageModifier": 0.1
          },
          "DamageEffects": {
            "Knockback": { "Force": 0.5, "RelativeX": -5, "RelativeZ": -5, "VelocityY": 5 },
            "WorldSoundEventId": "SFX_Unarmed_Impact",
            "WorldParticles": [ { "SystemId": "Impact_Blade_01" } ]
          }
        }
      ]
    },
    "Next": {
      "Type": "Simple",
      "RunTime": 0.1
    }
  }
}
```

### InteractionVars (Template-Level Damage Override)

Templates can define overridable interaction variable slots:

```json
"InteractionVars": {
  "Melee_Damage": {
    "Interactions": [
      {
        "Parent": "NPC_Attack_Melee_Damage",
        "DamageCalculator": {
          "Type": "Absolute",
          "BaseDamage": { "Physical": 10 },
          "RandomPercentageModifier": 0.1
        }
      }
    ]
  }
}
```

Variants override these via `"Modify"`:

```json
"InteractionVars": {
  "Melee_SwingDown_Damage": {
    "Interactions": [
      {
        "Parent": "Goblin_Ogre_Swing_Down_Damage",
        "DamageCalculator": {
          "Type": "Absolute",
          "BaseDamage": { "Physical": 20 }
        }
      }
    ]
  }
}
```

---

## Inter-NPC Interaction

### Beacon Communication

NPCs communicate via named beacon messages.

**Listening for a beacon (receiver):**

```json
{
  "Sensor": {
    "Type": "Beacon",
    "Message": "Annoy_Ogre",
    "Range": 5
  },
  "Actions": [
    {
      "Type": "Attack",
      "Attack": { "Compute": "SleepingAttack" },
      "AttackPauseRange": [1, 2]
    }
  ]
}
```

**Sending a beacon (via state transition):**

```json
{
  "Type": "Beacon",
  "Message": "Goblin_Ogre_Warn",
  "TargetGroups": { "Compute": "WarnGroups" },
  "SendTargetSlot": "LockedTarget"
}
```

### NPC Groups

Define groups for filtering:

```json
{
  "IncludeRoles": ["Goblin_Scrapper"]
}
```

### Spawn Beacons (Manual NPC Spawning)

**Spawn beacon definition:**

```json
{
  "Environments": [],
  "NPCs": [
    { "Weight": 1, "Id": "Edible Rat" }
  ],
  "SpawnAfterGameTimeRange": ["PT5M", "PT10M"],
  "NPCSpawnState": "Seek",
  "TargetSlot": "LockedTarget"
}
```

**Triggering from template:**

```json
{
  "Continue": true,
  "Sensor": { "Type": "Any", "Once": true },
  "Actions": [
    {
      "Type": "TriggerSpawnBeacon",
      "BeaconSpawn": { "Compute": "FoodNPCBeacon" },
      "Range": 15
    }
  ]
}
```

### Edible Critter Template Pattern

Generic template for NPCs that seek a target and get consumed:

```json
{
  "Type": "Abstract",
  "KnockbackScale": 0.5,
  "Parameters": {
    "Appearance": { "Value": "Rat", "Description": "Model to be used" },
    "WalkSpeed": { "Value": 3, "Description": "How fast this critter moves" },
    "SeekRange": { "Value": 40, "Description": "How far it can be from eater" },
    "MaxHealth": { "Value": 100, "Description": "Max health for the NPC" }
  },
  "Appearance": { "Compute": "Appearance" },
  "StartState": "Idle",
  "MaxHealth": { "Compute": "MaxHealth" },
  "Instructions": [
    {
      "Instructions": [
        {
          "Sensor": { "Type": "State", "State": "Idle" },
          "Instructions": [
            {
              "Sensor": { "Type": "Beacon", "Message": "Approach_Target", "TargetSlot": "LockedTarget" },
              "Actions": [ { "Type": "State", "State": "Seek" } ]
            },
            {
              "ActionsBlocking": true,
              "Actions": [
                { "Type": "Timeout", "Delay": [1, 1] },
                { "Type": "Despawn" }
              ]
            }
          ]
        },
        {
          "Sensor": { "Type": "State", "State": "Seek" },
          "Instructions": [
            {
              "Sensor": { "Type": "Target", "TargetSlot": "LockedTarget", "Range": { "Compute": "SeekRange" } },
              "BodyMotion": { "Type": "Seek", "SlowDownDistance": 0.1, "StopDistance": 0.1 }
            },
            {
              "ActionsBlocking": true,
              "Actions": [
                { "Type": "Timeout", "Delay": [1, 1] },
                { "Type": "State", "State": "Idle" }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

---

## ReturnHome & Search States

### ReturnHome

Handles returning to spawn point after leash triggers. Heals to full on arrival.

```json
{
  "Sensor": { "Type": "State", "State": "ReturnHome" },
  "Instructions": [
    {
      "Sensor": {
        "Type": "And",
        "Sensors": [
          { "Type": "Damage", "Combat": true, "TargetSlot": "LockedTarget",
            "Enabled": { "Compute": "AbsoluteDetectionRange > 0" } },
          { "Type": "Target", "TargetSlot": "LockedTarget",
            "Range": { "Compute": "AbsoluteDetectionRange" } }
        ]
      },
      "Actions": [ { "Type": "State", "State": "Combat" } ]
    },
    {
      "Sensor": { "Type": "Leash", "Range": { "Compute": "LeashDistance * 0.3" } },
      "BodyMotion": {
        "Type": "Seek",
        "SlowDownDistance": { "Compute": "LeashDistance * 0.4" },
        "StopDistance": { "Compute": "LeashDistance * 0.2" },
        "RelativeSpeed": 0.8,
        "UsePathfinder": true
      }
    },
    {
      "Actions": [
        { "Type": "SetStat", "Stat": "Health", "Value": 1000000 },
        { "Type": "State", "State": "Idle" }
      ]
    }
  ]
}
```

### Search State

Wander around looking for lost target before returning to idle:

```json
{
  "Sensor": { "Type": "State", "State": "Search" },
  "Instructions": [
    {
      "Sensor": { "Type": "Damage", "Combat": true, "TargetSlot": "LockedTarget" },
      "Actions": [ { "Type": "State", "State": "Alerted" } ]
    },
    {
      "Instructions": [
        {
          "Sensor": { "Reference": "Component_Sensor_Lost_Target_Detection", "Modify": { ... } },
          "Actions": [ { "Type": "State", "State": "Combat" } ]
        },
        {
          "Sensor": { "Reference": "Component_Sensor_Standard_Detection", "Modify": { ... } },
          "Actions": [ { "Type": "State", "State": "Alerted" } ]
        },
        {
          "BodyMotion": { "Type": "Sequence", "Motions": [ /* wander pattern */ ] },
          "ActionsBlocking": true,
          "Actions": [
            { "Type": "Timeout", "Delay": [4, 5] },
            { "Type": "State", "State": "Idle" }
          ]
        }
      ]
    }
  ]
}
```

---

## Related Skills

- `hytale-npc-templates` — Core template structure, states, parameters, instruction flags
- `hytale-npc-sensors` — All sensor types, entity filters, Standard_Detection component
- `hytale-npc-actions` — All action types, motions, timers
- `hytale-npc-components` — Reusable JSON components (Intelligent_Chase, Soft_Leash)
- `hytale-entity-effects` — Status effects and buffs for combat
