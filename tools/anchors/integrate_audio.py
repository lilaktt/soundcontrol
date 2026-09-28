"""Idempotent volume integration for anchors, preserving existing playback tracking."""
from pathlib import Path
import re,json
ROOT=Path(__file__).resolve().parents[2]

def end(s,a):
 b=s.index('{',a);d=1
 for i in range(b+1,len(s)):
  d+=(s[i]=='{')-(s[i]=='}')
  if d==0:return i+1
 raise ValueError()

for port in sorted(ROOT.iterdir()):
 if not port.is_dir() or not port.name.startswith(('fabric-','neoforge-')):continue
 yarn=port.name.startswith('fabric-') and '-26.' not in port.name
 legacy=port.name.endswith('1.20.1');forgeLegacy=port.name=='neoforge-1.20.1'
 java=port/'src/main/java/soundcontrol'
 p=java/'mixin/SoundEngineMixin.java';s=p.read_text(encoding='utf-8')
 if 'soundcontrol$anchorVolume' in s:continue
 getid='getId' if yarn else ('getIdentifier' if ('-26.' in port.name or port.name == 'neoforge-1.21.11') else 'getLocation')
 soundtype='net.minecraft.client.sound.SoundInstance' if yarn else 'net.minecraft.client.resources.sounds.SoundInstance'
 clienttype='net.minecraft.client.MinecraftClient' if yarn else 'net.minecraft.client.Minecraft'
 level='world' if yarn else 'level'
 dim='client.world.getRegistryKey().getValue().toString()' if yarn else 'client.level.dimension().toString()'
 calc='getAdjustedVolume' if yarn else 'calculateVolume'
 channeltype='net.minecraft.client.sound.Channel.SourceManager' if yarn else 'net.minecraft.client.sounds.ChannelAccess.ChannelHandle'
 field='sources' if yarn else 'instanceToChannel'
 run='run' if yarn else 'execute'
 if not legacy:
  match=re.search(r'    private float \w+\([^\n]+Operation<Float> original, SoundInstance sound\) \{',s)
  assert match,p
  s=s[:match.end()]+'''\n        return original.call(instance, volume, category) * soundcontrol$anchorVolume(sound);
    }'''+s[end(s,match.start()):]
 else:
  s=s.replace('float modifier = SoundConfig.getVolumeModifier(id);', 'float modifier = soundcontrol$anchorVolume(sound);')
 additions=f'''
    @org.spongepowered.asm.mixin.Unique
    private static float soundcontrol$anchorVolume(SoundInstance sound) {{
        String id = sound.{getid}().toString();
        float profile = SoundConfig.getVolumeModifier(id);
        // A zone must never undo an explicit profile/global mute. UI/relative sounds have no world origin.
        if (profile <= 0f || sound.isRelative()) return profile;
        var client = {clienttype}.getInstance();
        if (client.{level} == null) return profile;
        float anchor = SoundConfig.getAnchorVolumeModifier(id, {dim}, sound.getX(), sound.getY(), sound.getZ());
        return anchor >= 0f ? anchor : profile;
    }}
'''
 if not legacy:
  additions+=f'''
    @org.spongepowered.asm.mixin.injection.Inject(method = "{calc}(L{soundtype.replace('.', '/')};)F",
            at = @At("RETURN"), cancellable = true)
    private void soundcontrol$updateVolume(SoundInstance sound,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {{
        cir.setReturnValue(cir.getReturnValue() * soundcontrol$anchorVolume(sound));
    }}
'''
 # Recompute already playing static and moving sources, not only newly started sounds.
 aliasfield='(aliases = {"f_120226_"})' if forgeLegacy else ''
 aliasmethod='(aliases = {"m_120327_"})' if forgeLegacy else ''
 tick='{"tick(Z)V", "m_120302_(Z)V"}' if forgeLegacy else '"tick(Z)V"'
 additions+=f'''
    @org.spongepowered.asm.mixin.Shadow{aliasfield}
    @org.spongepowered.asm.mixin.Final
    private java.util.Map<SoundInstance, {channeltype}> {field};

    @org.spongepowered.asm.mixin.Shadow{aliasmethod}
    protected abstract float {calc}(SoundInstance sound);

    @org.spongepowered.asm.mixin.Unique
    private boolean soundcontrol$hadAnchors;

    @org.spongepowered.asm.mixin.injection.Inject(method = {tick}, at = @At("TAIL"))
    private void soundcontrol$refreshAnchors(boolean paused, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {{
        if (paused) return;
        boolean hasAnchors = !SoundConfig.getAnchors().isEmpty();
        if (!hasAnchors && !soundcontrol$hadAnchors) return;
        soundcontrol$hadAnchors = hasAnchors;
        for (var entry : {field}.entrySet()) {{
            if (entry.getKey().isRelative()) continue;
            float volume = {calc}(entry.getKey());
            entry.getValue().{run}(channel -> channel.setVolume(volume));
        }}
    }}
'''
 s=s.replace('public class SoundEngineMixin', 'public abstract class SoundEngineMixin')
 s=s[:s.rfind('}')]+additions+'}\n'
 p.write_text(s,encoding='utf-8')
 # Anchor-mutated sounds still need to be recorded for discovery. Remove radar early-outs.
 p=next(java.rglob('SoundWorldRenderer.java'));s=p.read_text(encoding='utf-8')
 s=re.sub(r'\n    Minecraft client = Minecraft.getInstance\(\);\n    if \(client.player != null\) \{\n      String dim = .*?if \(anchorMod == 0.0f\) return;\n    }\n', '\n',s,flags=re.S)
 p.write_text(s,encoding='utf-8')
 print('Audio integration',port.name)
