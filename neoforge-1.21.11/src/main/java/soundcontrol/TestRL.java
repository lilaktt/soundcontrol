package soundcontrol;
public class TestRL {
    public static void printClass() {
        System.out.println(net.minecraft.client.Minecraft.getInstance().getSoundManager().getAvailableSounds().iterator().next().getClass().getName());
    }
}