# Extended Pistons 1.1.3 baseline

This directory preserves the redstone retraction and moving-part name fixes
for Minecraft 1.21.1. The original 1.1.2 release remains unchanged.

- Artifact: `extendedpistons-1.1.3.jar`
- SHA-256: `C55BE48A7E67DEE73BCE64FF101273C255E6BC4C467720E74026206D3B0D0DB5`
- Minecraft metadata range: exactly 1.21.1
- NeoForge metadata range: 21.1.1 or newer 21.1.x
- Focused tests: 22 passed on NeoForge 21.1.234
- GameTests: 50 passed on NeoForge 21.1.234
- Embedded release metadata: inspected and verified as 1.1.3

Upward-facing normal and sticky pistons now fully retract when moving a
redstone block. Moving parts display "Moving Extended Piston" instead of a
translation key.

The JAR is intentionally ignored by Git and remains in this directory as a
local archive. Publish it as the `v1.1.3` GitHub and CurseForge release asset.
