# Unified sources for all 17 modules

Every Fabric and NeoForge module shares one set of screens, renderers and config code. The shared code is written
once in `templates/` against the newest API (Minecraft 26.2, official names). `generate.py` rewrites it for each
module's Minecraft version and mappings, so all versions look and behave the same.

```
python tools/unify/generate.py              # all modules
python tools/unify/generate.py fabric-1.21  # one module
python tools/unify/generate.py --autotest   # test builds only: adds AutoTest (screenshots every screen, then quits)
```

Edit the templates, not the generated files in `*/src/main/java`; the next run overwrites them.

| Path | Contents |
|---|---|
| `templates/` | Shared classes: config, screens, renderers, anchor model |
| `compat/` | `Compat` (everything version-specific behind one small API) and `EntitySoundResolver`, one yarn and one official-names variant |
| `loader.py` | Entry points, mixins and accessors per loader / API level |
| `lang/` | The translation files copied into every module |
| `autotest.py` | Test-only screenshot hook |

`generate.py` also writes `soundcontrol.mixins.json`, the Forge 1.20.1 refmap, and the license and Minecraft version
range in `fabric.mod.json` / `mods.toml`.
