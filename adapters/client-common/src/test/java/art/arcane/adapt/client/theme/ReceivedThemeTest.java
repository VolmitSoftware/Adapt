package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.assets.AssetTextures;
import art.arcane.adapt.client.assets.ReceivedAssets;
import art.arcane.adapt.client.protocol.AssetRef;
import art.arcane.adapt.client.ui.UiResources;
import art.arcane.adapt.client.ui.UiSpriteCache;
import art.arcane.adapt.engine.asset.AssetHashes;
import art.arcane.adapt.engine.asset.SkillLook;
import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.engine.asset.ThemeGenerator;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A theme the server generated and sent for another namespace's skill is found where the skill's id says, and framed. */
class ReceivedThemeTest {
    @Test
    void aReceivedForeignThemeIsFoundAndWearsTheGenericFrame() {
        Id skill = Id.of("alchemist", "alchemy");
        ThemeAssets theme = ThemeGenerator.generate(new SkillLook(skill, 0x7A3FD1, Id.minecraft("brewing_stand")));
        String kitId = ThemeKit.dataId(skill.namespace(), skill.path());
        String atlasId = ThemeKit.atlasId(skill.namespace(), skill.path());
        ReceivedAssets received = new ReceivedAssets();
        received.textures(new AssetTextures() {
            @Override
            public boolean local(String id) {
                return false;
            }

            @Override
            public boolean register(String id, byte[] png) {
                return true;
            }

            @Override
            public void release(String id) {
            }
        });
        UiSpriteCache sprites = new UiSpriteCache(new UiResources() {
            @Override
            public InputStream open(String id) {
                byte[] bytes = received.bytes(id);
                return bytes == null ? null : new ByteArrayInputStream(bytes);
            }

            @Override
            public int revision() {
                return received.revision();
            }
        });
        ThemeLibrary library = new ThemeLibrary();
        assertNull(library.theme(sprites, "alchemist:alchemy"));

        String kitHash = AssetHashes.sha1(theme.kitJson());
        String atlasHash = AssetHashes.sha1(theme.atlasPng());
        received.manifest(List.of(new AssetRef(kitId, kitHash), new AssetRef(atlasId, atlasHash)));
        received.asset(kitId, kitHash, theme.kitJson());
        received.asset(atlasId, atlasHash, theme.atlasPng());
        sprites.refresh();

        PageTheme found = library.theme(sprites, "alchemist:alchemy");
        assertNotNull(found);
        assertEquals(skill, found.lead().id());
        assertEquals(atlasId, found.lead().texture().id());
        assertNull(found.lead().frame());
        assertNull(library.theme(sprites, "alchemy"), "a bare id is an adapt skill");

        RecordingCanvas generated = new RecordingCanvas().at(1.0, 0.5, false);
        SkillFrames.draw(found.lead(), generated);
        RecordingCanvas generic = new RecordingCanvas().at(1.0, 0.5, false);
        FrameDataRenderer.draw(FrameData.GENERIC, generic);
        assertEquals(generic.calls(), generated.calls());
    }
}
