"""Compile actual AnchorWorldContext adapters with client fixtures and test identity/lifecycle.
Uses temporary world directories under build/, never touches actual saves.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/anchor-world-context-tests'

CLIENT = '''package net.minecraft.client;
import java.nio.file.Path;
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public static Minecraft getInstance() { return INSTANCE; }
    public Object level;
    public LocalServer server;
    public RemoteServer remote;
    public LocalServer getSingleplayerServer() { return server; }
    public RemoteServer getCurrentServer() { return remote; }
    public static class LocalServer {
        public Path root; public int pathQueries;
        public String displayName = "Same world name";
        public LocalServer(Path root) { this.root = root; }
        public Path getWorldPath(net.minecraft.world.level.storage.LevelResource resource) {
            pathQueries++; return root;
        }
    }
    public static class RemoteServer {
        public String ip;
        public String displayName = "Same server name";
        public RemoteServer(String ip) { this.ip = ip; }
    }
}
'''
TEST = '''package soundcontrol;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
public class WorldContextTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Minecraft client = Minecraft.getInstance();
        Path root = Path.of(args[0]);
        Path a = Files.createDirectories(root.resolve("world-A"));
        Path b = Files.createDirectories(root.resolve("world-B"));
        // Disconnected, even if the old integrated server reference lingers.
        client.server = new Minecraft.LocalServer(a);
        check(AnchorWorldContext.currentKey() == null, "no identity outside world");
        client.level = new Object();
        String keyA = AnchorWorldContext.currentKey();
        check(keyA.startsWith("singleplayer:"), "typed local key");
        check(keyA.equals(AnchorWorldContext.localKey(a.resolve("."))), "normalized save path");
        check(keyA.equals(AnchorWorldContext.localKey(a.resolve("../world-A"))), "same canonical folder");
        for (int i=0; i<100; i++) check(keyA.equals(AnchorWorldContext.currentKey()), "stable cached identity");
        check(client.server.pathQueries == 1, "no filesystem work for each audio/render query");
        // Changing dimensions replaces the client level, not the integrated server/save.
        client.level = new Object();
        check(keyA.equals(AnchorWorldContext.currentKey()), "dimension change keeps world list");
        client.server.displayName = "Renamed in menu";
        check(keyA.equals(AnchorWorldContext.currentKey()), "display rename does not lose anchors");
        client.server = new Minecraft.LocalServer(b);
        String keyB = AnchorWorldContext.currentKey();
        check(!keyA.equals(keyB), "different folders with same display name separated");
        // Handle rapid world switch even if a caller did not observe the title screen.
        client.server = new Minecraft.LocalServer(a);
        check(keyA.equals(AnchorWorldContext.currentKey()), "A-B-A restores exact key");
        client.level = null;
        check(AnchorWorldContext.currentKey() == null, "disconnect clears cached context");
        client.level = new Object(); client.server = null;
        check(AnchorWorldContext.currentKey() == null, "unknown connection fails closed");
        client.remote = new Minecraft.RemoteServer(" Example.ORG:25565 ");
        String remote = AnchorWorldContext.currentKey();
        check(remote.equals("multiplayer:example.org"), "normalized server address");
        client.remote = new Minecraft.RemoteServer("example.org");
        check(remote.equals(AnchorWorldContext.currentKey()), "default port spelling is equivalent");
        client.remote.displayName = "My renamed server";
        check(remote.equals(AnchorWorldContext.currentKey()), "server label irrelevant");
        client.remote = new Minecraft.RemoteServer("example.org:25566");
        check(!remote.equals(AnchorWorldContext.currentKey()), "nondefault ports separate worlds");
        client.remote = new Minecraft.RemoteServer("other.org");
        check(!remote.equals(AnchorWorldContext.currentKey()), "different servers separated");
        check(AnchorWorldContext.serverKey("[::1]:25565").equals(AnchorWorldContext.serverKey("[::1]")), "IPv6 default port");
        check(!AnchorWorldContext.serverKey("[::1]:25566").equals(AnchorWorldContext.serverKey("[::1]")), "IPv6 distinct port");
        check(AnchorWorldContext.serverKey("2001:db8::25565").equals("multiplayer:2001:db8::25565"), "bare IPv6 tail not mistaken for port");
        check(AnchorWorldContext.serverKey(null) == null && AnchorWorldContext.serverKey(" ") == null, "empty server identity rejected");
        // Local host/open-to-LAN still uses its save path even if server data is present.
        client.server = new Minecraft.LocalServer(a);
        check(keyA.equals(AnchorWorldContext.currentKey()), "integrated host takes priority");
        client.level = null;
        check(AnchorWorldContext.currentKey() == null, "final disconnect");
        System.out.println("PASS save identity, A-B-A, cache, dimensions, display rename, server addresses, IPv6, disconnect");
    }
}
'''

paths = sorted(ROOT.glob('*/src/main/java/soundcontrol/AnchorWorldContext.java'))
assert len(paths) == 17
canonical = None
for path in paths:
    name = path.relative_to(ROOT).parts[0]
    out = OUT / name
    src = out / 'src'
    src.mkdir(parents=True, exist_ok=True)
    yarn = name.startswith('fabric-') and '-26.' not in name
    source = path.read_text(encoding='utf-8')
    normalized = source.replace('MinecraftClient', 'Minecraft').replace('net.minecraft.util.WorldSavePath', 'net.minecraft.world.level.storage.LevelResource').replace('WorldSavePath.ROOT', 'LevelResource.ROOT')
    normalized = normalized.replace('client.world', 'client.level').replace('getServer()', 'getSingleplayerServer()').replace('getSavePath(', 'getWorldPath(').replace('getCurrentServerEntry()', 'getCurrentServer()').replace('remote.address', 'remote.ip')
    if canonical is None: canonical = normalized
    assert canonical == normalized, (name, 'adapter drift')
    client, test = CLIENT, TEST
    resource = 'package net.minecraft.world.level.storage; public class LevelResource { public static final LevelResource ROOT = new LevelResource(); }'
    if yarn:
        client = client.replace('Minecraft', 'MinecraftClient').replace('Object level', 'Object world').replace('getSingleplayerServer', 'getServer').replace('getCurrentServer', 'getCurrentServerEntry').replace('getWorldPath', 'getSavePath').replace('net.minecraft.world.level.storage.LevelResource', 'net.minecraft.util.WorldSavePath').replace('String ip', 'String address').replace('this.ip = ip', 'this.address = address')
        test = test.replace('Minecraft', 'MinecraftClient').replace('client.level', 'client.world')
        resource = 'package net.minecraft.util; public class WorldSavePath { public static final WorldSavePath ROOT = new WorldSavePath(); }'
    files = {'AnchorWorldContext.java': source, 'WorldContextTest.java': test,
             ('MinecraftClient.java' if yarn else 'Minecraft.java'): client,
             ('WorldSavePath.java' if yarn else 'LevelResource.java'): resource}
    for file, content in files.items(): (src / file).write_text(content, encoding='utf-8')
    classes = out / 'classes'; classes.mkdir(exist_ok=True)
    subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-d', str(classes), *map(str, src.glob('*.java'))], check=True)
    print(name, flush=True)
    subprocess.run(['java', '-cp', str(classes), 'soundcontrol.WorldContextTest', str(out / 'saves')], check=True)
