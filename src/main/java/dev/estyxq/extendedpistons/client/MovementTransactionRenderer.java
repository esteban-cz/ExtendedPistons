package dev.estyxq.extendedpistons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.estyxq.extendedpistons.block.entity.MovementTransactionBlockEntity;
import dev.estyxq.extendedpistons.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MovementTransactionRenderer implements BlockEntityRenderer<MovementTransactionBlockEntity> {
    public MovementTransactionRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MovementTransactionBlockEntity transaction, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (transaction.getLevel() == null) {
            return;
        }

        // Transaction blocks replace every affected cell for the whole animation.
        // Keep the stationary shaft underneath a moving head: the completed shaft
        // when extending, or the not-yet-consumed shaft when retracting. This is
        // the connector layer that prevents elbows and straight rods from blinking
        // out while the head's own rod travels through the cell.
        BlockState support = supportState(transaction.originalState(), transaction.finalState());
        if (!support.isAir()) {
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    support, poseStack, buffers, packedLight, packedOverlay);
        }
        if (transaction.renderState().isAir()) {
            return;
        }
        float linearProgress = Mth.clamp((transaction.getLevel().getGameTime() + partialTick
                - transaction.startGameTime()) / transaction.duration(), 0.0F, 1.0F);
        // Smoothstep removes the abrupt velocity change at the start and end of
        // every cell while preserving the authoritative server duration.
        float progress = linearProgress * linearProgress * (3.0F - 2.0F * linearProgress);
        double offset = transaction.renderAtDestination() ? progress - 1.0D : progress;
        var normal = transaction.renderDirection().getNormal();
        poseStack.pushPose();
        poseStack.translate(normal.getX() * offset, normal.getY() * offset, normal.getZ() * offset);
        BlockState movingState = transaction.renderStateAtProgress(linearProgress);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                movingState, poseStack, buffers, packedLight, packedOverlay);
        poseStack.popPose();
    }

    static BlockState supportState(BlockState originalState, BlockState finalState) {
        if (originalState.is(ModBlocks.EXTENDED_PISTON_HEAD.get())
                && finalState.is(ModBlocks.PISTON_SHAFT.get())) {
            return finalState;
        }
        if (originalState.is(ModBlocks.PISTON_SHAFT.get())
                && finalState.is(ModBlocks.EXTENDED_PISTON_HEAD.get())) {
            return originalState;
        }
        return Blocks.AIR.defaultBlockState();
    }
}
