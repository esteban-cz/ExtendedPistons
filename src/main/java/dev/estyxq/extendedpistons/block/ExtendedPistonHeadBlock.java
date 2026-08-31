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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

public final class ExtendedPistonHeadBlock extends BaseEntityBlock {
    public static final MapCodec<ExtendedPistonHeadBlock> CODEC = simpleCodec(ExtendedPistonHeadBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** Direction from the head cell back toward the preceding shaft/base cell. */
    public static final DirectionProperty CONNECTION = DirectionProperty.create("connection");
    public static final BooleanProperty STICKY = BooleanProperty.create("sticky");
    public static final BooleanProperty SHORT = BlockStateProperties.SHORT;
    private static final Map<Direction, VoxelShape> PLATES = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

    static {
        PLATES.put(Direction.DOWN, box(0, 0, 0, 16, 4, 16));
        PLATES.put(Direction.UP, box(0, 12, 0, 16, 16, 16));
        PLATES.put(Direction.NORTH, box(0, 0, 0, 16, 16, 4));
        PLATES.put(Direction.SOUTH, box(0, 0, 12, 16, 16, 16));
        PLATES.put(Direction.WEST, box(0, 0, 0, 4, 16, 16));
        PLATES.put(Direction.EAST, box(12, 0, 0, 16, 16, 16));

        ARMS.put(Direction.DOWN, box(6, 0, 6, 10, 8, 10));
        ARMS.put(Direction.UP, box(6, 8, 6, 10, 16, 10));
        ARMS.put(Direction.NORTH, box(6, 6, 0, 10, 10, 8));
        ARMS.put(Direction.SOUTH, box(6, 6, 8, 10, 10, 16));
        ARMS.put(Direction.WEST, box(0, 6, 6, 8, 10, 10));
        ARMS.put(Direction.EAST, box(8, 6, 6, 16, 10, 10));
    }

    public ExtendedPistonHeadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CONNECTION, Direction.SOUTH)
                .setValue(STICKY, false)
                .setValue(SHORT, false));
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

    public BlockState stateFor(Direction facing, Direction connection, boolean sticky) {
        return defaultBlockState().setValue(FACING, facing)
                .setValue(CONNECTION, connection).setValue(STICKY, sticky);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        Direction connection = state.getValue(CONNECTION);
        VoxelShape shape = Shapes.or(PLATES.get(facing), ARMS.get(facing));
        return connection == facing ? shape : Shapes.or(shape, ARMS.get(connection));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONNECTION, STICKY, SHORT);
    }
}
