package dev.estyxq.extendedpistons.movement;

import dev.estyxq.extendedpistons.block.TransactionPhase;
import dev.estyxq.extendedpistons.path.PistonPath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementInvariantTest {
    @Test
    void directionMappingChangesAtCornersAndContinuesAtTail() {
        PistonPath path = new PistonPath(BlockPos.ZERO, Direction.EAST);
        path.append(Direction.NORTH);
        path.append(Direction.UP);

        assertEquals(Direction.NORTH, MovementPlanner.directionAfter(path, 0));
        assertEquals(Direction.UP, MovementPlanner.directionAfter(path, 1));
        assertEquals(Direction.UP, MovementPlanner.directionAfter(path, 2));
        assertEquals(new BlockPos(1, 2, -1), path.positionAt(3));
    }

    @Test
    void reverseStepHeadFacesAwayFromItsDestination() {
        PistonPath path = new PistonPath(BlockPos.ZERO, Direction.EAST);
        path.append(Direction.UP);

        assertEquals(Direction.UP, MovementPlanner.retractionHeadFacing(path, 1));
        assertEquals(Direction.EAST, MovementPlanner.retractionHeadFacing(path, 0));
        assertEquals(Direction.WEST, MovementPlanner.headConnection(path, 0));
        assertEquals(Direction.DOWN, MovementPlanner.headConnection(path, 1));
        assertEquals(Direction.WEST, MovementPlanner.headConnection(path, -1));
    }

    @Test
    void preparingJournalRollsBackAndCommittedJournalFinishesForward() {
        MovementCell cell = new MovementCell(BlockPos.ZERO, 0,
                Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
                Blocks.STONE.defaultBlockState(), Direction.EAST, false);
        StepPlan plan = new StepPlan(List.of(cell), List.of(), 0, true, false);
        MovementJournal journal = new MovementJournal(42L, TransactionPhase.PREPARING,
                plan, 100L, 2);

        assertFalse(journal.shouldFinishForward());
        assertEquals("PREPARING", journal.save().getString("Phase"));
        journal.commit();
        assertTrue(journal.shouldFinishForward());
        assertEquals("COMMITTED", journal.save().getString("Phase"));
        journal.setEntityProgress(1);
        assertEquals(1, journal.save().getInt("EntityProgress"));
    }
}
