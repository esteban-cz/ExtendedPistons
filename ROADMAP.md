# Extended Pistons roadmap

This roadmap describes the intended direction of Extended Pistons after the
stable NeoForge 1.21.1 foundation established by version 1.1.1. It is a guide,
not a promise of dates or exact release numbers. Priorities may change after
testing, Minecraft updates, or compatibility discoveries.

## Current foundation

- NeoForge support for Minecraft 1.21.1.
- Editable three-dimensional piston paths with straight and curved movement.
- Normal and sticky piston behavior, restart recovery, multiplayer path sync,
  entity lifting, custom textures, configuration, and automated GameTests.
- Conservative handling of unloaded chunks, protected claims, block entities,
  and incompatible movement callbacks.

## Now: Fabric support on Minecraft 1.21.1

The next major goal is a multiloader project that produces separate NeoForge
and Fabric JARs for the same Minecraft version.

- Split the project into shared `common`, `neoforge`, and `fabric` modules.
- Preserve the existing NeoForge behavior and public IDs while extracting
  loader-independent path, movement, transaction, and persistence logic.
- Implement Fabric registration, networking, configuration, events, rendering,
  and GameTest integration using Fabric API.
- Keep the Fabric and NeoForge player experience, recipes, translations,
  configuration keys, and safety rules equivalent where their APIs allow it.
- Build and test both loader artifacts in continuous integration.
- Document loader-specific dependencies, filenames, compatibility, and known
  limitations before publishing the first Fabric release.

The two loaders will use separate JARs. A single universal mod JAR is not a
goal.

## Next: multiloader stabilization

- Test dedicated servers, multiplayer clients, path editing, rendering, rapid
  power reversals, transaction recovery, sticky movement, and elevators on
  both loaders.
- Expand compatibility testing beyond the original Better Minecraft NeoForge
  environment to representative Fabric optimization and rendering mods.
- Provide safe loader-specific adapters for protection and sticky-block APIs
  when dependable public APIs are available.
- Keep unsupported integration fail-closed rather than moving blocks or
  crossing claims incorrectly.
- Improve diagnostics so rejected movement clearly identifies the relevant
  safety or compatibility condition.

## Later: additional Minecraft versions

After Fabric and NeoForge reach feature parity on 1.21.1, port one later 1.21.x
release to measure the real maintenance cost before promising a wider matrix.

- Maintain separate version branches where Minecraft APIs differ materially.
- Share fixes between supported versions when doing so is safe.
- Add further Minecraft versions according to player demand, loader
  availability, and sustainable testing capacity.
- Treat Minecraft 26.x as a separate migration because its unobfuscated code,
  build tooling, and Java requirements form a larger compatibility boundary.
- Publish a clear support table showing loader, Minecraft version, Java
  version, artifact name, and maintenance status.

Supporting every historical Minecraft release is not a goal. A smaller set of
well-tested builds is preferred over many unreliable ports.

## Ongoing polish and compatibility

- Continue improving vanilla-like movement, entity interaction, sound,
  particles, animation, and visual consistency.
- Profile very long paths and keep path processing, rendering, and networking
  iterative and bounded per tick or transfer budget.
- Add regression tests for every confirmed bug and retain recovery tests for
  interruption, chunk unload, restart, and technical-part cleanup.
- Evaluate compatible block-entity transport only if it can preserve data and
  callbacks safely across every supported loader and Minecraft version.
- Maintain installation, controls, configuration, compatibility, limitations,
  and contributor documentation alongside each release.

## Permanent design principles

- Never modify the vanilla piston push limit globally.
- Never force-load or generate chunks for piston movement.
- Never accept a client-supplied authoritative path or movement result.
- Preserve worlds and payload blocks through recoverable transactions.
- Prefer a refused move with useful feedback over duplication, deletion, claim
  bypass, or silent corruption.
- Keep registry IDs and documented configuration keys stable whenever possible.

Concrete implementation tasks are deliberately kept out of this document. The
maintainer uses an ignored `TODO.local.md` for short-lived notes, while accepted
work is tracked publicly through GitHub issues and pull requests.
