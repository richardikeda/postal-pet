package br.com.labcraft.petpost;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.server.level.ServerLevel;

/** Directional mailbox block. Stores the selected range and activation state in the world's block state. */
public final class PetPostBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<PetPostBlock> CODEC = simpleCodec(PetPostBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty RADIUS_STEP = IntegerProperty.create("radius_step", 0, 3);
    public static final BooleanProperty FLAG_RAISED = BooleanProperty.create("flag_raised");

    public PetPostBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(RADIUS_STEP, 1)
                .setValue(FLAG_RAISED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(RADIUS_STEP, PostRadius.closestStep(PetPostConfig.get().defaultRadius))
                .setValue(FLAG_RAISED, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RADIUS_STEP, FLAG_RAISED);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, net.minecraft.core.BlockPos pos,
                                               boolean movedByPiston) {
        PetHomeFollowGuard.forgetPost(level, pos);
        PetPostSavedData.get(level).remove(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    public static int radius(BlockState state) { return PostRadius.atStep(state.getValue(RADIUS_STEP)); }
    public static int nextStep(BlockState state) { return PostRadius.nextStep(state.getValue(RADIUS_STEP)); }
    public static int closestSupportedRadius(int requested) {
        return PostRadius.atStep(PostRadius.closestStep(requested));
    }
}
