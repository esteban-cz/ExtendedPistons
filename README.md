# Extended Pistons

Extended Pistons is a NeoForge mod for Minecraft 1.21.1 that lets piston heads
follow player-authored, three-dimensional paths. Straight, sticky, and turning
transport all use the configured trajectory without changing vanilla pistons.
Moving heads and payload blocks push intersecting entities continuously, so an
upward-facing Extended Piston can lift a player standing on its payload as an
elevator.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.1 or a newer 21.1.x release
- Java 21

Place `extendedpistons-1.1.2.jar` in the instance's `mods` directory on the
server and every connecting client. The mod has no required dependencies beyond
Minecraft and NeoForge.

NeoForge 21.1.x is the loader line for Minecraft 1.21.1. Other Minecraft 1.21
patch releases use different NeoForge lines and require separately ported mod
builds; this JAR deliberately accepts Minecraft 1.21.1 only.

## Controls

Craft and hold the **Piston Path Tool**. Aim at the translucent virtual endpoint
of an Extended Piston:

- Use/right-click a face to append exactly one segment in that face's direction.
- Attack/left-click to remove exactly the final segment.

The final endpoint is cyan and turns green with a bright outline only when the
crosshair ray intersects its exact one-block cube. The currently selected face
receives a bright lime marker; that face determines the exact direction of the
next one-block addition, regardless of where the player is standing. There is
no magnetic targeting margin, and solid blocks or neighboring pistons occlude
endpoints behind them. The tool tooltip also lists its controls and editing
requirements.

The first segment is fixed to the direction in which the piston was placed. A
path may not intersect its base or itself, and its final output may not point
back into the configured route. Edits are checked again by the server and must
be within normal player interaction reach.

Configured path length has no artificial mod-enforced maximum. Natural limits
still apply: every edited destination must be loaded, inside build height and
the world border, and empty at editing time. Blocks may enter the route later
and will be handled when the piston moves.

## Configuration

Server settings are written to `config/extendedpistons-server.toml`:

- `extendedPistonPushLimit` (default `12`) controls the maximum unique ordinary
  blocks affected by one movement step.
- `ticksPerSegment` (default `4`) controls travel time per configured cell.

Both settings accept integers from 1 through 2147483647. The vanilla piston
push limit is deliberately not modified.

Version 1.0.1 uses eased per-frame movement and raises the recommended/default
segment duration to four ticks. Existing 1.0.0 server-config files retain their
saved value; change `ticksPerSegment` to `4` to use the new pacing in an existing
world.

## Compatibility and safety

Extended Pistons do not force-load chunks and reject movement through unloaded
chunks, outside build bounds, or across the world border. Blocks with block
entities are rejected. When Open Parties and Claims is installed, every affected
chunk is checked through its published protection API and motion fails closed if
that optional bridge cannot initialize.

Technical shaft, head, and recovery blocks have no item or loot form. Path data,
motion state, and active transaction data are persisted so an interrupted move
can be recovered after restart.

## Building and testing

From PowerShell:

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer
.\gradlew.bat build
```

The automated suite contains 22 focused packed-path, network, transaction,
metadata, and model-geometry tests, plus 44 dedicated-server GameTests,
including all 30 acceptance scenarios from the specification plus slime, honey,
entity, destroy-reaction, block-entity, and sticky-component coverage. It also
verifies that replacement pistons receive a fresh path and that every
extended-base shape matches vanilla. The entity tests include a connected
server player lifted by an upward-moving payload.

The release artifact is produced at
`build/libs/extendedpistons-1.1.2.jar`. A local BMC5 mod directory may be attached
to development runs without publishing its files as dependencies:

```powershell
.\gradlew.bat runClient -Pbmc5_mods_dir="C:\path\to\BMC5\mods"
```

The complete BMC5 profile includes Sinytra Connector, which requires a
production artifact and cannot be loaded as an ordinary `forgeclientdev` mod.
For full-pack testing, copy the built JAR into a disposable production-profile
clone. Version 1.0.0 was smoke-tested this way against BMC5 v51's 348 installed
JARs; the client completed resource loading with Iris, ImmediatelyFast,
Lithium, Moonlight, and Open Parties and Claims present.

## Development workflow

Changes are developed on short-lived branches and merged into `main` through
pull requests. GitHub CI runs the focused tests, dedicated-server GameTests, and
release build for every pull request. See [CONTRIBUTING.md](CONTRIBUTING.md) for
the branch naming, validation, pull-request, and release process. Planned
loader, Minecraft-version, compatibility, and polish work is described in the
[project roadmap](ROADMAP.md).

## Version baselines

The verified 1.0.0 through 1.1.1 releases are preserved under their matching
`releases/` directories and are not affected by Gradle clean/build operations.
Version 1.1.2 widens the verified NeoForge 1.21.1 runtime range without changing
world data or gameplay behavior, so all archived JARs can be compared directly.

## Known conservative limitations

- Arbitrary block entities are not transported.
- A sticky attachment component that would require conflicting movement vectors
  at a corner is rejected rather than split or moved incorrectly.
- Compatibility callbacks that cannot be reproduced safely are rejected.

Copyright (c) 2026 estyxq. All Rights Reserved.
