# Extended Pistons

Extended Pistons is a NeoForge mod for Minecraft 1.21.1 that adds pistons capable
of following player-authored, three-dimensional paths.

Unlike vanilla pistons, an Extended Piston is not limited to moving in a straight
line. Its head can travel horizontally, vertically, and around corners while
carrying blocks along the configured trajectory.

Both normal and sticky variants are available. Moving heads and payload blocks
continuously push intersecting entities, allowing mechanisms such as vertical
player elevators, moving platforms, directional block transport, and compact
redstone contraptions without modifying vanilla piston behavior.

## Requirements

* Minecraft 1.21.1
* NeoForge 21.1.1 or a newer 21.1.x release
* Java 21

Place `extendedpistons-1.1.2.jar` in the instance's `mods` directory on both the
server and every connecting client.

The mod has no required dependencies beyond Minecraft and NeoForge.

NeoForge 21.1.x is the loader line for Minecraft 1.21.1. Other Minecraft 1.21
patch releases use different NeoForge lines and require separately ported mod
builds. This JAR deliberately accepts Minecraft 1.21.1 only.

## Crafting

Extended Pistons adds three craftable items:

* Extended Piston
* Extended Sticky Piston
* Piston Path Tool

### Extended Piston

The Extended Piston uses a shapeless crafting recipe.

Ingredients:

* 1× Piston
* 1× Iron Ingot
* 1× Redstone Dust

![Extended Piston recipe](https://raw.githubusercontent.com/esteban-cz/ExtendedPistons/refs/heads/main/images/recipe-extended-piston.png)

Because the recipe is shapeless, the ingredients may be placed anywhere in the
crafting grid.

### Extended Sticky Piston

The Extended Sticky Piston also uses a shapeless recipe.

Ingredients:

* 1× Extended Piston
* 1× Slime Ball

![Extended Sticky Piston recipe](https://raw.githubusercontent.com/esteban-cz/ExtendedPistons/refs/heads/main/images/recipe-extended-sticky-piston.png)

### Piston Path Tool

The Piston Path Tool is used to create and edit Extended Piston paths.

Ingredients:

* 1× Iron Ingot
* 1× Redstone Dust
* 1× Stick

Crafting layout:

![Piston Path Tool recipe](https://raw.githubusercontent.com/esteban-cz/ExtendedPistons/refs/heads/main/images/recipe-piston-path-tool.png)

The recipe is shaped, so the relative ingredient positions must match the
recipe.

## Creating and editing paths

Place an **Extended Piston** or **Extended Sticky Piston** normally.

Each Extended Piston has a virtual endpoint representing the end of its
configured path.

Craft and hold the **Piston Path Tool**, then aim at the translucent virtual
endpoint:

* **Use / right-click** a face to append exactly one segment in that face's
  direction.
* **Attack / left-click** the endpoint to remove exactly the final segment.

The final endpoint is cyan and turns green with a bright outline only when the
crosshair ray intersects its exact one-block cube.

The currently selected face receives a bright lime marker. That face determines
the exact direction of the next one-block addition regardless of where the
player is standing.

There is no magnetic targeting margin. Solid blocks and neighboring pistons can
occlude endpoints behind them.

The Piston Path Tool tooltip also lists its controls and editing requirements.

### Path rules

The first segment is fixed to the direction in which the piston was placed.

A path may not:

* Intersect the piston base
* Intersect itself
* End with an output direction pointing back into the configured route

All edits are validated again by the server and must be within normal player
interaction reach.

Configured path length has no artificial mod-enforced maximum.

Natural world limits still apply. Every newly edited destination must be:

* In a loaded chunk
* Inside world build height
* Inside the world border
* Empty at editing time

Blocks may enter the configured route later and will be handled when the piston
moves.

## Movement behavior

When powered, an Extended Piston follows its complete configured trajectory.

Straight movement, vertical movement, sticky movement, and turns all use the
same path system.

Payload blocks travel with the piston head, including through corners.

Moving heads and transported blocks continuously push intersecting entities
during movement. For example, an upward-facing Extended Piston can transport a
block vertically while lifting a player standing on that block.

Vanilla pistons are not modified.

## Configuration

Server settings are written to:

```text
config/extendedpistons-server.toml
```

Available settings:

### `extendedPistonPushLimit`

Default:

```text
12
```

Controls the maximum number of unique ordinary blocks affected by one movement
step.

This does **not** modify Minecraft's vanilla piston push limit.

### `ticksPerSegment`

Default:

```text
4
```

Controls the travel time for each configured path cell.

Both configuration values accept integers from:

```text
1
```

through:

```text
2147483647
```

Version 1.0.1 introduced eased per-frame movement and changed the recommended
and default segment duration to four ticks.

Existing 1.0.0 server configuration files retain their previously saved value.
Set:

```toml
ticksPerSegment = 4
```

to use the newer pacing in an existing world.

## Compatibility and safety

Extended Pistons use conservative movement validation to avoid unsafe or
unsupported world changes.

The mod does not force-load chunks.

Movement is rejected when it would:

* Enter an unloaded chunk
* Leave world build bounds
* Cross the world border
* Move an unsupported block entity
* Violate an available protection integration
* Require movement behavior that cannot be reproduced safely

Blocks containing block entities are currently rejected.

When **Open Parties and Claims** is installed, every affected chunk is checked
through its published protection API.

If the optional protection bridge is present but cannot initialize correctly,
motion fails closed rather than bypassing protection checks.

Technical shaft, head, and recovery blocks have no obtainable item or loot form.

Path data, movement state, and active transaction data are persisted so an
interrupted movement can be recovered after a server restart.

## Known conservative limitations

* Arbitrary block entities are not transported.
* A sticky attachment component requiring conflicting movement vectors at a
  corner is rejected instead of being split or moved incorrectly.
* Compatibility callbacks that cannot be reproduced safely are rejected.
* Extended Pistons do not force-load chunks.

## Building and testing

From PowerShell:

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer
.\gradlew.bat build
```

The automated test suite contains:

* 22 focused packed-path, network, transaction, metadata, and model-geometry
  tests
* 44 dedicated-server GameTests
* All 30 acceptance scenarios from the specification
* Slime and honey behavior coverage
* Entity movement coverage
* Destroy-reaction coverage
* Block-entity safety coverage
* Sticky-component coverage

The suite also verifies that replacement pistons receive a fresh path and that
every Extended Piston base shape matches its vanilla equivalent.

Entity tests include a connected server player being lifted by an
upward-moving payload.

The release artifact is produced at:

```text
build/libs/extendedpistons-1.1.2.jar
```

## BMC5 development testing

A local BMC5 mod directory may be attached to development runs without
publishing its files as dependencies:

```powershell
.\gradlew.bat runClient -Pbmc5_mods_dir="C:\path\to\BMC5\mods"
```

The complete BMC5 profile includes Sinytra Connector, which requires a
production artifact and cannot be loaded as an ordinary `forgeclientdev` mod.

For full-pack testing, copy the built JAR into a disposable production-profile
clone.

Version 1.0.0 was smoke-tested this way against BMC5 v51's 348 installed JARs.
The client completed resource loading with the following notable mods present:

* Iris
* ImmediatelyFast
* Lithium
* Moonlight
* Open Parties and Claims

## Development workflow

Changes are developed on short-lived branches and merged into `main` through
pull requests.

GitHub CI runs the focused test suite, dedicated-server GameTests, and release
build for every pull request.

See [CONTRIBUTING.md](CONTRIBUTING.md) for branch naming, validation,
pull-request, and release procedures.

Planned loader support, Minecraft-version ports, compatibility work, and polish
are documented in the [project roadmap](ROADMAP.md).

## Version baselines

Verified releases from 1.0.0 through 1.1.1 are preserved under their matching
`releases/` directories and are not affected by Gradle clean/build operations.

Version 1.1.2 widens the verified NeoForge 1.21.1 runtime range without changing
world data or gameplay behavior.

Archived JARs can therefore be compared directly against the current release.

---

Copyright (c) 2026 estyxq. All Rights Reserved.
