I want you to build a complete Minecraft mod called **Extended Pistons** by estyxq.

Before writing code, read this entire specification carefully, inspect the existing workspace/repository, inspect the actual Minecraft/NeoForge 1.21.1 APIs and mappings available to the project, and then produce a short implementation plan.

After the plan, implement the mod incrementally and compile/test after each major phase.

Do not invent Minecraft/NeoForge APIs. Verify actual class names, method signatures, networking APIs, rendering APIs, config APIs, etc. against NeoForge 1.21.1 / Minecraft 1.21.1.

# TARGET ENVIRONMENT

Minecraft:
- Minecraft Java Edition 1.21.1

Mod loader:
- NeoForge ONLY
- Target NeoForge version appropriate for Minecraft 1.21.1
- Java 21

Target modpack:
- Better MC [NEOFORGE] BMC5 v51
- This is a very large ~344-mod modpack.
- Compatibility is extremely important.

The BMC5 v51 instance includes performance/core mods such as:
- Lithium
- C2ME NeoForge
- FerriteCore
- ImmediatelyFast
- Iris
- Forgified Fabric API

And gameplay/content mods including things such as:
- Supplementaries
- Carry On
- Tom's Simple Storage
- Moonlight
- JEI
- many worldgen/content mods

The mod must avoid invasive behavior that could conflict with this modpack.

# DEPENDENCY REQUIREMENTS

I want this mod to be as standalone as reasonably possible.

Runtime dependencies:
- Minecraft
- NeoForge

Prefer ZERO additional runtime dependencies.

Maximum acceptable external dependencies would be 1-3 small API/library dependencies only if there is a very strong technical reason.

DO NOT depend on large gameplay/content mods such as:
- Create
- MineColonies
- Structurize
- Domum Ornamentum
- etc.

Do not introduce an entire content mod just to implement the piston mechanic.

Avoid mixins/coremods/vanilla method replacement unless absolutely necessary.

Prefer:
- custom blocks
- custom block entities
- NeoForge events
- NeoForge networking
- NeoForge configuration
- custom client rendering

over modifying vanilla piston internals.

If a mixin is genuinely required, keep it extremely small, isolated, documented and compatibility-conscious.

# MAIN IDEA

This mod adds TWO player-obtainable piston blocks:

1. Extended Piston
2. Extended Sticky Piston

They should look and behave similarly to the vanilla:
- Piston
- Sticky Piston

We can initially reuse/reference vanilla piston textures.

Do NOT copy Minecraft texture files into the project if referencing the existing `minecraft:` texture resources is sufficient.

I may make custom textures later.

The major difference is that these pistons have a configurable **3D extension path**.

This is NOT simply a piston with a larger straight extension distance.

The piston head must be capable of following an arbitrary orthogonal path through the world, including 90-degree turns.

Example:

P = piston
x = configured shaft/head path

Top/side conceptual example:

P -> x -> x
           |
           x
           |
           x

The piston can therefore go:
- 2 blocks forward
- then 2 blocks upward

Another path could be:

forward
forward
up
up
left
left
down
forward
etc.

The path is made exclusively from the 6 cardinal Minecraft directions:

- NORTH
- SOUTH
- EAST
- WEST
- UP
- DOWN

No diagonal segments.

# PISTON PLACEMENT

The Extended Piston and Extended Sticky Piston should place and orient like vanilla pistons.

The base occupies exactly one normal world block.

On placement, every Extended Piston automatically gets a default path consisting of exactly ONE segment in the piston's facing direction.

Therefore:

P -> head

is the minimum configuration.

The path may NEVER contain zero segments.

The first path segment always begins in the direction the piston was facing when placed.

# PATH REPRESENTATION

Internally, represent the configured path efficiently.

A good representation would probably be a list/array of directions:

FORWARD,
FORWARD,
UP,
LEFT,
LEFT,
...

or another compact equivalent.

The coordinates should preferably be relative to the piston base rather than storing huge numbers of absolute BlockPos values.

Each consecutive path cell must be Manhattan-adjacent to the previous one.

The path should NOT be allowed to intersect:
- the piston base
- itself

because overlapping shaft segments would produce undefined behavior.

Do not impose an arbitrary numeric maximum on path length.

I explicitly want:

**NO MOD-ENFORCED SHAFT LENGTH LIMIT.**

Obviously there are natural technical/world limitations such as:
- unloaded chunks
- world build limits
- world border
- serialization/network constraints
- available memory
- etc.

But do NOT add something arbitrary such as:

MAX_PATH_LENGTH = 64

or

MAX_PATH_LENGTH = 256.

Use iterative algorithms rather than recursion so very long paths do not cause stack overflows.

Keep serialization/networking reasonably compact.

# CONFIGURATION TOOL

Add one item:

**Piston Path Tool**

This tool configures the path of Extended Pistons.

When the player holds this tool, an Extended Piston should show a translucent/gray "ghost" preview of its configured extension path.

When the player is NOT holding the tool, a retracted Extended Piston should look approximately like a normal retracted piston.

The ghost preview is VISUAL ONLY.

Ghost shaft/head segments:
- do not occupy real blocks
- have no collision
- cannot suffocate entities
- should not affect redstone
- should not exist as server world blocks

Use client rendering for the preview.

Use the normal piston appearance/textures initially, but render the preview:
- translucent
- gray/desaturated if convenient
- obviously non-physical

The final ghost head should face the direction of the LAST path segment.

Therefore a path:

FORWARD
FORWARD
UP

should end with a piston head facing UP.

# PATH TOOL CONTROLS

Desired interaction:

LEFT CLICK:
- add one path segment

RIGHT CLICK:
- remove the last path segment

Minimum path length is always 1, so right clicking when the path contains only one segment should do nothing.

For adding a segment:

The player should be able to choose any of the six cardinal directions from the CURRENT ENDPOINT of the path.

The intended UX is:

- while holding the Piston Path Tool
- the ghost endpoint/head is visible
- clicking/aiming at a face/direction of the ghost endpoint lets the player append a segment in that direction

For example:

Current:

P -> x -> HEAD

Player chooses the TOP face of the ghost head.

Result:

P -> x -> x
           |
          HEAD

A segment can only be added if its destination is valid at CONFIGURATION TIME.

Do not allow adding into:
- an occupied solid world block
- the piston base
- an existing segment of the same path
- outside build height/world border
- an unloaded chunk

The important distinction is:

A block may be placed into the configured piston path AFTER configuration.

That is allowed.

The piston must then attempt to push that block when activated.

The empty-space requirement applies only when editing/appending the path.

If interacting directly with virtual ghost geometry requires custom client raycasting, implement that properly.

Do not make path editing awkward purely because the preview isn't a real block.

The server must always validate any path-edit packet sent by the client.

Never trust client-provided coordinates blindly.

Editing should only be possible when the piston is:
- fully retracted
- not currently moving

Prefer preventing edits while powered.

# REDSTONE

Extended Pistons should activate using normal Minecraft redstone.

Expected behavior should be as close as practical to vanilla pistons:

unpowered:
- retracted

powered:
- extend through configured path

power removed:
- retract through configured path in reverse

Handle:
- direct power
- neighboring redstone updates
- levers
- buttons
- repeaters
- observers
- redstone torches
- rapid pulses

Do not duplicate items or corrupt piston state if power changes while the piston is moving.

Implement an explicit server-side movement/state machine.

For example states may conceptually include:

RETRACTED
EXTENDING
EXTENDED
RETRACTING

If the desired powered state changes while motion is in progress, handle it deterministically.

Never allow two movement operations to operate on the same piston simultaneously.

# PHYSICAL EXTENSION

When powered, the piston head physically follows the configured path.

For example path:

FORWARD
FORWARD
UP

means the head actually travels:

step 1: forward
step 2: forward
step 3: up

When fully extended, the head is located at the final path position and points in the final path direction.

The shaft visually occupies the route behind it while extended.

At corners, render/use proper connected shaft geometry so the path makes visual sense.

Only the following should be normal player-obtainable blocks:

- Extended Piston
- Extended Sticky Piston

It is acceptable to register INTERNAL technical blocks/entities for:
- moving piston head
- shaft
- movement state

if that makes implementation significantly safer.

If you do this:
- they must not appear in creative inventory
- they should not be obtainable normally
- they should clean themselves up safely
- breaking/chunk unloading must not duplicate them

Prefer the smallest and safest architecture.

# MOST IMPORTANT MECHANIC: BLOCK TRANSPORT

The piston must push blocks ALONG THE CONFIGURED PATH.

This includes paths with corners.

This is extremely important.

Do NOT interpret a corner as simply changing which direction the piston checks for new blocks.

A block already being pushed must follow the piston around the corner.

Consider this configured path:

Base position:

P

Path trajectory:

A -> B -> C -> D
               |
               E
               |
               F

If a block starts at A, activation should progressively move it:

A
then B
then C
then D
then E
then F
then finally one block beyond F in the final path direction.

In other words, the pushed block follows the same trajectory as the piston head, staying ahead of it.

This allows blocks to be moved around corners.

# TRAJECTORY MODEL

A useful conceptual model is:

T = configured path + a straight infinite/final tail continuing in the direction of the final path segment.

Example:

Configured path:

A -> B -> C
          |
          D
          |
          E

Final direction = DOWN

Conceptual movement trajectory becomes:

A
B
C
D
E
then positions continuing DOWN beyond E.

When the piston head advances one trajectory index, blocks occupying the next trajectory cells are shifted one position forward along that trajectory.

This is only a conceptual model; choose whatever implementation is safest.

The important observable behavior is that blocks can travel around corners.

# BLOCK ENCOUNTERED LATER IN THE PATH

The piston does not only interact with a block immediately beside its base.

Example:

P -> empty -> BLOCK -> empty -> endpoint

When activated:

1. piston head travels through the first empty path cell
2. reaches the block later in its route
3. begins pushing that block
4. transports it through the remaining route
5. leaves it beyond the final piston head

So the piston effectively "encounters" blocks along the configured route.

This was specifically one of the intended use cases.

# NORMAL EXTENDED PISTON

The non-sticky Extended Piston:

- extends through the route
- pushes encountered blocks
- leaves the pushed block(s) at their destination
- retracts its head/shaft through the route
- DOES NOT bring the pushed block back

Equivalent philosophy to vanilla normal pistons.

# EXTENDED STICKY PISTON

The Extended Sticky Piston:

Extension:
- same pushing behavior as Extended Piston

Retraction:
- the block directly attached/in front of the final extended sticky piston head should be pulled back
- it must travel BACKWARD through the configured route
- it should end beside the piston base, equivalent to the starting position of a normal sticky piston payload

Example path:

P -> A -> B
          |
          C

Block after full extension:

P -> shaft -> shaft
              |
             HEAD -> BLOCK

After power is removed:

BLOCK must travel:

C
B
A

back toward the base as the piston retracts.

The block must therefore follow the reverse path around corners.

Try to mimic vanilla sticky piston semantics:

- pushing can involve multiple blocks up to the push limit
- sticky retraction should primarily pull the block directly attached to/in front of the final piston head
- do not automatically suck an arbitrary entire line of blocks backward unless vanilla piston behavior would imply it

If slime/honey interactions make this significantly more complex, implement them carefully and test them separately.

Do NOT introduce item duplication in edge cases.

# PUSH LIMIT

Minecraft normally has a piston push limit of 12 blocks.

I want a SERVER-WIDE config setting for Extended Pistons.

Example:

extendedPistonPushLimit = 12

This applies globally to all Extended Pistons.

It should be possible for server admins to change it.

Use NeoForge's server configuration system.

Prefer a config such as:

config/extendedpistons-server.toml

or the appropriate NeoForge SERVER config location.

The push limit and PATH LENGTH are completely separate concepts.

Example:

path length = 500
push limit = 12

is valid.

The piston can travel 500 empty path cells but may push at most 12 blocks.

I would ALSO like an OPTIONAL configuration for changing the vanilla piston push limit globally.

Something conceptually like:

overrideVanillaPistonPushLimit = false
vanillaPistonPushLimit = 12

However:

COMPATIBILITY IS MORE IMPORTANT THAN THIS OPTIONAL FEATURE.

If changing the vanilla piston limit requires invasive replacement of vanilla piston logic or a risky mixin likely to conflict with Lithium/BMC5, then:

1. implement Extended Piston push-limit config first
2. isolate the vanilla override
3. disable it by default
4. clearly document the compatibility risk

Do NOT compromise the core mod just for the vanilla override.

# PUSHABILITY / MOD COMPATIBILITY

Use Minecraft/NeoForge piston movement rules wherever possible.

Respect things such as:
- unpushable blocks
- PushReaction / piston reaction
- world border
- build height
- blocks that should be destroyed by pistons
- blocks that should block movement

Do not assume every modded block can be moved.

For modded block entities:

DO NOT blindly serialize/remove/recreate arbitrary block entities.

That can corrupt:
- storage blocks
- inventories
- machines
- modded state
- network nodes
- capability/data attachments

Use vanilla/NeoForge movement rules.

If a block/block entity cannot safely be piston-moved according to the API, treat it as immovable.

Compatibility and no-duping are more important than forcing every block to move.

# SLIME / HONEY

Where realistically possible, mimic vanilla piston behavior involving:
- slime blocks
- honey blocks
- attached blocks

But arbitrary 3D curved movement makes this significantly more complex than vanilla straight pistons.

Therefore implement this in stages.

First priority:
- ordinary block chains
- correct path movement
- correct corner movement
- no duplication
- no corruption

Then add/test slime/honey attached structures.

Never fake support if it risks world corruption.

Document any unavoidable behavioral difference from vanilla.

# UNLOADED CHUNKS

DO NOT silently force-load arbitrary chunks because somebody configured a 10,000-block piston.

The mod should respect chunk loading.

Configuration:
- cannot append a segment into an unloaded chunk

Activation:
- before moving into another chunk, verify that the required chunk is loaded
- do not call APIs that force-load arbitrary chunks just to complete piston movement

If the required path or required push destination enters an unloaded chunk:

the piston should fail/pause safely without:
- deleting blocks
- duplicating blocks
- leaving corrupt moving-piston state

Prefer preflight validation before beginning a movement where possible.

Never call something equivalent to:

getChunk(...)

if that call causes an unloaded chunk to synchronously generate/load.

Use APIs that verify loaded state without forcing chunk loading.

# CHUNK UNLOAD DURING MOVEMENT

Design for the possibility that a chunk becomes unloaded while movement exists.

Do not rely on "this probably won't happen."

Movement state must be recoverable.

On server/world restart:
- no block duplication
- no missing block payload
- piston state should recover consistently

Persistence must be server-authoritative.

# ENTITIES

The moving piston head should interact reasonably with entities.

At minimum:
- no crashes
- no teleporting entities to invalid coordinates
- no suffocation caused by ghost preview
- physical moving head should push entities approximately like a piston if practical

Entity handling is lower priority than correct world block movement, but design the architecture so it can be implemented safely.

# RENDERING

Client-side rendering requirements:

Retracted, tool NOT held:
- approximately normal piston appearance

Retracted, Piston Path Tool held:
- render translucent ghost shaft
- render ghost turns
- render translucent final piston head
- final head orientation = last path direction

Moving:
- animate the real piston head along the configured route if practical
- turns should visually make sense
- show shaft behind head

Extended:
- real shaft/head visible along the path

Do not perform important gameplay logic client-side.

Client only renders state received from server.

Avoid excessive rendering cost for extremely long paths.

Use:
- frustum culling
- distance checks
- efficient iteration

where appropriate.

Do not impose an artificial path length limit simply to solve rendering.

It is acceptable to only render path portions inside the client's current render distance.

# NETWORKING

All authoritative data lives server-side.

Use NeoForge 1.21.1 networking.

Packets may be needed for:
- editing path
- synchronizing path
- movement state
- tool interaction

Server must validate:
- player distance
- correct held item
- target piston exists
- piston is editable
- requested direction
- target position
- loaded chunk
- world border/build limits
- self-intersection
- occupied configuration destination

Never trust the client.

Because paths are theoretically unbounded, do not design a protocol where a malicious client can send a gigantic arbitrary path list in one packet.

Prefer edit operations such as:

ADD_SEGMENT(direction)

REMOVE_LAST_SEGMENT

and let the server modify the canonical path.

For client synchronization of large paths, use a compact representation and avoid unsafe packet sizes.

# DATA STORAGE

Each Extended Piston will probably need a BlockEntity or equivalent persistent data.

Persist at least:
- configured route
- current movement state
- movement progress if required for recovery
- sticky/non-sticky if not intrinsic to block type

Use compact serialization.

A direction only requires a few bits, so don't store thousands of full compound NBT BlockPos entries if unnecessary.

Do not use recursive path processing.

# BREAKING THE PISTON

Handle safely:

- breaking while retracted
- breaking while extended
- breaking while moving
- piston base destroyed by explosion
- shaft/head technical state destroyed
- server shutdown during movement

No duplication.

No orphaned technical shaft/head blocks.

The safest acceptable behavior during ambiguous destruction is preferable to trying to preserve an impossible animation.

# MULTIPLAYER

Must work correctly on a dedicated NeoForge server.

Do not design this as a client-only mod.

Test with:
- one player
- multiple players observing same piston
- player joining while piston is extended
- player joining while piston is moving
- two players trying to edit the same piston

Server is authoritative.

# CREATIVE TAB / RECIPES

Add:
- Extended Piston
- Extended Sticky Piston
- Piston Path Tool

Use sensible temporary recipes.

For example, recipes may initially be:

Extended Piston:
vanilla piston + something simple

Extended Sticky Piston:
Extended Piston + slimeball

Piston Path Tool:
simple cheap recipe

Exact recipes are not important yet.

Put them somewhere reasonable in creative inventory.

# MODEL/TEXTURE PLACEHOLDERS

For now reuse vanilla piston texture references as much as possible.

Extended Piston:
- looks like piston

Extended Sticky Piston:
- looks like sticky piston

Path tool:
- basic placeholder model is fine

Ghost shaft:
- use piston-side/stem-like vanilla texture references

Ghost head:
- vanilla piston head texture
- sticky variant for sticky piston

Do not spend excessive time on polished art.

FUNCTIONALITY FIRST.

# PERFORMANCE REQUIREMENTS

Remember this runs inside BMC5 v51.

Avoid:
- scanning the entire world every tick
- global tick handlers iterating every piston
- recursive path algorithms
- constantly synchronizing entire paths
- forcing chunks to load
- excessive object allocation every tick
- expensive rendering of off-screen route segments

Prefer each active piston managing its own state.

A completely idle Extended Piston should have negligible tick cost.

If block entities do not need to tick while idle, don't tick them while idle.

# COMPATIBILITY REQUIREMENTS

This mod needs to coexist with a heavily modded NeoForge environment.

Especially avoid interfering with:
- vanilla piston classes globally
- chunk scheduling
- server threading
- C2ME
- Lithium
- rendering internals modified by ImmediatelyFast/Iris
- arbitrary BlockEntity serialization
- Carry On
- storage/network mods

Do not access Minecraft world state asynchronously unless the NeoForge/Minecraft API explicitly permits it.

C2ME being installed does NOT mean our mod should start doing world mutations from arbitrary worker threads.

World mutations should remain on the appropriate server thread.

# SAFETY / DUPLICATION

Treat item/block duplication as a critical bug.

Any multi-step movement needs to follow transactional thinking:

- validate first
- move blocks deterministically
- update state
- synchronize clients

Never:
1. copy block
2. leave original
3. hope cleanup happens later

without robust recovery semantics.

Moving modded blocks should preserve BlockState correctly.

If BlockEntity movement is unsupported, reject it instead of risking corruption.

# TESTS

Create automated GameTests where realistically possible.

At minimum test:

1. Straight default path length 1

2. Straight path length 5

3. Long empty path

4. Path:
   FORWARD
   FORWARD
   UP

5. Path with several turns

6. Path cannot intersect itself

7. Path cannot enter piston base

8. Cannot remove final remaining segment

9. Cannot configure through occupied block

10. Block can be placed into route AFTER configuration

11. Piston encounters block later in route

Example:

P
empty
BLOCK
empty

and successfully pushes it

12. Block follows 90-degree corner

13. Normal Extended Piston leaves block at destination

14. Extended Sticky Piston brings attached block back through reverse route

15. Multiple pushed blocks

16. Push exactly configured push limit

17. Push one more than configured limit -> extension fails safely

18. Obsidian/immovable block

19. World height boundary

20. World border

21. Unloaded chunk boundary

22. Rapid redstone pulse

23. Power removed while extending

24. Power restored while retracting

25. Breaking retracted piston

26. Breaking extended piston

27. Restart while extended

28. Restart/reload during movement if architecture supports persistent movement

29. Multiplayer synchronization

30. Very long path does not cause recursion/stack overflow

Also manually test inside a BMC5 v51 client/server instance if a BMC5 mods directory is available.

# IMPLEMENTATION PRIORITIES

Do not try to perfect everything simultaneously.

Use these phases.

PHASE 1
- working NeoForge 1.21.1 project
- registration
- Extended Piston
- Extended Sticky Piston
- Path Tool
- placement/orientation
- block entity
- path persistence
- basic ghost preview
- path editing
- compile successfully

PHASE 2
- straight-path physical movement
- redstone state machine
- ordinary block pushing
- server authority
- push limit

PHASE 3
- turns
- blocks following curved route
- encounter block later in path
- reverse curved sticky retraction

PHASE 4
- robust multiplayer synchronization
- chunk boundaries
- restart recovery
- destruction edge cases
- rapid redstone

PHASE 5
- slime/honey compatibility
- entities
- visual polish
- performance optimization
- compatibility testing

After EACH phase:
- compile
- fix errors
- run relevant tests
- do not continue with known compiler errors

# IMPORTANT DESIGN EXPECTATION

Before implementing the movement engine, explicitly explain the movement model you plan to use.

I want to review that architecture in the code/comments.

In particular explain:

1. how the path is represented

2. how the moving head position is calculated

3. how a block encountered at trajectory position N is moved to N+1

4. how blocks move around a 90-degree corner

5. how multiple blocks are handled

6. how sticky retraction maps the payload through the reversed path

7. how world state stays safe if movement fails

8. how unloaded chunks are handled

Do not quietly reduce the requested mechanic to a straight multi-piston.

# EXAMPLE THAT MUST WORK

This is the canonical behavior example.

Place piston facing EAST.

Default:

P -> A

Use tool to append:

EAST
UP

Resulting configured path:

P -> A -> B
          |
          C

where C is the final head location.

Place a stone block at A before activating.

Power piston.

Expected conceptual movement:

Initial:

P BLOCK . .
        path turns upward later

During extension the stone is pushed along the route.

Final:

P -> shaft -> shaft
              |
             HEAD
              |
             STONE

Assuming UP is the final direction.

Then:

NORMAL EXTENDED PISTON:
- retracts
- stone remains at final destination

EXTENDED STICKY PISTON:
- retracts
- stone follows reverse path
- stone ends beside the piston base again

Another required example:

P -> empty -> STONE -> empty -> endpoint

The piston travels through the empty section first, encounters STONE, and then pushes STONE through the remaining path.

# CODE QUALITY

Use clean architecture.

Suggested conceptual separation:

- registration
- ExtendedPistonBlock
- ExtendedStickyPistonBlock
- ExtendedPistonBlockEntity
- path model
- movement engine
- movement validator / push resolver
- server config
- networking
- client renderer
- Path Tool
- GameTests

Do not put the entire mod into one 2000-line class.

Use comments for complicated movement logic.

Do not add unnecessary abstractions or dependencies.

# README

Create/update README.md containing:

- Minecraft version
- NeoForge requirement
- Java version
- installation
- no external content-mod dependencies
- how Extended Pistons work
- Path Tool controls
- push limit configuration
- path length has no artificial maximum
- unloaded chunks are not force-loaded
- known limitations
- compatibility notes
- testing instructions

# GRADLE / BUILD

Make sure:

./gradlew build

passes successfully.

Also provide the final JAR location.

Do not claim something compiles unless you actually run the build.

# FIRST RESPONSE / FIRST ACTION

Do NOT immediately dump hundreds of lines of speculative code.

First:

1. inspect the repository
2. inspect NeoForge/Minecraft versions
3. inspect relevant vanilla piston code/API for Minecraft 1.21.1
4. propose the architecture
5. specifically explain the curved-path block movement algorithm
6. identify any dangerous compatibility areas
7. then begin implementation

If some detail of the specification is technically impossible exactly as described, do not silently change it.

Explain the problem, propose the closest safe implementation, and continue with the best compatible solution.

The primary goals, in order, are:

1. no world corruption or duplication
2. NeoForge 1.21.1 correctness
3. BMC5 v51 compatibility
4. correct arbitrary 3D piston-path behavior
5. multiplayer/server correctness
6. performance
7. visual polish