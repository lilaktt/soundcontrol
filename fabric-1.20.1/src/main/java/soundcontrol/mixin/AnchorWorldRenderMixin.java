package soundcontrol.mixin;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.Camera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import soundcontrol.AnchorRenderFrame;

@Mixin(WorldRenderer.class)
public class AnchorWorldRenderMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void soundcontrol$captureAnchorFrame(MatrixStack matrices, float delta, long limit, boolean outline,
            Camera camera, GameRenderer renderer, LightmapTextureManager light, Matrix4f projection, CallbackInfo ci) {
        AnchorRenderFrame.capture(MinecraftClient.getInstance().world, matrices.peek().getPositionMatrix(), projection, camera.getPos());
    }
}
