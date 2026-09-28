"""Generate anchor sources from shared templates and apply narrow integration patches.
Run from anywhere. Review changes and compile all platforms after changing adapters.
"""
from pathlib import Path
import re,json
ROOT=Path(__file__).resolve().parents[2]
T=Path(__file__).parent/'templates'
PORTS=sorted(p for p in ROOT.iterdir() if p.is_dir() and p.name.startswith(('fabric-','neoforge-')))

def body_end(s,start):
    a=s.index('{',start); depth=1
    for i in range(a+1,len(s)):
        depth+=(s[i]=='{')-(s[i]=='}')
        if depth==0:return i+1
    raise ValueError('Unclosed body')

def replace_method(s,signature,new):
    a=s.index(signature);return s[:a]+new+s[body_end(s,a):]

def write(p,s):
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,encoding='utf-8',newline='\n')

def convert(s,port,cls):
    fab=port.name.startswith('fabric-');yarn=fab and '-26.' not in port.name
    modern='-26.' in port.name
    oldentry=port.name in ['fabric-1.20.1','fabric-1.21','fabric-1.21.3','fabric-1.21.4','fabric-1.21.6','neoforge-1.20.1','neoforge-1.21.1']
    legacy=port.name.endswith('-1.20.1')
    s=s.replace('import org.lwjgl.glfw.GLFW;\n', '')
    if port.name.endswith(('-26.2','-26.3')): s=s.replace('.setScreen(', '.setScreenAndShow(')
    if not fab:
        s=s.replace('package soundcontrol.anchor;', 'package soundcontrol;').replace('package soundcontrol.gui;', 'package soundcontrol;')
        s=s.replace('soundcontrol.anchor.', 'soundcontrol.').replace('soundcontrol.gui.', 'soundcontrol.')
    if not modern:
        s=s.replace('GuiGraphicsExtractor','GuiGraphics').replace('extractRenderState','render')
        s=s.replace('context.centeredText(', 'context.drawCenteredString(')
        # Both no-shadow and explicit-shadow overloads exist in Mojang GUI.
        s=s.replace('context.text(', 'context.drawString(')
        if oldentry:
            s=s.replace('public void extractContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float tickDelta)',
                        'public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta)')
            s=s.replace('            int x = this.getX(); int y = this.getY();\n','').replace('            int x = this.getX();\n            int y = this.getY();\n','')
        else:s=s.replace('extractContent','renderContent')
        if legacy:
            s=s.replace('super(client, width, height, y, itemHeight);','super(client, width, height, y, y + height, itemHeight);')
    if cls=='SoundAnchorRenderer':
        world=next((port/'src/main/java/soundcontrol').rglob('SoundWorldRenderer.java')).read_text(encoding='utf-8')
        start=world.index('Camera camera =',world.index('public static void render('))
        end=world.index('int screenWidth',start)
        camera=world[start:end].strip()
        if port.name == 'fabric-1.21.9': camera=camera.replace('camera.getPos()', 'camera.getCameraPos()')
        if legacy:
            level='client.world' if yarn else 'client.level'
            vec='Vec3d' if yarn else 'Vec3'
            camera=f'var frame = soundcontrol.AnchorRenderFrame.take({level});\n        if (frame == null) return;\n        {vec} camPos = frame.position();\n        Matrix4f viewProjMatrix = frame.viewProjection();'
        # Reuse the already compiling projection implementation for each game version.
        matrix='projectionMatrix' if 'Matrix4f projectionMatrix' in camera else 'viewProjMatrix'
        camera=camera.replace(matrix,'anchorProjection')
        if matrix=='viewProjMatrix':camera=camera.replace('projMatrix','projection')
        camera=camera.replace('anchorProjection','projMatrix')
        a=s.index('Camera camera =');b=s.index('Font font =',a)
        s=s[:a]+camera+'\n        '+s[b:]
        if 'Quaternionf' in camera:s=s.replace('import org.joml.Matrix4f;', 'import org.joml.Matrix4f;\nimport org.joml.Quaternionf;')
    if cls in ['SoundAnchorScreen','SoundAnchorEditScreen','AllSoundsPickerScreen']:
        scroll='scrollBarX' if modern or not oldentry and not yarn else ('getScrollbarPositionX' if yarn and legacy else 'getScrollbarX' if yarn else 'getScrollbarPosition')
        if port.name=='neoforge-1.21.1': scroll='getScrollbarPosition'
        rowWidth = 248 if cls == 'SoundAnchorScreen' else 360
        row=f'@Override public int getRowWidth() {{ return Math.min({rowWidth}, this.width - 24); }}'
        if cls == 'SoundAnchorScreen':
            # Center the compact content plus the 4px gap and 6px scrollbar as one unit.
            s=s.replace(row,row+f'\n        @Override public int getRowLeft() {{ return (this.width - getRowWidth() - 10) / 2; }}\n        @Override protected int {scroll}() {{ return getRowLeft() + getRowWidth() + 4; }}')
        else:
            s=s.replace(row,row+f'\n        @Override protected int {scroll}() {{ return this.width / 2 + getRowWidth() / 2 + 4; }}')
        if legacy:
            listField='anchorList' if cls == 'SoundAnchorScreen' else 'soundList'
            s=s.replace('super.render(context, mouseX, mouseY, delta);', f'context.fill(0, 0, this.width, this.height, 0xC0101010);\n        this.{listField}.drawPanelBackground(context);\n        super.render(context, mouseX, mouseY, delta);')
            listClass={'SoundAnchorScreen':'AnchorListWidget', 'SoundAnchorEditScreen':'AnchorSoundList', 'AllSoundsPickerScreen':'SoundPickerList'}[cls]
            s=re.sub(r'(private static class '+listClass+r' extends [^\n]+\{)', r'\1\n        private int panelTop, panelBottom;', s)
            s=s.replace('super(client, width, height, y, y + height, itemHeight);', 'super(client, width, height, y, y + height, itemHeight); this.panelTop = y; this.panelBottom = y + height;')
            s=s.replace('@Override public int getRowWidth()', 'public void drawPanelBackground(GuiGraphics context) { context.fill(0, panelTop, this.width, panelBottom, 0x80000000); }\n        @Override public int getRowWidth()')
            top='setRenderHorizontalShadows' if yarn else 'setRenderTopAndBottom'
            s=s.replace('super(client, width, height, y, y + height, itemHeight);', 'super(client, width, height, y, y + height, itemHeight); this.setRenderBackground(false); this.'+top+'(false);')
    if cls == 'SoundAnchor': return s
    if yarn:
        replacements={
            'net.minecraft.client.Minecraft':'net.minecraft.client.MinecraftClient',
            'net.minecraft.client.gui.GuiGraphics':'net.minecraft.client.gui.DrawContext',
            'net.minecraft.client.gui.components.Button':'net.minecraft.client.gui.widget.ButtonWidget',
            'net.minecraft.client.gui.components.EditBox':'net.minecraft.client.gui.widget.TextFieldWidget',
            'net.minecraft.client.gui.components.AbstractSliderButton':'net.minecraft.client.gui.widget.SliderWidget',
            'net.minecraft.client.gui.components.ContainerObjectSelectionList':'net.minecraft.client.gui.widget.ElementListWidget',
            'net.minecraft.client.gui.components.Tooltip':'net.minecraft.client.gui.tooltip.Tooltip',
            'net.minecraft.client.gui.components.events.GuiEventListener':'net.minecraft.client.gui.Element',
            'net.minecraft.client.gui.narration.NarratableEntry':'net.minecraft.client.gui.Selectable',
            'net.minecraft.client.gui.screens.Screen':'net.minecraft.client.gui.screen.Screen',
            'net.minecraft.network.chat.CommonComponents':'net.minecraft.screen.ScreenTexts',
            'net.minecraft.network.chat.Component':'net.minecraft.text.Text',
            'net.minecraft.client.Camera':'net.minecraft.client.render.Camera',
            'net.minecraft.client.gui.Font':'net.minecraft.client.font.TextRenderer',
            'net.minecraft.world.phys.Vec3':'net.minecraft.util.math.Vec3d',
        }
        for a,b in replacements.items():s=s.replace(a,b)
        for a,b in {'Minecraft':'MinecraftClient','GuiGraphics':'DrawContext','Button':'ButtonWidget','EditBox':'TextFieldWidget',
                    'AbstractSliderButton':'SliderWidget','ContainerObjectSelectionList':'ElementListWidget',
                    'Component':'Text','CommonComponents':'ScreenTexts','Font':'TextRenderer','Vec3':'Vec3d'}.items():
            s=re.sub(r'\b'+a+r'\b',b,s)
        s=s.replace('.minecraft','.client').replace('.font','.textRenderer')
        # Package replacement above must not rewrite net.minecraft or client.font package names.
        s=s.replace('net.client','net.minecraft').replace('client.textRenderer.TextRenderer','client.font.TextRenderer')
        s=s.replace('.bounds(','.dimensions(').replace('addRenderableWidget','addDrawableChild')
        s=s.replace('onClose()', 'close()').replace('isPauseScreen()', 'shouldPause()')
        s=s.replace('.setValue(','.setText(').replace('searchBox.getValue()', 'searchBox.getText()').replace('setResponder','setChangedListener')
        s=s.replace('getFov().getText()', 'getFov().getValue()')
        s=s.replace('updateMessage()', 'updateMessage()').replace('applyValue()', 'applyValue()')
        s=s.replace('narratables()', 'selectableChildren()').replace('renderContent','render')
        s=s.replace('.getAvailableSounds()', '.getKeys()')
        s=s.replace('ScreenTexts.GUI_DONE','ScreenTexts.DONE').replace('Tooltip.create(', 'Tooltip.of(')
        s=s.replace('context.guiWidth()', 'context.getScaledWindowWidth()').replace('context.guiHeight()', 'context.getScaledWindowHeight()')
        s=s.replace('context.drawCenteredString(', 'context.drawCenteredTextWithShadow(')
        # Yarn drawText requires an explicit shadow flag.
        s=s.replace('context.drawString(', 'context.drawText(')
        lines=[]
        for line in s.splitlines():
            if 'context.drawText(' in line and line.rstrip().endswith(');') and not re.search(r', (true|false)\);$',line):
                line=line[:-2]+', true);'
            lines.append(line)
        s='\n'.join(lines)+'\n'
        s=s.replace('font.width(', 'font.getWidth(').replace('font.plainSubstrByWidth(', 'font.trimToWidth(').replace('font.lineHeight','font.fontHeight')
        s=s.replace('player.level().dimension().toString()', 'player.getWorld().getRegistryKey().getValue().toString()')
        if port.name=='fabric-1.21.9':s=s.replace('player.getWorld()', 'player.getEntityWorld()')
        # New entry geometry has no public getX in Yarn, use its owning list.
        if not oldentry:
            owner={'SoundAnchorScreen':'parentScreen.anchorList','SoundAnchorEditScreen':'parentScreen.soundList','AllSoundsPickerScreen':'parent.soundList'}.get(cls)
            if owner:s=s.replace('this.getX()',owner+'.getRowLeft()')
    return s

for port in PORTS:
    fab=port.name.startswith('fabric-');yarn=fab and '-26.' not in port.name
    java=port/'src/main/java/soundcontrol'
    context=(T/'AnchorWorldContext.java.template').read_text(encoding='utf-8')
    if yarn:
        context=context.replace('net.minecraft.client.Minecraft;', 'net.minecraft.client.MinecraftClient;').replace('Minecraft client = Minecraft.getInstance()', 'MinecraftClient client = MinecraftClient.getInstance()')
        context=context.replace('net.minecraft.world.level.storage.LevelResource', 'net.minecraft.util.WorldSavePath').replace('LevelResource.ROOT', 'WorldSavePath.ROOT')
        context=context.replace('client.level', 'client.world').replace('getSingleplayerServer()', 'getServer()').replace('getWorldPath(', 'getSavePath(').replace('getCurrentServer()', 'getCurrentServerEntry()').replace('remote.ip', 'remote.address')
    write(java/'AnchorWorldContext.java', context)
    if port.name.endswith('-1.20.1'):
        frame=(T/'AnchorRenderFrame.java.template').read_text(encoding='utf-8')
        hook=(T/'AnchorWorldRenderMixin.java.template').read_text(encoding='utf-8')
        if yarn:
            frame=frame.replace('net.minecraft.world.phys.Vec3', 'net.minecraft.util.math.Vec3d').replace('Vec3 ', 'Vec3d ')
            for old,new in {
                'com.mojang.blaze3d.vertex.PoseStack':'net.minecraft.client.util.math.MatrixStack',
                'net.minecraft.client.Camera':'net.minecraft.client.render.Camera',
                'net.minecraft.client.Minecraft':'net.minecraft.client.MinecraftClient',
                'net.minecraft.client.renderer.GameRenderer':'net.minecraft.client.render.GameRenderer',
                'net.minecraft.client.renderer.LevelRenderer':'net.minecraft.client.render.WorldRenderer',
                'net.minecraft.client.renderer.LightTexture':'net.minecraft.client.render.LightmapTextureManager'
            }.items(): hook=hook.replace(old,new)
            for old,new in {'PoseStack':'MatrixStack','LevelRenderer':'WorldRenderer','LightTexture':'LightmapTextureManager','Minecraft':'MinecraftClient'}.items():
                hook=re.sub(r'\b'+old+r'\b',new,hook)
            hook=hook.replace('method = "renderLevel"', 'method = "render"').replace('getInstance().level','getInstance().world').replace('matrices.last().pose()', 'matrices.peek().getPositionMatrix()').replace('camera.getPosition()', 'camera.getPos()')
        write(java/'AnchorRenderFrame.java',frame)
        write(java/'mixin/AnchorWorldRenderMixin.java',hook)
        mixinFile=port/'src/main/resources/soundcontrol.mixins.json'
        data=json.loads(mixinFile.read_text(encoding='utf-8'))
        if 'AnchorWorldRenderMixin' not in data.setdefault('client',[]):data['client'].append('AnchorWorldRenderMixin')
        write(mixinFile,json.dumps(data,indent=2)+'\n')
        if not yarn:
            refmap=port/'src/main/resources/soundcontrol.refmap.json'
            data=json.loads(refmap.read_text(encoding='utf-8'))
            descriptor='(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V'
            for table in [data['mappings'],data['data']['named:srg']]:
                table.setdefault('soundcontrol/mixin/AnchorWorldRenderMixin',{})['renderLevel']='m_109599_'+descriptor
            write(refmap,json.dumps(data,indent=2)+'\n')
    for cls in ['SoundAnchor','SoundAnchorScreen','SoundAnchorEditScreen','SoundAnchorRenderer','AllSoundsPickerScreen']:
        s=(T/(cls+'.java.template')).read_text(encoding='utf-8')
        sub=('gui' if cls=='AllSoundsPickerScreen' else 'anchor') if fab else ''
        write(java/sub/(cls+'.java'),convert(s,port,cls))
    p=java/'SoundConfig.java';s=p.read_text(encoding='utf-8')
    if fab and 'import soundcontrol.anchor.SoundAnchor;' not in s:s=s.replace('package soundcontrol;', 'package soundcontrol;\n\nimport soundcontrol.anchor.SoundAnchor;')
    if 'List<SoundAnchor> anchors' not in s:s=s.replace('public String activeProfile = "default";', 'public String activeProfile = "default";\n        public List<SoundAnchor> anchors = new ArrayList<>();')
    if 'Map<String, List<SoundAnchor>> anchorsByWorld' not in s:
        s=s.replace('public List<SoundAnchor> anchors = new ArrayList<>();', 'public List<SoundAnchor> anchors = new ArrayList<>(); // Unassigned legacy anchors; explicit import only.\n        public Map<String, List<SoundAnchor>> anchorsByWorld = new LinkedHashMap<>();')
    if 'public static List<SoundAnchor> getAnchors()' in s:
        a=s.index('    public static List<SoundAnchor> getAnchors()');b=body_end(s,s.index('public static float getAnchorVolumeModifier',a))
        s=s[:a]+s[b:]
    s=s.replace('    private static float lookupIn(', (T/'config-methods.java.template').read_text()+'\n    private static float lookupIn(')
    write(p,s)
    p=next(java.rglob('SoundControlScreen.java'));s=p.read_text(encoding='utf-8')
    if not re.search(r'new (?:soundcontrol\.(?:anchor\.)?)?SoundAnchorScreen\(this\)', s):
        widget='ButtonWidget' if yarn else 'Button';comp='Text' if yarn else 'Component'
        client='client' if yarn else 'minecraft';level='world' if yarn else 'level';add='addDrawableChild' if yarn else 'addRenderableWidget';bounds='dimensions' if yarn else 'bounds'
        full='soundcontrol.anchor.SoundAnchorScreen' if fab else 'soundcontrol.SoundAnchorScreen'
        init=s.index('protected void init()');a=s.index('{',init)+1
        block=f'''
        if (this.{client}.{level} != null) {{
            this.{add}({widget}.builder({comp}.literal("\\u2693"),
                    b -> this.{client}.setScreen(new {full}(this)))
                .{bounds}(this.width - 50, this.height - 28, 20, 20)
                .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of({comp}.translatable("text.soundcontrol.anchors.title"))).build());
        }}
'''
        if not yarn:block=block.replace('net.minecraft.client.gui.tooltip.Tooltip.of(', 'net.minecraft.client.gui.components.Tooltip.create(')
        s=s[:a]+block+s[a:]
        write(p,s)
    # Insert renderer beside the existing radar hook; doesn't depend on radar visibility.
    for p in [java/'SoundControl.java', *list((java/'mixin').glob('*.java'))]:
        s=p.read_text(encoding='utf-8')
        if 'SoundWorldRenderer.render(' in s and 'SoundAnchorRenderer.render(' not in s:
            full='soundcontrol.anchor.SoundAnchorRenderer' if fab else 'soundcontrol.SoundAnchorRenderer'
            s=re.sub(r'(\s*)SoundWorldRenderer.render\(([^;]+)\);',lambda m:m[0]+m[1]+full+'.render('+m[2]+');',s)
            write(p,s)
    # Retained 26.x RecentSoundsPicker target-anchor mode must save the settings file too.
    p=next(java.rglob('RecentSoundsPickerScreen.java'));s=p.read_text(encoding='utf-8')
    if 'targetAnchor' in s:
        # The only direct anchor mutations are followed by editScreen methods in most ports.
        s=re.sub(r'(targetAnchor.getSoundOverrides\(\)\.(?:put|remove)\([^;]+;\s*)SoundConfig.save\(\);',r'\1SoundConfig.saveSettings();',s)
        write(p,s)
    print('Generated/integrated',port.name)
