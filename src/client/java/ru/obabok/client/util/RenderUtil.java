package ru.obabok.client.util;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import fi.dy.masa.malilib.util.data.Color4f;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import ru.obabok.client.Config;
import ru.obabok.client.Scan;
import ru.obabok.client.gui.screens.ScanTaskScreen;
import ru.obabok.common.model.BlockArea;
import ru.obabok.common.References;

import java.lang.Math;
import java.util.*;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static fi.dy.masa.malilib.render.RenderUtils.renderAreaOutline;

public class RenderUtil {
    public static final List<BlockPos> renderBlocksList = new CopyOnWriteArrayList<>();
    private static final List<ChunkPos> renderChunksList = new CopyOnWriteArrayList<>();
    private static final Minecraft client = Minecraft.getInstance();

    private static final ByteBufferBuilder allocator = new ByteBufferBuilder(1024 * 1024 * 2); // 2 MB
    private static BufferBuilder buffer;
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    //new
    private static final RenderPipeline BLOCKS_RENDER = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(References.MOD_ID, "pipeline/box")).withDepthStencilState(Optional.empty())
            .build()
    );
    private static final ByteBufferBuilder sortAllocator = new ByteBufferBuilder(1024 * 1024);


    public static void render(LevelRenderContext context) {
        if(!Config.Generic.MAIN_RENDER.getBooleanValue()) return;
        if(Minecraft.getInstance().gui.hud.isHidden()) return;
        BlockArea scanRange = Scan.getArea();
        if(scanRange != null){
            for (int i = 0; i < scanRange.size(); i++) {
                if(Minecraft.getInstance().gui.screen() instanceof ScanTaskScreen && i != ScanTaskScreen.selectedBoxIndex){
                    renderAreaOutline(scanRange.getArea(i).min(), scanRange.getArea(i).max(), 1, Color4f.fromColor(CommonColors.GRAY), Color4f.fromColor(CommonColors.GRAY),Color4f.fromColor(CommonColors.GRAY));
                }else{
                    renderAreaOutline(scanRange.getArea(i).min(), scanRange.getArea(i).max(), 1, Color4f.fromColor(CommonColors.RED), Color4f.fromColor(CommonColors.GREEN),Color4f.fromColor(CommonColors.BLUE));
                }
            }
//            if(Config.Generic.AREA_EDGE_RENDER.getBooleanValue()){
//                renderAreaSides(scanRange.min(), scanRange.max(), Config.Generic.AREA_EDGE_COLOR.getColor(), false);
//            }
        }

/*        //test render process scheduler
        if(Config.Generic.RENDER_PROCESS_QUEUE.getBooleanValue()){
            if (!ChunkScheduler.getChunkQueue().isEmpty()){
                Queue<ChunkPos> queue = ChunkScheduler.getChunkQueue();
                queue.forEach(chunkPos -> {
                    //renderAreaEdges(context, chunkPos.getStartPos(), chunkPos.getStartPos().add(16,100,16));
                });
            }
        }*/

        if(!renderChunksList.isEmpty() || !renderBlocksList.isEmpty()){
            try {

                context.poseStack().pushPose();
                if (buffer == null) {
                    buffer = new BufferBuilder(allocator, BLOCKS_RENDER.getPrimitiveTopology(), BLOCKS_RENDER.getVertexFormatBinding(0));
                }
                prepareRenderChunks(context);

                prepareRenderBlocksOptimized(context);
                context.poseStack().popPose();
                renderAll(Minecraft.getInstance(), BLOCKS_RENDER, context.poseStack());
            }catch (Exception ignored){

            }

        }
    }


    private static void prepareRenderChunks(LevelRenderContext context){
        if(renderChunksList.isEmpty()) return;
        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;


        Color4f chunkColor = Config.Generic.UNLOADED_CHUNK_COLOR.getColor();
        int maxChunkDist = Config.Generic.UNLOADED_CHUNK_MAX_DISTANCE.getIntegerValue();
        int offset = Config.Generic.UNLOADED_CHUNK_Y_OFFSET.getIntegerValue();
        boolean checkChunkDist = maxChunkDist >= 0;
        int maxDistSq = maxChunkDist * maxChunkDist;

        for (int i = 0; i < renderChunksList.size(); i++) {
            ChunkPos pos = renderChunksList.get(i);
            double centerX = (pos.x() << 4) + 8.5;
            double centerZ = (pos.z() << 4) + 8.5;

            double relx = centerX - camera.x;
            double relz = centerZ - camera.z;
            if (checkChunkDist) {
                double distSq = relx * relx + relz * relz;
                if (distSq > maxDistSq) {
                    continue;
                }
            }
            filledPlane(matrices.last().pose(), pos.x() << 4, pos.z() << 4, (pos.x() << 4) + 16, (pos.z() << 4) + 16, camera.y + offset, camera, chunkColor);
        }
    }

    private static void prepareRenderBlocksOptimized(LevelRenderContext context) {
        if(renderBlocksList.isEmpty()) return;

        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        Quaternionf orientation = context.levelState().cameraRenderState.orientation;

        Vec3 lookDirection = getLookDirection(orientation);

        int maxDistance = Config.Generic.SELECTED_BLOCKS_MAX_DISTANCE.getIntegerValue();
        boolean checkDistance = maxDistance != -1;
        Color4f color = Color4f.fromColor(Config.Generic.SELECTED_BLOCKS_COLOR.getIntegerValue());

        if(Config.Generic.AREA_EDGE_RENDER.getBooleanValue()){
            BlockArea area = Scan.getArea();
            for (int i = 0; i < area.size(); i++) {
                if((camera.x <= area.getArea(i).min().getX() || camera.x >= area.getArea(i).max().getX() + 1) ||
                        (camera.y <= area.getArea(i).min().getY() || camera.y >= area.getArea(i).max().getY() + 1) ||
                        (camera.z <= area.getArea(i).min().getZ() || camera.z >= area.getArea(i).max().getZ() + 1)
                ){
                    newRenderFilledBox(matrices.last().pose(), area.getArea(i).min().getX(), area.getArea(i).min().getY(), area.getArea(i).min().getZ(), area.getArea(i).max().getX() + 1, area.getArea(i).max().getY() + 1, area.getArea(i).max().getZ() + 1, camera, Config.Generic.AREA_EDGE_COLOR.getColor());
                }
            }

        }


        for (BlockPos pos : renderBlocksList) {
            double relX = pos.getX() + 0.5 - camera.x;
            double relY = pos.getY() + 0.5 - camera.y;
            double relZ = pos.getZ() + 0.5 - camera.z;

            double distXZ = Math.sqrt(relX * relX + relZ * relZ);
            double distance = Math.sqrt(relX * relX + relY * relY + relZ * relZ);

            if (checkDistance && distXZ > maxDistance) {
                continue;
            }
            double dot = (relX * lookDirection.x + relY * lookDirection.y + relZ * lookDirection.z) / distance;
            if (dot < -0.2) {
                continue;
            }

            //LOD
            if (distXZ < (double) Config.Generic.LOD1.getIntegerValue()) {
                newRenderFilledBox(matrices.last().pose(),
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                        camera, color);
            } else if (distXZ < Config.Generic.LOD2.getIntegerValue()) {
                addMediumDetailCube(matrices.last().pose(),  pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, camera, color);
            } else if (distXZ < Config.Generic.LOD2_HORIZON.getIntegerValue()) {
                addBillboardLOD(matrices.last().pose(),  pos.getX(), pos.getY(), pos.getZ(), camera, color, 1, 1);
            } else if (!Config.Generic.LOD2_HUD.getBooleanValue() && (Config.Generic.SELECTED_BLOCKS_MAX_DISTANCE.getIntegerValue() < 0 || distXZ < Config.Generic.SELECTED_BLOCKS_MAX_DISTANCE.getIntegerValue())) {
                addBillboardLOD(matrices.last().pose(),  pos.getX(), camera.y, pos.getZ(), camera, color, 2, 5);
            }
        }

    }

    private static Vec3 getLookDirection(Quaternionf orientation) {
        org.joml.Vector3f forward = new org.joml.Vector3f(0, 0, -1);
        orientation.transform(forward);
        return new Vec3(forward.x, forward.y, forward.z);
    }

    private static void addMediumDetailCube(Matrix4fc matrix,
                                            double x1, double y1, double z1, double x2, double y2, double z2,
                                            Vec3 camera, Color4f color) {
        float rx1 = (float)(x1 - camera.x);
        float ry1 = (float)(y1 - camera.y);
        float rz1 = (float)(z1 - camera.z);
        float rx2 = (float)(x2 - camera.x);
        float ry2 = (float)(y2 - camera.y);
        float rz2 = (float)(z2 - camera.z);

        buffer.addVertex(matrix, rx1, ry1, rz1).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(matrix, rx2, ry1, rz2).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(matrix, rx2, ry2, rz2).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(matrix, rx1, ry2, rz1).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(matrix, rx2, ry1, rz1).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(matrix, rx1, ry1, rz2).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(matrix, rx1, ry2, rz2).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(matrix, rx2, ry2, rz1).setColor(color.r, color.g, color.b, color.a);

    }


    private static void addBillboardLOD(Matrix4fc viewMatrix, double blockX, double blockY, double blockZ, Vec3 camera, Color4f color, float width, float height) {
        float cx = (float)(blockX + 0.5 - camera.x);
        float cy = (float)(blockY + 0.5 - camera.y);
        float cz = (float)(blockZ + 0.5 - camera.z);

        double lenSq = cx*cx + cy*cy + cz*cz;
        if (lenSq == 0) return;

        double invLen = 1.0 / Math.sqrt(lenSq);
        double nx = cx * invLen;
        double ny = cy * invLen;
        double nz = cz * invLen;

        double rightX = nz;
        double rightY = 0;
        double rightZ = -nx;

        double rLenSq = rightX*rightX + rightZ*rightZ;
        if (rLenSq < 1e-6) {
            rightX = 1; rightY = 0; rightZ = 0;
        } else {
            double invRLen = 1.0 / Math.sqrt(rLenSq);
            rightX *= invRLen;
            rightZ *= invRLen;
        }

        double upX = rightY * nz - rightZ * ny;
        double upY = rightZ * nx - rightX * nz;
        double upZ = rightX * ny - rightY * nx;

        float halfW = width * 0.5f;
        float halfH = height * 0.5f;


        float rW = (float)(rightX * halfW);
        float rH_up = (float)(upX * halfH);
        buffer.addVertex(viewMatrix, cx - rW - rH_up, cy - (float)(rightY * halfW) - (float)(upY * halfH), cz - (float)(rightZ * halfW) - (float)(upZ * halfH)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(viewMatrix, cx + rW - rH_up, cy + (float)(rightY * halfW) - (float)(upY * halfH), cz + (float)(rightZ * halfW) - (float)(upZ * halfH)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(viewMatrix, cx + rW + rH_up, cy + (float)(rightY * halfW) + (float)(upY * halfH), cz + (float)(rightZ * halfW) + (float)(upZ * halfH)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(viewMatrix, cx - rW + rH_up, cy - (float)(rightY * halfW) + (float)(upY * halfH), cz - (float)(rightZ * halfW) + (float)(upZ * halfH)).setColor(color.r, color.g, color.b, color.a);

    }

    private static void newRenderFilledBox(Matrix4fc positionMatrix, double x1, double y1, double z1, double x2, double y2, double z2, Vec3 camera, Color4f color) {

        //up
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y2 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y2 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y2 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y2 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);

        //down
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y1 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y1 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y1 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y1 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);

        //north
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y1 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y1 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y2 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y2 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);

        //south
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y1 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y1 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y2 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y2 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);

        //west
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y1 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y1 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y2 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y2 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);

        //east
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y1 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y1 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y2 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y2 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);



    }

    private static void filledPlane(Matrix4fc positionMatrix, double x1, double z1, double x2, double z2, double y, Vec3 camera, Color4f color){
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
    }


    public static void renderAll(Minecraft client, RenderPipeline pipeline, PoseStack matrices) {
        if (buffer == null) return;

        MeshData builtBuffer;
        try {
            builtBuffer = buffer.buildOrThrow();
        } catch (IllegalStateException e) {
            buffer = null;
            return;
        }

        if (builtBuffer.drawState().vertexCount() == 0) {
            builtBuffer.close();
            buffer = null;
            return;
        }

        GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "mod_vertex_buffer",
                GpuBuffer.USAGE_VERTEX,
                builtBuffer.vertexBuffer()
        );

        draw(client, pipeline, builtBuffer, vertexBuffer, matrices);

        builtBuffer.close();
        buffer = null;
    }

    private static void draw(Minecraft client, RenderPipeline pipeline, MeshData builtBuffer, GpuBuffer vertexBuffer, PoseStack matrices) {
        MeshData.DrawState drawParameters = builtBuffer.drawState();

        GpuBuffer indices;
        IndexType indexType;

        MeshData.SortState sortState = builtBuffer.sortQuads(sortAllocator, RenderSystem.getProjectionType().vertexSorting());

        if (sortState != null) {
            indices = RenderSystem.getDevice().createBuffer(
                    () -> "mod_index_buffer",
                    GpuBuffer.USAGE_INDEX,
                    builtBuffer.indexBuffer()
            );
            indexType = drawParameters.indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
            indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice[] transforms = RenderSystem.getDynamicUniforms().writeTransforms(
                new DynamicUniforms.Transform(
                        RenderSystem.getModelViewMatrixCopy(),
                        COLOR_MODULATOR,
                        MODEL_OFFSET,
                        TEXTURE_MATRIX
                )
        );

        // 4. Рендер-пасс
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "mod_render_pass",
                        client.gameRenderer.mainRenderTarget().getColorTextureView(),
                        Optional.empty(),
                        client.gameRenderer.mainRenderTarget().getDepthTextureView(),
                        OptionalDouble.empty()
                )) {

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setPipeline(pipeline);
            renderPass.setUniform("DynamicTransforms", transforms[0]);

            renderPass.setVertexBuffer(0, vertexBuffer.slice());
            renderPass.setIndexBuffer(indices, indexType);

            renderPass.drawIndexed(drawParameters.indexCount(), 1, 0, 0, 0);
        }

        vertexBuffer.close();
        if (sortState != null) {
            indices.close();
        }

    }

    public static void lookRandomSelectedBlock(){
        if(client.player == null) return;
        Random random = new Random();
        BlockPos pos = renderBlocksList.get(random.nextInt(renderBlocksList.size()));
        client.player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(pos.getX(), pos.getY(), pos.getZ()));
    }
    public static void clearRender(){
        renderBlocksList.clear();
        renderChunksList.clear();
    }

    
    public static void addAllRenderBlocks(HashSet<BlockPos> blocks) {
        if(Config.Generic.MAIN_RENDER.getBooleanValue()) renderBlocksList.addAll(blocks);
    }
    public static void addAllRenderChunks(HashSet<ChunkPos> chunkPos) {
        if(Config.Generic.MAIN_RENDER.getBooleanValue()) renderChunksList.addAll(chunkPos);
    }

}
