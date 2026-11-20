package soundcontrol;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("soundcontrol")
public class SoundControl {
    public SoundControl() {
        if (FMLEnvironment.dist == Dist.CLIENT) SoundControlClient.init();
    }
}
