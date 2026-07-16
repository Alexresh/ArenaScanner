package ru.obabok.client.util;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import fi.dy.masa.malilib.util.data.Color4f;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CommonColors;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import org.lwjgl.system.MemoryUtil;
import ru.obabok.client.Config;
import ru.obabok.client.Scan;
import ru.obabok.common.References;

import java.lang.Math;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static fi.dy.masa.malilib.render.RenderUtils.renderAreaOutline;
import static fi.dy.masa.malilib.render.RenderUtils.renderAreaSides;

public class RenderUtil {
    private static final List<BlockPos> renderBlocksList = new CopyOnWriteArrayList<>();
    private static final List<ChunkPos> renderChunksList = new CopyOnWriteArrayList<>();
    private static final Minecraft client = Minecraft.getInstance();

    private static final ByteBufferBuilder allocator = new ByteBufferBuilder(1024 * 1024 * 2); // 2 MB
    //private static final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE);
    private static BufferBuilder blockBuffer;
    private static MappableRingBuffer blockVertexBuffer;
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    //new
    private static final RenderPipeline BLOCKS_RENDER = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(References.MOD_ID, "pipeline/box"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build()
    );


    public static void renderAll(WorldRenderContext context) {
        if(!Config.Generic.MAIN_RENDER.getBooleanValue()) return;
        if(Minecraft.getInstance().options.hideGui) return;
        BlockBox scanRange = Scan.getRange();
        if(scanRange != null){
            renderAreaOutline(scanRange.min(), scanRange.max(), 2, Color4f.fromColor(CommonColors.RED), Color4f.fromColor(CommonColors.GREEN),Color4f.fromColor(CommonColors.BLUE));
            if(Config.Generic.AREA_EDGE_RENDER.getBooleanValue()){
                renderAreaSides(scanRange.min(), scanRange.max(), Config.Generic.AREA_EDGE_COLOR.getColor(), context.matrices().last().pose());
            }
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
                context.matrices().pushPose();

                blockBuffer = prepareRenderChunks(context, blockBuffer);

                blockBuffer = prepareRenderBlocksOptimized(context, blockBuffer);
                context.matrices().popPose();
                renderAll(Minecraft.getInstance(), BLOCKS_RENDER, context.matrices());
            }catch (Exception ignored){

            }

        }
    }


    private static BufferBuilder prepareRenderChunks(WorldRenderContext context, BufferBuilder buffer){
        if(renderChunksList.isEmpty()) return buffer;
        PoseStack matrices = context.matrices();
        Vec3 camera = context.worldState().cameraRenderState.pos;

        //matrices.pushPose();

        if (buffer == null) {
            buffer = new BufferBuilder(allocator, BLOCKS_RENDER.getVertexFormatMode(), BLOCKS_RENDER.getVertexFormat());
        }

        Color4f chunkColor = Config.Generic.UNLOADED_CHUNK_COLOR.getColor();
        int maxChunkDist = Config.Generic.UNLOADED_CHUNK_MAX_DISTANCE.getIntegerValue();
        int offset = Config.Generic.UNLOADED_CHUNK_Y_OFFSET.getIntegerValue();
        boolean checkChunkDist = maxChunkDist >= 0;
        int maxDistSq = maxChunkDist * maxChunkDist;

        for (int i = 0; i < renderChunksList.size(); i++) {
            ChunkPos pos = renderChunksList.get(i);
            double centerX = (pos.x << 4) + 8.5;
            double centerZ = (pos.z << 4) + 8.5;

            double relx = centerX - camera.x;
            double relz = centerZ - camera.z;
            if (checkChunkDist) {
                double distSq = relx * relx + relz * relz;
                if (distSq > maxDistSq) {
                    continue;
                }
            }
            filledPlane(matrices.last().pose(), buffer, pos.x << 4, pos.z << 4, (pos.x << 4) + 16, (pos.z << 4) + 16, camera.y + offset, camera, chunkColor);
        }
        //matrices.popPose();
        return buffer;
    }

    private static BufferBuilder prepareRenderBlocksOptimized(WorldRenderContext context, BufferBuilder buffer) {
        if(renderBlocksList.isEmpty()) return buffer;

        PoseStack matrices = context.matrices();
        Vec3 camera = context.worldState().cameraRenderState.pos;
        Quaternionf orientation = context.worldState().cameraRenderState.orientation;

        // Извлекаем направление взгляда из кватерниона
        Vec3 lookDirection = getLookDirection(orientation);

        matrices.pushPose();

        if (buffer == null) {
            buffer = new BufferBuilder(allocator, BLOCKS_RENDER.getVertexFormatMode(), BLOCKS_RENDER.getVertexFormat());
        }

        int maxDistance = Config.Generic.SELECTED_BLOCKS_MAX_DISTANCE.getIntegerValue();
        boolean checkDistance = maxDistance != -1;
        Color4f color = Color4f.fromColor(Config.Generic.SELECTED_BLOCKS_COLOR.getIntegerValue());

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
                newRenderFilledBox(matrices.last().pose(), buffer,
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                        camera, color);
            } else if (distXZ < Config.Generic.LOD2.getIntegerValue()) {
                addMediumDetailCube(matrices.last().pose(), buffer,  pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, camera, color);
            } else if (distXZ < Config.Generic.LOD2_HORIZON.getIntegerValue()) {
                addBillboardLOD(matrices.last().pose(), buffer,  pos.getX(), pos.getY(), pos.getZ(), camera, color, 1, 1);
            } else if (Config.Generic.SELECTED_BLOCKS_MAX_DISTANCE.getIntegerValue() < 0 || distXZ < Config.Generic.SELECTED_BLOCKS_MAX_DISTANCE.getIntegerValue()) {
                addBillboardLOD(matrices.last().pose(), buffer,  pos.getX(), camera.y, pos.getZ(), camera, color, 2, 5);
            }
        }

        matrices.popPose();
        return buffer;
    }

    private static Vec3 getLookDirection(Quaternionf orientation) {
        org.joml.Vector3f forward = new org.joml.Vector3f(0, 0, -1);
        orientation.transform(forward);
        return new Vec3(forward.x, forward.y, forward.z);
    }

    private static void addMediumDetailCube(Matrix4f matrix, BufferBuilder buffer,
                                            double x1, double y1, double z1, double x2, double y2, double z2,
                                            Vec3 camera, Color4f color) {
        float rx1 = (float)(x1 - camera.x);
        float ry1 = (float)(y1 - camera.y);
        float rz1 = (float)(z1 - camera.z);
        float rx2 = (float)(x2 - camera.x);
        float ry2 = (float)(y2 - camera.y);
        float rz2 = (float)(z2 - camera.z);

        addQuad(buffer, matrix, rx1, ry1, rz2, rx2, ry1, rz2, rx2, ry2, rz2, rx1, ry2, rz2, color);
        addQuad(buffer, matrix, rx2, ry1, rz1, rx1, ry1, rz1, rx1, ry2, rz1, rx2, ry2, rz1, color);
        addQuad(buffer, matrix, rx1, ry1, rz1, rx1, ry1, rz2, rx1, ry2, rz2, rx1, ry2, rz1, color);
        addQuad(buffer, matrix, rx2, ry1, rz2, rx2, ry1, rz1, rx2, ry2, rz1, rx2, ry2, rz2, color);
    }


    private static void addBillboardLOD(Matrix4f viewMatrix, BufferBuilder buffer, double blockX, double blockY, double blockZ, Vec3 camera, Color4f color, float width, float height) {

        double dx = blockX + 0.5 - camera.x;
        double dy = blockY + 0.5 - camera.y;
        double dz = blockZ + 0.5 - camera.z;

        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len == 0) return;

        double nx = dx / len;
        double ny = dy / len;
        double nz = dz / len;

        double upX, upY, upZ;
        if (Math.abs(ny) > 0.99) {
            upX = 0; upY = 0; upZ = 1;
        } else {
            upX = 0; upY = 1; upZ = 0;
        }

        double rightX = ny * upZ - nz * upY;
        double rightY = nz * upX - nx * upZ;
        double rightZ = nx * upY - ny * upX;

        double rLen = Math.sqrt(rightX*rightX + rightY*rightY + rightZ*rightZ);
        rightX /= rLen; rightY /= rLen; rightZ /= rLen;

        double trueUpX = rightY * nz - rightZ * ny;
        double trueUpY = rightZ * nx - rightX * nz;
        double trueUpZ = rightX * ny - rightY * nx;

        float cx = (float)(blockX + 0.5 - camera.x);
        float cy = (float)(blockY + 0.5 - camera.y);
        float cz = (float)(blockZ + 0.5 - camera.z);

        float halfW = width / 2.0f;
        float halfH = height / 2.0f;

        float p1x = cx - (float)(rightX * halfW) - (float)(trueUpX * halfH);
        float p1y = cy - (float)(rightY * halfW) - (float)(trueUpY * halfH);
        float p1z = cz - (float)(rightZ * halfW) - (float)(trueUpZ * halfH);

        float p2x = cx + (float)(rightX * halfW) - (float)(trueUpX * halfH);
        float p2y = cy + (float)(rightY * halfW) - (float)(trueUpY * halfH);
        float p2z = cz + (float)(rightZ * halfW) - (float)(trueUpZ * halfH);

        float p3x = cx + (float)(rightX * halfW) + (float)(trueUpX * halfH);
        float p3y = cy + (float)(rightY * halfW) + (float)(trueUpY * halfH);
        float p3z = cz + (float)(rightZ * halfW) + (float)(trueUpZ * halfH);

        float p4x = cx - (float)(rightX * halfW) + (float)(trueUpX * halfH);
        float p4y = cy - (float)(rightY * halfW) + (float)(trueUpY * halfH);
        float p4z = cz - (float)(rightZ * halfW) + (float)(trueUpZ * halfH);

        addQuad(buffer, viewMatrix, p1x, p1y, p1z, p2x, p2y, p2z, p3x, p3y, p3z, p4x, p4y, p4z, color);
    }


    private static void addQuad(BufferBuilder buffer, Matrix4f matrix,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float x3, float y3, float z3, float x4, float y4, float z4, Color4f color) {
        buffer.addVertex(matrix, x1, y1, z1).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(matrix, x2, y2, z2).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(matrix, x3, y3, z3).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(matrix, x4, y4, z4).setColor(color.r, color.g, color.b, color.a);
    }


    private static void newRenderFilledBox(Matrix4f positionMatrix, BufferBuilder buffer, double x1, double y1, double z1, double x2, double y2, double z2, Vec3 camera, Color4f color) {
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y1 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y1 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y2 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y2 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);

        // Back face
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y1 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y1 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y2 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y2 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);

        // Left face
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y1 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y1 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y2 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y2 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);

        // Right face
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y1 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y1 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y2 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y2 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);

        // Top face
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y2 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y2 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y2 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y2 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);

        // Bottom face
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y1 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y1 - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y1 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y1 - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);

    }

    private static void filledPlane(Matrix4f positionMatrix, BufferBuilder buffer, double x1, double z1, double x2, double z2, double y, Vec3 camera, Color4f color){
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y - camera.y), (float)(z2 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x2 - camera.x), (float)(y - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, (float)(x1 - camera.x), (float)(y - camera.y), (float)(z1 - camera.z)).setColor(color.r, color.g, color.b, color.a);
    }


    public static void renderAll(Minecraft client, RenderPipeline pipeline, PoseStack matrices) {
        if(blockBuffer == null) return;
        MeshData builtBuffer;
        try {
            builtBuffer = blockBuffer.buildOrThrow();
        }catch (IllegalStateException e){
            blockBuffer = null;
            return;
        }
        MeshData.DrawState drawParameters = builtBuffer.drawState();
        VertexFormat format = drawParameters.format();

        blockVertexBuffer = upload(drawParameters, format, builtBuffer, blockVertexBuffer);
        GpuBuffer vertices = blockVertexBuffer.currentBuffer();
        draw(client, pipeline, builtBuffer, drawParameters, vertices, format, matrices);

        blockVertexBuffer.rotate();
        blockBuffer = null;
    }

    private static MappableRingBuffer upload(MeshData.DrawState drawParameters, VertexFormat format, MeshData builtBuffer, MappableRingBuffer vertexBuffer) {
        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }

            vertexBuffer = new MappableRingBuffer(() -> References.MOD_ID + " pipeline", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();

        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(vertexBuffer.currentBuffer(), false, true)) {
            ByteBuffer targetBuffer = mappedView.data();
            ByteBuffer sourceBuffer = builtBuffer.vertexBuffer();
            long bytesToCopy = sourceBuffer.remaining();

            if (bytesToCopy > 0) {
                MemoryUtil.memCopy(
                        MemoryUtil.memAddress(sourceBuffer),
                        MemoryUtil.memAddress(targetBuffer),
                        bytesToCopy
                );
            }
        }

        return vertexBuffer;
    }

    private static void draw(Minecraft client, RenderPipeline pipeline, MeshData builtBuffer, MeshData.DrawState drawParameters, GpuBuffer vertices, VertexFormat format, PoseStack matrices) {
        GpuBuffer indices;
        VertexFormat.IndexType indexType;

        if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            builtBuffer.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting());
            indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.indexBuffer());
            indexType = builtBuffer.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
            indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX, 1f);
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> References.MOD_ID + " example render pipeline rendering", client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(), client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(pipeline);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);


            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);

            renderPass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
        }

        builtBuffer.close();
    }

    public static void lookRandomSelectedBlock(){
        if(client.player == null) return;
        Random random = new Random();
        Vec3 pos = renderBlocksList.get(random.nextInt(renderBlocksList.size())).getCenter();
        client.player.lookAt(EntityAnchorArgument.Anchor.EYES, pos);
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
