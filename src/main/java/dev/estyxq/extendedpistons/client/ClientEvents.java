package dev.estyxq.extendedpistons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.estyxq.extendedpistons.block.ExtendedPistonBlock;
import dev.estyxq.extendedpistons.network.PathEditPayload;
import dev.estyxq.extendedpistons.network.PathOperation;
import dev.estyxq.extendedpistons.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.IEventBus;
import dev.estyxq.extendedpistons.registry.ModBlockEntities;
import org.joml.Matrix4f;

public final class ClientEvents {
    private ClientEvents() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientEvents::registerRenderers);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onInteraction);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onLogout);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if ((minecraft.player.tickCount % 10) == 0) {
            ClientPathCache.pruneInvalid(minecraft.level);
        }
        if ((minecraft.player.tickCount % 10) != 0
                || (!minecraft.player.getMainHandItem().is(ModItems.PISTON_PATH_TOOL.get())
                && !minecraft.player.getOffhandItem().is(ModItems.PISTON_PATH_TOOL.get()))) {
            return;
        }

        // Recover paths for pistons placed after their chunk was delivered, and
        // for worlds first opened with an older mod build. Only the handful of
        // chunks inside interaction reach are inspected, and only while the tool
        // is held; long configured paths are never traversed here.
        BlockPos playerPos = minecraft.player.blockPosition();
        int centerX = playerPos.getX() >> 4;
        int centerZ = playerPos.getZ() >> 4;
        int radius = Math.max(1, (int) Math.ceil(minecraft.player.blockInteractionRange() / 16.0D));
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                LevelChunk chunk = minecraft.level.getChunkSource()
                        .getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                chunk.getBlockEntities().forEach((pos, blockEntity) -> {
                    if (blockEntity instanceof dev.estyxq.extendedpistons.block.entity.ExtendedPistonBlockEntity
                            && minecraft.player.distanceToSqr(Vec3.atCenterOf(pos)) <= 512.0D * 512.0D) {
                        ClientPathCache.requestIfMissing(pos);
                    }
                });
            }
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.MOVEMENT_TRANSACTION.get(),
                MovementTransactionRenderer::new);
    }

    private static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || !minecraft.player.getItemInHand(event.getHand()).is(ModItems.PISTON_PATH_TOOL.get())
                || (!event.isAttack() && !event.isUseItem())) {
            return;
        }
        Vec3 eye = minecraft.player.getEyePosition();
        double reach = minecraft.player.blockInteractionRange();
        ClientPathCache.Target target = ClientPathCache.raycastEndpoint(
                eye, eye.add(minecraft.player.getViewVector(1.0F).scale(reach)));
        if (target == null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.extendedpistons.path.aim_help"), true);
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        // Match normal Minecraft placement/removal muscle memory: use adds one
        // cell from the selected endpoint face, while attack removes one cell.
        PathOperation operation = event.isUseItem() ? PathOperation.ADD : PathOperation.REMOVE;
        Direction direction = operation == PathOperation.ADD ? target.hit().getDirection() : null;
        PacketDistributor.sendToServer(new PathEditPayload(
                target.base(), event.getHand(), target.revision(), operation, direction));
        event.setCanceled(true);
        event.setSwingHand(true);
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || (!minecraft.player.getMainHandItem().is(ModItems.PISTON_PATH_TOOL.get())
                && !minecraft.player.getOffhandItem().is(ModItems.PISTON_PATH_TOOL.get()))) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        // QUADS keep every preview cuboid independent. The vanilla debug-filled
        // triangle strip can produce long connecting triangles between separate
        // piston paths under shader renderers.
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        int radius = minecraft.options.getEffectiveRenderDistance();
        int centerChunkX = BlockPos.containing(camera).getX() >> 4;
        int centerChunkZ = BlockPos.containing(camera).getZ() >> 4;
        ClientLevel level = minecraft.level;
        Vec3 eye = minecraft.player.getEyePosition(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        double reach = minecraft.player.blockInteractionRange();
        ClientPathCache.Target activeTarget = ClientPathCache.raycastEndpoint(
                eye, eye.add(minecraft.player.getViewVector(
                        event.getPartialTick().getGameTimeDeltaPartialTick(false)).scale(reach)));

        ClientPathCache.forEachVisibleChunk(centerChunkX, centerChunkZ, radius, segment -> {
            BlockPos pos = segment.position();
            AABB cell = new AABB(pos);
            if (!level.isLoaded(pos) || !level.isLoaded(segment.base())
                    || !(level.getBlockState(segment.base()).getBlock() instanceof ExtendedPistonBlock)
                    || !event.getFrustum().isVisible(cell)) {
                return;
            }
            if (segment.finalHead()) {
                boolean selected = activeTarget != null && activeTarget.base().equals(segment.base());
                float red = selected ? 0.20F : 0.35F;
                float green = selected ? 1.00F : 0.72F;
                float blue = selected ? 0.30F : 1.00F;
                float alpha = selected ? 0.58F : 0.38F;
                drawArm(poseStack, vertices, pos, segment.incoming().getOpposite(),
                        red, green, blue, alpha);
                drawHead(poseStack, vertices, pos, segment.incoming(), red, green, blue, alpha);
            } else {
                drawBox(poseStack, vertices, pos,
                        new AABB(0.375, 0.375, 0.375, 0.625, 0.625, 0.625),
                        0.55F, 0.55F, 0.55F, 0.28F);
                drawArm(poseStack, vertices, pos, segment.incoming().getOpposite(),
                        0.55F, 0.55F, 0.55F, 0.28F);
                if (segment.outgoing() != null) {
                    drawArm(poseStack, vertices, pos, segment.outgoing(),
                            0.55F, 0.55F, 0.55F, 0.28F);
                }
            }
        });
        if (activeTarget != null) {
            drawSelectedFace(poseStack, vertices, activeTarget.hit().getBlockPos(),
                    activeTarget.hit().getDirection());
        }
        buffers.endBatch(RenderType.debugQuads());
        if (activeTarget != null) {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(poseStack, lines,
                    new AABB(activeTarget.hit().getBlockPos()).inflate(0.025D),
                    0.20F, 1.00F, 0.30F, 0.90F);
            buffers.endBatch(RenderType.lines());
        }
        poseStack.popPose();
    }

    private static void drawSelectedFace(PoseStack poseStack, VertexConsumer vertices,
                                         BlockPos pos, Direction face) {
        double inset = 0.055D;
        double thickness = 0.025D;
        AABB marker = switch (face) {
            case DOWN -> new AABB(inset, -thickness, inset, 1.0D - inset, thickness, 1.0D - inset);
            case UP -> new AABB(inset, 1.0D - thickness, inset,
                    1.0D - inset, 1.0D + thickness, 1.0D - inset);
            case NORTH -> new AABB(inset, inset, -thickness,
                    1.0D - inset, 1.0D - inset, thickness);
            case SOUTH -> new AABB(inset, inset, 1.0D - thickness,
                    1.0D - inset, 1.0D - inset, 1.0D + thickness);
            case WEST -> new AABB(-thickness, inset, inset,
                    thickness, 1.0D - inset, 1.0D - inset);
            case EAST -> new AABB(1.0D - thickness, inset, inset,
                    1.0D + thickness, 1.0D - inset, 1.0D - inset);
        };
        drawBox(poseStack, vertices, pos, marker, 0.85F, 1.00F, 0.18F, 0.78F);
    }

    private static void drawArm(PoseStack poseStack, VertexConsumer vertices, BlockPos pos, Direction direction,
                                float red, float green, float blue, float alpha) {
        double minX = 0.375;
        double minY = 0.375;
        double minZ = 0.375;
        double maxX = 0.625;
        double maxY = 0.625;
        double maxZ = 0.625;
        switch (direction) {
            case DOWN -> minY = 0.0;
            case UP -> maxY = 1.0;
            case NORTH -> minZ = 0.0;
            case SOUTH -> maxZ = 1.0;
            case WEST -> minX = 0.0;
            case EAST -> maxX = 1.0;
        }
        drawBox(poseStack, vertices, pos, new AABB(minX, minY, minZ, maxX, maxY, maxZ),
                red, green, blue, alpha);
    }

    private static void drawHead(PoseStack poseStack, VertexConsumer vertices, BlockPos pos, Direction direction,
                                 float red, float green, float blue, float alpha) {
        AABB plate = switch (direction) {
            case DOWN -> new AABB(0.0625, 0.0, 0.0625, 0.9375, 0.25, 0.9375);
            case UP -> new AABB(0.0625, 0.75, 0.0625, 0.9375, 1.0, 0.9375);
            case NORTH -> new AABB(0.0625, 0.0625, 0.0, 0.9375, 0.9375, 0.25);
            case SOUTH -> new AABB(0.0625, 0.0625, 0.75, 0.9375, 0.9375, 1.0);
            case WEST -> new AABB(0.0, 0.0625, 0.0625, 0.25, 0.9375, 0.9375);
            case EAST -> new AABB(0.75, 0.0625, 0.0625, 1.0, 0.9375, 0.9375);
        };
        drawBox(poseStack, vertices, pos, plate, red, green, blue, alpha);
    }

    private static void drawBox(PoseStack poseStack, VertexConsumer vertices, BlockPos pos, AABB box,
                                float red, float green, float blue, float alpha) {
        Matrix4f pose = poseStack.last().pose();
        float minX = (float) (pos.getX() + box.minX);
        float minY = (float) (pos.getY() + box.minY);
        float minZ = (float) (pos.getZ() + box.minZ);
        float maxX = (float) (pos.getX() + box.maxX);
        float maxY = (float) (pos.getY() + box.maxY);
        float maxZ = (float) (pos.getZ() + box.maxZ);

        quad(vertices, pose, minX, minY, minZ, minX, maxY, minZ,
                minX, maxY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        quad(vertices, pose, maxX, minY, maxZ, maxX, maxY, maxZ,
                maxX, maxY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        quad(vertices, pose, minX, minY, maxZ, maxX, minY, maxZ,
                maxX, minY, minZ, minX, minY, minZ, red, green, blue, alpha);
        quad(vertices, pose, minX, maxY, minZ, maxX, maxY, minZ,
                maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        quad(vertices, pose, maxX, minY, minZ, maxX, maxY, minZ,
                minX, maxY, minZ, minX, minY, minZ, red, green, blue, alpha);
        quad(vertices, pose, minX, minY, maxZ, minX, maxY, maxZ,
                maxX, maxY, maxZ, maxX, minY, maxZ, red, green, blue, alpha);
    }

    private static void quad(VertexConsumer vertices, Matrix4f pose,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float red, float green, float blue, float alpha) {
        vertices.addVertex(pose, x1, y1, z1).setColor(red, green, blue, alpha);
        vertices.addVertex(pose, x2, y2, z2).setColor(red, green, blue, alpha);
        vertices.addVertex(pose, x3, y3, z3).setColor(red, green, blue, alpha);
        vertices.addVertex(pose, x4, y4, z4).setColor(red, green, blue, alpha);
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            ClientPathCache.removeBaseChunk(event.getChunk().getPos());
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPathCache.clear();
    }
}
