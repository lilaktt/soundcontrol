package soundcontrol;

import java.lang.ref.WeakReference;
import org.joml.Matrix4f;
import net.minecraft.world.phys.Vec3;

public final class AnchorRenderFrame {
    private static WeakReference<Object> world = new WeakReference<>(null);
    private static Frame pending;

    private AnchorRenderFrame() {}

    public record Frame(Matrix4f viewProjection, Vec3 position) {}

    public static void capture(Object currentWorld, Matrix4f view, Matrix4f projection, Vec3 position) {
        world = new WeakReference<>(currentWorld);
        pending = currentWorld == null ? null : new Frame(new Matrix4f(projection).mul(view), position);
    }

    public static Frame take(Object currentWorld) {
        Frame result = currentWorld != null && world.get() == currentWorld ? pending : null;
        pending = null;
        world.clear();
        return result;
    }
}
