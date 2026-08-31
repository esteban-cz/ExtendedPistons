# Changelog

## 1.1.1 - 2026-08-31

- Replace the extended piston side and bottom artwork with the revised custom
  textures.
- Remove overlapping shaft-center and connector geometry that caused stationary
  shaft textures to shimmer or appear to crawl as the camera moved.
- Remove hidden connector end caps and apply the same seamless geometry to the
  piston head's plate-side arm without changing collision or movement behavior.

## 1.1.0 - 2026-08-31

- Introduce the first complete custom visual set for Extended Pistons, including
  new 32x mechanical casing, normal face, sticky face, inner, and shaft textures.
- Replace the repeater placeholder with a custom 16x Piston Path Tool sprite.
- Apply the new textures consistently to retracted blocks, inventory models,
  extended bases, head plates, connector arms, shafts, and movement particles.
- Preserve the 1.0.9 release as the final pre-redesign build.

## 1.0.9 - 2026-08-30

- Turn the moving head's base-side connector toward the destination support at
  the midpoint of an extension through a corner.
- Keep the source connector for the first half of the cell transition, avoiding
  both an early disconnect and the late straight-shaft snap seen in 1.0.8.
- Apply the same connector state to rendered and moving collision geometry.

## 1.0.8 - 2026-08-30

- Split Extended Piston Head geometry into a textured plate, a plate-side arm,
  and an independently directed base-side arm.
- Preserve real elbow geometry while a head retracts through a corner, switching
  from the source connector to the destination connector at the cell midpoint.
- Store the preceding-path connection on every settled head so corner heads no
  longer leave a straight rod protruding beyond their shaft junction.
- Migrate heads saved by 1.0.7 to the new explicit connector direction when
  their technical block entities are recovered.
- Carry entities standing on an upward-moving top surface by exactly the same
  per-tick delta before advancing moving collision, improving elevator contact.
- Correct shallow rider embedding during that support pass without launching
  entities that are already standing at the proper height.

## 1.0.7 - 2026-08-30

- Advance moving payload collision only after intersecting entities have been
  displaced, matching vanilla piston's push-before-progress tick order.
- Track collision progress independently on the server and client instead of
  deriving it from the already-advanced world game time.
- Combine head, shaft, and payload overlaps by maximum required displacement so
  block-entity tick order cannot leave a player partially embedded.
- Extend the late-client elevator regression to verify collision remains at the
  source before the push and reaches the destination only afterward.

## 1.0.6 - 2026-08-30

- Move transaction collision shapes together with animated payloads instead of
  leaving collision behind at the source cell.
- Use vanilla-style swept overlap distances and separation epsilon when moving
  entities, preventing players from ending a lift inside the payload block.
- Replay the full elapsed collision sweep when a client receives a transaction
  late instead of skipping the missed part of the elevator movement.
- Avoid vanilla's half-block-per-tick piston movement clamp for legitimate
  delayed catch-up and one-tick segment sweeps.
- Strengthen the elevator GameTest to reject any final player/payload overlap.

## 1.0.5 - 2026-08-30

- Require the crosshair ray to intersect the exact one-block endpoint cube;
  remove the magnetic targeting margin.
- Prevent endpoints hidden behind solid blocks or neighboring pistons from
  being highlighted or edited.
- Render ghost geometry as independent quads so previews from nearby pistons
  cannot form large connecting triangles under shader renderers.
- Add the display names `Extended Piston Shaft` and `Extended Piston Head` for
  block-information overlays.

## 1.0.4 - 2026-08-30

- Invalidate and delete the client path cache when its piston base is destroyed.
- Cancel pending path fragments for destroyed bases so replacement pistons start
  with their own authoritative default path.
- Match vanilla's directional extended-base shape and light-occlusion behavior,
  preventing neighboring floor faces from being incorrectly hidden.
- Push the local client player alongside server-side entity movement so piston
  elevators remain synchronized in actual multiplayer and modpack clients.

## 1.0.3 - 2026-08-30

- Extend the first shaft four pixels into the recessed piston base so there is
  no visible gap in any placement direction.
- Move intersecting entities incrementally with the animated head and payload
  instead of applying one late one-block shove.
- Support upward-moving blocks as reliable player and entity elevators.
- Keep entity displacement synchronized with the same easing used by rendering.

## 1.0.2 - 2026-08-30

- Keep the elbow/support shaft visible while the head passes through a corner.
- Keep the destination shaft visible beneath a retracting head for continuous geometry.
- Align the moving head and its built-in rod with every reverse-step trajectory immediately.
- Use vanilla's short/full moving-head models to reduce rod overlap during extension.
- Switch Path Tool controls: right-click adds and left-click removes.
- Resolve additions from the clicked face of the highlighted endpoint, one block per click.
- Add a bright face marker showing the direction selected for the next segment.

## 1.0.1 - 2026-08-30

- Ease movement at the beginning and end of every path cell.
- Change the default movement duration from two to four ticks per segment.
- Add a magnetic margin around the virtual endpoint for easier editing.
- Color the final endpoint cyan and highlight the active target in green.
- Add Path Tool control and editing-requirement tooltips.
- Show action-bar guidance when a tool click misses the editable endpoint.

## 1.0.0

- Initial core release with editable three-dimensional paths, curved movement,
  sticky return, transaction recovery, multiplayer synchronization, and BMC5
  compatibility safeguards.
