package soundcontrol.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import soundcontrol.SoundWorldRenderer;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onRenderLevelBegin(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                   boolean bl, CameraRenderState cameraRenderState,
                                   Matrix4fc projectionMatrix, GpuBufferSlice bufferSlice,
                                   Vector4f fogColor, boolean bl2,
                                   ChunkSectionsToRender chunks, CallbackInfo ci) {
        SoundWorldRenderer.updateMatrices(cameraRenderState, projectionMatrix);
    }
}
