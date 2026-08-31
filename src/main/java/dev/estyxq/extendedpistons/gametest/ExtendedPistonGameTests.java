package dev.estyxq.extendedpistons.gametest;

import dev.estyxq.extendedpistons.ExtendedPistons;
import dev.estyxq.extendedpistons.block.ExtendedPistonBlock;
import dev.estyxq.extendedpistons.block.ExtendedPistonHeadBlock;
import dev.estyxq.extendedpistons.block.MovementState;
import dev.estyxq.extendedpistons.block.TransactionPhase;
import dev.estyxq.extendedpistons.block.entity.ExtendedPistonBlockEntity;
import dev.estyxq.extendedpistons.block.entity.MovementTransactionBlockEntity;
import dev.estyxq.extendedpistons.movement.MovementPlanner;
import dev.estyxq.extendedpistons.movement.PlanStatus;
import dev.estyxq.extendedpistons.path.PistonPath;
import dev.estyxq.extendedpistons.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.List;

@GameTestHolder(ExtendedPistons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExtendedPistonGameTests {
    private static final BlockPos BASE = new BlockPos(2, 2, 2);

    private ExtendedPistonGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void straightDefaultPathLength1(GameTestHelper helper) {
        power(place(helper, false));
        expectAt(helper, 15, new BlockPos(3, 2, 2), ModBlocks.EXTENDED_PISTON_HEAD.get());
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void straightPathLength5(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false,
                Direction.EAST, Direction.EAST, Direction.EAST, Direction.EAST);
        power(piston);
        expectAt(helper, 30, new BlockPos(7, 2, 2), ModBlocks.EXTENDED_PISTON_HEAD.get());
    }

    @GameTest(template = "empty")
    public static void longEmptyPath(GameTestHelper helper) {
        PistonPath path = new PistonPath(BlockPos.ZERO, Direction.EAST);
        for (int index = 1; index < 2_000; index++) helper.assertTrue(path.append(Direction.EAST), "append failed");
        helper.assertValueEqual(path.size(), 2_000, "long path length");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void forwardForwardUp(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false, Direction.EAST, Direction.UP);
        power(piston);
        helper.runAtTickTime(25, () -> {
            helper.assertBlockProperty(new BlockPos(4, 3, 2), ExtendedPistonHeadBlock.FACING, Direction.UP);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void cornerHeadHasIndependentPlateAndBaseConnections(GameTestHelper helper) {
        BlockState head = ModBlocks.EXTENDED_PISTON_HEAD.get().stateFor(
                Direction.UP, Direction.WEST, false);
        helper.assertValueEqual(head.getValue(ExtendedPistonHeadBlock.FACING),
                Direction.UP, "head plate direction");
        helper.assertValueEqual(head.getValue(ExtendedPistonHeadBlock.CONNECTION),
                Direction.WEST, "head base-side connection");
        helper.assertTrue(head.getShape(helper.getLevel(), helper.absolutePos(BASE)).toAabbs().size() >= 3,
                "corner head did not contain its plate and both shaft arms");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void extendingCornerHeadTurnsConnectorAtMidpoint(GameTestHelper helper) {
        BlockState sourceHead = ModBlocks.EXTENDED_PISTON_HEAD.get().stateFor(
                Direction.UP, Direction.WEST, false);
        BlockState supportElbow = ModBlocks.PISTON_SHAFT.get().stateFor(
                Direction.EAST, Direction.UP);
        helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.MOVEMENT_TRANSACTION.get());
        MovementTransactionBlockEntity transaction = helper.getBlockEntity(new BlockPos(2, 2, 2));
        transaction.configure(sourceHead, supportElbow, sourceHead,
                helper.absolutePos(BASE), 7L, TransactionPhase.COMMITTED,
                Direction.UP, false, helper.getLevel().getGameTime(), 4);

        helper.assertValueEqual(transaction.renderStateAtProgress(0.25D)
                        .getValue(ExtendedPistonHeadBlock.CONNECTION),
                Direction.WEST, "extension changed the source connector too early");
        helper.assertValueEqual(transaction.renderStateAtProgress(0.75D)
                        .getValue(ExtendedPistonHeadBlock.CONNECTION),
                Direction.DOWN, "extension did not turn back toward its support shaft");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pathWithSeveralTurns(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false,
                Direction.SOUTH, Direction.UP, Direction.WEST, Direction.NORTH);
        power(piston);
        helper.runAtTickTime(35, () -> {
            helper.assertBlockProperty(new BlockPos(2, 3, 2), ExtendedPistonHeadBlock.FACING, Direction.NORTH);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void pathCannotIntersectItself(GameTestHelper helper) {
        PistonPath path = new PistonPath(BlockPos.ZERO, Direction.EAST);
        helper.assertTrue(path.append(Direction.NORTH), "setup append");
        helper.assertFalse(path.canAppend(Direction.SOUTH), "self intersection accepted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pathCannotEnterBase(GameTestHelper helper) {
        PistonPath path = new PistonPath(BlockPos.ZERO, Direction.EAST);
        helper.assertFalse(path.canAppend(Direction.WEST), "base intersection accepted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cannotRemoveFinalSegment(GameTestHelper helper) {
        helper.assertFalse(new PistonPath(BlockPos.ZERO, Direction.EAST).removeLast(), "removed final segment");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cannotConfigureThroughOccupiedBlock(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.STONE);
        helper.assertFalse(piston.tryAppend(Direction.EAST), "configured through occupied block");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void blockMayEnterRouteAfterConfiguration(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false, Direction.EAST);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.STONE);
        power(piston);
        expectAt(helper, 25, new BlockPos(5, 2, 2), Blocks.STONE);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void encountersBlockLaterInRoute(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false, Direction.EAST, Direction.EAST);
        helper.setBlock(new BlockPos(5, 2, 2), Blocks.STONE);
        power(piston);
        expectAt(helper, 30, new BlockPos(6, 2, 2), Blocks.STONE);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void blockFollowsNinetyDegreeCorner(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false, Direction.EAST, Direction.UP);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.STONE);
        power(piston);
        expectAt(helper, 30, new BlockPos(4, 4, 2), Blocks.STONE);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void normalPistonLeavesBlock(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
        power(piston);
        helper.runAtTickTime(15, () -> unpower(piston));
        expectAt(helper, 35, new BlockPos(4, 2, 2), Blocks.STONE);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stickyPistonReturnsBlock(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, true);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
        power(piston);
        helper.runAtTickTime(15, () -> unpower(piston));
        expectAt(helper, 35, new BlockPos(3, 2, 2), Blocks.STONE);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void multiplePushedBlocks(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false);
        for (int x = 3; x <= 5; x++) helper.setBlock(new BlockPos(x, 2, 2), Blocks.STONE);
        power(piston);
        expectAt(helper, 25, new BlockPos(6, 2, 2), Blocks.STONE);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void pushesExactlyConfiguredLimit(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false);
        for (int x = 3; x < 15; x++) helper.setBlock(new BlockPos(x, 2, 2), Blocks.STONE);
        power(piston);
        expectAt(helper, 25, new BlockPos(15, 2, 2), Blocks.STONE);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void oneOverPushLimitFailsSafely(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false);
        for (int x = 3; x < 16; x++) helper.setBlock(new BlockPos(x, 2, 2), Blocks.STONE);
        power(piston);
        helper.runAtTickTime(35, () -> {
            helper.assertValueEqual(piston.movementState(), MovementState.BLOCKED, "not blocked");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, 2));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void immovableObsidian(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.OBSIDIAN);
        power(piston);
        helper.runAtTickTime(25, () -> {
            helper.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(3, 2, 2));
            helper.assertValueEqual(piston.movementState(), MovementState.BLOCKED, "not blocked");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void worldHeightBoundary(GameTestHelper helper) {
        BlockPos base = new BlockPos(0, helper.getLevel().getMaxBuildHeight() - 1, 0);
        PistonPath path = new PistonPath(base, Direction.UP);
        helper.assertValueEqual(MovementPlanner.preflightExtension(helper.getLevel(), path, -1, 12, false),
                PlanStatus.BLOCKED, "height boundary accepted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void worldBorder(GameTestHelper helper) {
        PistonPath path = new PistonPath(new BlockPos(30_000_100, 64, 0), Direction.EAST);
        helper.assertValueEqual(MovementPlanner.preflightExtension(helper.getLevel(), path, -1, 12, false),
                PlanStatus.BLOCKED, "world border accepted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unloadedChunkBoundary(GameTestHelper helper) {
        PistonPath path = new PistonPath(new BlockPos(10_000, 64, 10_000), Direction.EAST);
        helper.assertValueEqual(MovementPlanner.preflightExtension(helper.getLevel(), path, -1, 12, false),
                PlanStatus.UNLOADED, "unloaded path was not paused");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rapidRedstonePulse(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false);
        power(piston);
        helper.runAtTickTime(1, () -> unpower(piston));
        helper.runAtTickTime(2, () -> power(piston));
        helper.runAtTickTime(3, () -> unpower(piston));
        helper.runAtTickTime(35, () -> {
            helper.assertValueEqual(piston.movementState(), MovementState.RETRACTED, "pulse did not settle");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void powerRemovedWhileExtending(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false, Direction.EAST, Direction.EAST);
        power(piston);
        helper.runAtTickTime(3, () -> unpower(piston));
        helper.runAtTickTime(40, () -> {
            helper.assertValueEqual(piston.movementState(), MovementState.RETRACTED, "did not reverse");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void powerRestoredWhileRetracting(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false, Direction.EAST);
        power(piston);
        helper.runAtTickTime(18, () -> unpower(piston));
        helper.runAtTickTime(21, () -> power(piston));
        helper.runAtTickTime(55, () -> {
            helper.assertValueEqual(piston.movementState(), MovementState.EXTENDED, "did not reverse again");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void breakingRetractedPiston(GameTestHelper helper) {
        place(helper, false);
        helper.destroyBlock(BASE);
        helper.assertBlockPresent(Blocks.AIR, BASE);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void replacementPistonStartsWithFreshPath(GameTestHelper helper) {
        ExtendedPistonBlockEntity oldPiston = place(helper, false,
                Direction.NORTH, Direction.UP);
        helper.assertValueEqual(oldPiston.path().size(), 3, "old path length");
        helper.destroyBlock(BASE);

        ExtendedPistonBlockEntity replacement = place(helper, false);
        helper.assertValueEqual(replacement.path().size(), 1, "replacement path length");
        helper.assertValueEqual(replacement.path().endpoint(), helper.absolutePos(BASE.east()),
                "replacement endpoint");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void extendedBaseShapeMatchesVanillaInEveryDirection(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(BASE);
        for (Direction direction : Direction.values()) {
            var extended = ModBlocks.EXTENDED_PISTON.get().defaultBlockState()
                    .setValue(ExtendedPistonBlock.FACING, direction)
                    .setValue(ExtendedPistonBlock.EXTENDED, true);
            var vanilla = Blocks.PISTON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, direction)
                    .setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.EXTENDED, true);
            helper.assertValueEqual(extended.getShape(helper.getLevel(), absolute).bounds(),
                    vanilla.getShape(helper.getLevel(), absolute).bounds(),
                    direction + " extended base bounds");
            helper.assertTrue(extended.useShapeForLightOcclusion(),
                    direction + " extended base did not use its partial occlusion shape");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void breakingExtendedPiston(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false, Direction.EAST);
        power(piston);
        helper.runAtTickTime(25, () -> helper.destroyBlock(BASE));
        helper.runAtTickTime(35, () -> {
            helper.assertBlockNotPresent(ModBlocks.EXTENDED_PISTON_HEAD.get(), new BlockPos(3, 2, 2));
            helper.assertBlockNotPresent(ModBlocks.PISTON_SHAFT.get(), new BlockPos(3, 2, 2));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void restartWhileExtendedPersistsState(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false);
        power(piston);
        helper.runAtTickTime(20, () -> {
            CompoundTag saved = piston.saveWithFullMetadata(helper.getLevel().registryAccess());
            helper.assertValueEqual(saved.getString("MovementState"), MovementState.EXTENDED.name(),
                    "extended state not persisted");
            helper.assertValueEqual(saved.getInt("HeadIndex"), 0, "head index not persisted");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void reloadDuringMovementPersistsJournal(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false);
        piston.setDesiredPowered(true);
        ExtendedPistonBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(BASE),
                piston.getBlockState(), piston);
        ExtendedPistonBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(BASE),
                piston.getBlockState(), piston);
        CompoundTag saved = piston.saveWithFullMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(saved.contains("ActiveJournal", CompoundTag.TAG_COMPOUND),
                "active transaction journal not persisted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void multiplayerSynchronizationSurface(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false, Direction.EAST, Direction.UP);
        CompoundTag update = piston.getUpdateTag(helper.getLevel().registryAccess());
        helper.assertFalse(update.contains("PathData"), "unbounded path leaked into vanilla update packet");
        helper.assertValueEqual(piston.path().pack().length, 2, "packed sync payload length");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void technicalPartOcclusionPredicatesAreSafe(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        for (Block block : List.of(ModBlocks.PISTON_SHAFT.get(),
                ModBlocks.EXTENDED_PISTON_HEAD.get(), ModBlocks.MOVEMENT_TRANSACTION.get())) {
            for (var state : block.getStateDefinition().getPossibleStates()) {
                helper.assertFalse(state.isViewBlocking(helper.getLevel(), pos),
                        block + " unexpectedly blocks view");
                helper.assertFalse(state.isSuffocating(helper.getLevel(), pos),
                        block + " unexpectedly suffocates");
                helper.assertFalse(state.isRedstoneConductor(helper.getLevel(), pos),
                        block + " unexpectedly conducts redstone");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void veryLongPathIsIterative(GameTestHelper helper) {
        PistonPath path = new PistonPath(BlockPos.ZERO, Direction.EAST);
        for (int index = 1; index < 100_000; index++) {
            if (!path.append(Direction.EAST)) helper.fail("iterative append failed at " + index);
        }
        PistonPath decoded = PistonPath.fromPacked(BlockPos.ZERO, path.size(), path.pack());
        helper.assertValueEqual(decoded.endpoint(), new BlockPos(100_000, 0, 0), "long path endpoint");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void slimeAttachmentMovesIteratively(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.SLIME_BLOCK);
        helper.setBlock(new BlockPos(3, 3, 2), Blocks.STONE);
        power(piston);
        helper.runAtTickTime(25, () -> {
            helper.assertBlockPresent(Blocks.SLIME_BLOCK, new BlockPos(4, 2, 2));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 3, 2));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void honeyAttachmentMovesIteratively(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.HONEY_BLOCK);
        helper.setBlock(new BlockPos(3, 3, 2), Blocks.STONE);
        power(piston);
        helper.runAtTickTime(25, () -> {
            helper.assertBlockPresent(Blocks.HONEY_BLOCK, new BlockPos(4, 2, 2));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 3, 2));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stickySlimeAttachmentReturnsAsAComponent(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, true);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.SLIME_BLOCK);
        helper.setBlock(new BlockPos(3, 3, 2), Blocks.STONE);
        power(piston);
        helper.runAtTickTime(15, () -> unpower(piston));
        helper.runAtTickTime(35, () -> {
            helper.assertBlockPresent(Blocks.SLIME_BLOCK, new BlockPos(3, 2, 2));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 3, 2));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void conflictingStickyCornerVectorsFailClosed(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false, Direction.NORTH, Direction.EAST);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.SLIME_BLOCK);
        helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE);
        power(piston);
        helper.runAtTickTime(25, () -> {
            helper.assertValueEqual(piston.movementState(), MovementState.BLOCKED,
                    "conflicting sticky component moved");
            helper.assertBlockPresent(Blocks.SLIME_BLOCK, new BlockPos(3, 2, 2));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void movingHeadPushesEntity(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false);
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new Vec3(3.5, 2.1, 2.5));
        double startX = stand.getX();
        power(piston);
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(stand.getX() > startX + 0.5D, "entity was not pushed by the moving head");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void upwardPayloadLiftsPlayer(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = placeFacing(helper, false, Direction.UP);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        NetworkRegistry.configureMockConnection(player.connection.getConnection());
        player.setPos(helper.absoluteVec(new Vec3(2.5D, 4.0D, 2.5D)));
        double startY = player.getY();
        power(piston);
        helper.runAtTickTime(25, () -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 4, 2));
            AABB finalPayload = new AABB(helper.absolutePos(new BlockPos(2, 4, 2)));
            helper.assertTrue(player.getY() >= finalPayload.maxY - 1.0E-6D
                            && !player.getBoundingBox().intersects(finalPayload),
                    "upward payload left the player embedded (start=" + startY
                            + ", current=" + player.getY() + ", payload=" + finalPayload + ")");
            helper.assertTrue(player.getY() <= finalPayload.maxY + 0.15D,
                    "upward platform launched the player instead of carrying them: " + player.getY());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void lateClientTransactionStillExpelsPlayer(GameTestHelper helper) {
        BlockPos source = new BlockPos(2, 2, 2);
        helper.setBlock(source, ModBlocks.MOVEMENT_TRANSACTION.get());
        MovementTransactionBlockEntity transaction = helper.getBlockEntity(source);
        long startTime = helper.getLevel().getGameTime() - 4L;
        transaction.configure(Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(),
                Blocks.STONE.defaultBlockState(), helper.absolutePos(source.below()), 1L,
                TransactionPhase.COMMITTED, Direction.UP, false, startTime, 4);

        BlockPos absoluteSource = helper.absolutePos(source);
        var collisionBeforePush = helper.getLevel().getBlockState(absoluteSource)
                .getCollisionShape(helper.getLevel(), absoluteSource);
        helper.assertTrue(!collisionBeforePush.isEmpty()
                        && collisionBeforePush.bounds().maxY <= 1.0D + 1.0E-6D,
                "payload collision advanced before the player push");

        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        NetworkRegistry.configureMockConnection(player.connection.getConnection());
        player.setPos(helper.absoluteVec(new Vec3(2.5D, 3.0D, 2.5D)));
        MovementTransactionBlockEntity.clientTick(helper.getLevel(), absoluteSource,
                helper.getLevel().getBlockState(absoluteSource), transaction);

        var collisionAfterPush = helper.getLevel().getBlockState(absoluteSource)
                .getCollisionShape(helper.getLevel(), absoluteSource);
        helper.assertTrue(!collisionAfterPush.isEmpty()
                        && collisionAfterPush.bounds().maxY >= 2.0D - 1.0E-6D,
                "payload collision did not advance after the player push");
        AABB destination = new AABB(helper.absolutePos(source.above()));
        helper.assertTrue(player.getY() >= destination.maxY - 1.0E-6D
                        && !player.getBoundingBox().intersects(destination),
                "late transaction catch-up left the player embedded at " + player.getY());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void destroyReactionDropsAfterCommit(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.DIRT);
        // The transaction's swept-volume entity handling can carry the new drop
        // one cell forward; keep a floor under both cells so the assertion tests
        // the drop itself rather than how far it fell by tick 25.
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.DIRT);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.DANDELION);
        power(piston);
        helper.runAtTickTime(25, () -> {
            // Item spawn velocity plus the committed head's swept-volume push can
            // carry the drop several cells before this delayed assertion.
            helper.assertItemEntityPresent(Items.DANDELION, new BlockPos(3, 2, 2), 8.0D);
            helper.assertBlockPresent(ModBlocks.EXTENDED_PISTON_HEAD.get(), new BlockPos(3, 2, 2));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void arbitraryBlockEntityIsRejected(GameTestHelper helper) {
        ExtendedPistonBlockEntity piston = place(helper, false);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.CHEST);
        power(piston);
        helper.runAtTickTime(25, () -> {
            helper.assertValueEqual(piston.movementState(), MovementState.BLOCKED,
                    "block entity was moved");
            helper.assertBlockPresent(Blocks.CHEST, new BlockPos(3, 2, 2));
            helper.succeed();
        });
    }

    private static ExtendedPistonBlockEntity place(GameTestHelper helper, boolean sticky,
                                                    Direction... additionalDirections) {
        return placeFacing(helper, sticky, Direction.EAST, additionalDirections);
    }

    private static ExtendedPistonBlockEntity placeFacing(GameTestHelper helper, boolean sticky,
                                                          Direction facing,
                                                          Direction... additionalDirections) {
        Block block = sticky ? ModBlocks.EXTENDED_STICKY_PISTON.get() : ModBlocks.EXTENDED_PISTON.get();
        helper.setBlock(BASE, block.defaultBlockState()
                .setValue(ExtendedPistonBlock.FACING, facing)
                .setValue(ExtendedPistonBlock.EXTENDED, false)
                .setValue(ExtendedPistonBlock.ACTIVE, false));
        ExtendedPistonBlockEntity piston = helper.getBlockEntity(BASE);
        piston.resetPath(facing);
        for (Direction direction : additionalDirections) {
            helper.assertTrue(piston.tryAppend(direction), "could not append " + direction);
        }
        return piston;
    }

    private static void power(ExtendedPistonBlockEntity piston) {
        piston.getLevel().setBlock(piston.getBlockPos().below(), Blocks.REDSTONE_BLOCK.defaultBlockState(),
                Block.UPDATE_ALL);
        piston.setDesiredPowered(true);
    }

    private static void unpower(ExtendedPistonBlockEntity piston) {
        piston.getLevel().setBlock(piston.getBlockPos().below(), Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);
        piston.setDesiredPowered(false);
    }

    private static void expectAt(GameTestHelper helper, long tick, BlockPos pos, Block block) {
        helper.runAtTickTime(tick, () -> {
            helper.assertBlockPresent(block, pos);
            helper.succeed();
        });
    }
}
