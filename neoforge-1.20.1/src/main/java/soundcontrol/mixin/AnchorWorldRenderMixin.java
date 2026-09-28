package soundcontrol.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import soundcontrol.AnchorRenderFrame;

@Mixin(LevelRenderer.class)
public class AnchorWorldRenderMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void soundcontrol$captureAnchorFrame(PoseStack matrices, float delta, long limit, boolean outline,
            Camera camera, GameRenderer renderer, LightTexture light, Matrix4f projection, CallbackInfo ci) {
        AnchorRenderFrame.capture(Minecraft.getInstance().level, matrices.last().pose(), projection, camera.getPosition());
    }
}
