"""Additional cross-version anchor fixes: legacy playback, translations, safe persistence."""
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[2]

def end(s,a):
 b=s.index('{',a);d=1
 for i in range(b+1,len(s)):
  d+=(s[i]=='{')-(s[i]=='}')
  if d==0:return i+1
 raise ValueError()

for port in sorted(ROOT.iterdir()):
 if not port.is_dir() or not port.name.startswith(('fabric-','neoforge-')):continue
 java=port/'src/main/java/soundcontrol'
 if port.name.endswith('1.20.1'):
  yarn=port.name.startswith('fabric')
  p=java/'mixin/SoundEngineMixin.java';s=p.read_text(encoding='utf-8')
  if 'soundcontrol$initialAnchorVolume' not in s:
   engine='SoundSystem' if yarn else 'SoundEngine'
   calc='getAdjustedVolume' if yarn else 'calculateVolume'
   category='SoundCategory' if yarn else 'SoundSource'
   owner='net/minecraft/client/sound/SoundSystem' if yarn else 'net/minecraft/client/sounds/SoundEngine'
   cat='net/minecraft/sound/SoundCategory' if yarn else 'net/minecraft/sounds/SoundSource'
   method='"play"' if yarn else '{"play", "m_120312_"}'
   # Redirect the actual play overload (float, category); the instance overload is used for later ticks.
   target=f'L{owner};{calc}(FL{cat};)F'
   alias='' if yarn else '(aliases = {"m_235257_"})'
   extra=f'''
    @org.spongepowered.asm.mixin.Shadow{alias}
    protected abstract float {calc}(float volume, {category} category);

    @org.spongepowered.asm.mixin.injection.Redirect(method = {method},
            at = @At(value = "INVOKE", target = "{target}"))
    private float soundcontrol$initialAnchorVolume({engine} engine, float volume, {category} category, SoundInstance sound) {{
        return {calc}(volume, category) * soundcontrol$anchorVolume(sound);
    }}
'''
   s=s[:s.rfind('}')]+extra+'}\n';p.write_text(s,encoding='utf-8')
  if not yarn:
   p=port/'src/main/resources/soundcontrol.refmap.json';data=json.loads(p.read_text())
   mapped={
    'Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F':'Lnet/minecraft/client/sounds/SoundEngine;m_235257_(FLnet/minecraft/sounds/SoundSource;)F',
    'calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F':'m_120327_(Lnet/minecraft/client/resources/sounds/SoundInstance;)F',
    'calculateVolume(FLnet/minecraft/sounds/SoundSource;)F':'m_235257_(FLnet/minecraft/sounds/SoundSource;)F',
    'instanceToChannel:Ljava/util/Map;':'f_120226_:Ljava/util/Map;',
    'tick(Z)V':'m_120302_(Z)V',
    'play':'m_120312_(Lnet/minecraft/client/resources/sounds/SoundInstance;)V',
   }
   for table in [data['mappings'],data['data']['named:srg']]:table.setdefault('soundcontrol/mixin/SoundEngineMixin',{}).update(mapped)
   p.write_text(json.dumps(data,indent=2)+'\n')
 p=java/'SoundConfig.java';s=p.read_text(encoding='utf-8')
 a=s.index('    public static void saveSettings()');b=end(s,a)
 method='''    public static void saveSettings() {
        SC_DIR.mkdirs();
        java.nio.file.Path destination = SETTINGS_FILE.toPath();
        java.nio.file.Path temporary = null;
        try {
            temporary = java.nio.file.Files.createTempFile(SC_DIR.toPath(), "settings-", ".tmp");
            try (java.io.Writer writer = java.nio.file.Files.newBufferedWriter(temporary, java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(SETTINGS, writer);
            }
            try {
                java.nio.file.Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                java.nio.file.Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.error("Failed to save soundcontrol settings", exception);
        } finally {
            if (temporary != null) {
                try { java.nio.file.Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            }
        }
    }'''
 s=s[:a]+method+s[b:]
 s=s.replace('FileReader r = new FileReader(SETTINGS_FILE)', 'java.io.Reader r = java.nio.file.Files.newBufferedReader(SETTINGS_FILE.toPath(), java.nio.charset.StandardCharsets.UTF_8)')
 p.write_text(s,encoding='utf-8')
 # Carry all existing translations from 26.1; don't overwrite unrelated translations.
 lang=port/'src/main/resources/assets/soundcontrol/lang'
 for p in lang.glob('*.json'):
  source=ROOT/'build/anchor-port-backup/fabric-26.1/src/main/resources/assets/soundcontrol/lang'/p.name
  if not source.exists():continue
  old=json.loads(source.read_text(encoding='utf-8-sig'));data=json.loads(p.read_text(encoding='utf-8-sig'))
  for k,v in old.items():
   if k.startswith('text.soundcontrol.anchors.') or k.startswith('text.soundcontrol.recent.pick'):data[k]=v
  additions={
   'en_us': {'edit_title':'Edit anchor: %s','search':'Search sounds','remove':'Remove override','all_mods':'All namespaces'},
   'uk_ua': {'edit_title':'Редагування якоря: %s','search':'Пошук звуків','remove':'Прибрати перевизначення','all_mods':'Усі простори імен'},
   'ru_ru': {'edit_title':'Редактирование якоря: %s','search':'Поиск звуков','remove':'Удалить переопределение','all_mods':'Все пространства имён'},
  }
  for k,v in additions.get(p.stem,additions['en_us']).items():data['text.soundcontrol.anchors.'+k]=v
  p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 print('Completed persistence/localization:',port.name)
