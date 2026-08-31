package dev.estyxq.extendedpistons.block;

import com.mojang.serialization.MapCodec;
import dev.estyxq.extendedpistons.block.entity.PistonPartBlockEntity;
import dev.estyxq.extendedpistons.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class PistonShaftBlock extends BaseEntityBlock {
    public static final MapCodec<PistonShaftBlock> CODEC = simpleCodec(PistonShaftBlock::new);
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    private static final VoxelShape CENTER = box(6, 6, 6, 10, 10, 10);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

    static {
        ARMS.put(Direction.DOWN, box(6, 0, 6, 10, 8, 10));
        ARMS.put(Direction.UP, box(6, 8, 6, 10, 16, 10));
        ARMS.put(Direction.NORTH, box(6, 6, 0, 10, 10, 8));
        ARMS.put(Direction.SOUTH, box(6, 6, 8, 10, 10, 16));
        ARMS.put(Direction.WEST, box(0, 6, 6, 8, 10, 10));
        ARMS.put(Direction.EAST, box(8, 6, 6, 16, 10, 10));
    }

    public PistonShaftBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(DOWN, false).setValue(UP, false)
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(EAST, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PistonPartBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PistonPartBlockEntity part) {
            part.requestEmergencySettle();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PistonPartBlockEntity part) {
            part.requestEmergencySettle();
        }
        super.wasExploded(level, pos, explosion);
    }

    public BlockState stateFor(Direction incoming, @Nullable Direction outgoing) {
        BlockState state = defaultBlockState();
        state = connect(state, incoming.getOpposite());
        return outgoing == null ? state : connect(state, outgoing);
    }

    private static BlockState connect(BlockState state, Direction direction) {
        return state.setValue(property(direction), true);
    }

    private static BooleanProperty property(Direction direction) {
        return switch (direction) {
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CENTER;
        for (Direction direction : Direction.values()) {
            if (state.getValue(property(direction))) {
                shape = Shapes.or(shape, ARMS.get(direction));
            }
        }
        return shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DOWN, UP, NORTH, SOUTH, WEST, EAST);
    }
}
