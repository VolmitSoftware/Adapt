package art.arcane.adapt.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;

/**
 * {@link UiResources} over the client's resource manager, so resource packs (server packs included) override the mod's
 * own files. A reload opens every pack again, which changes {@link #revision()}.
 */
public final class MinecraftUiResources implements UiResources {
    public static final UiSpriteCache SPRITES = new UiSpriteCache(new MinecraftUiResources());

    private MinecraftUiResources() {
    }

    @Override
    public InputStream open(String id) throws IOException {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        return resource.isPresent() ? resource.get().open() : null;
    }

    @Override
    public int revision() {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceManager manager = minecraft == null ? null : minecraft.getResourceManager();
        if (manager == null) {
            return 0;
        }
        int revision = System.identityHashCode(manager);
        Iterator<PackResources> packs = manager.listPacks().iterator();
        while (packs.hasNext()) {
            revision = revision * 31 + System.identityHashCode(packs.next());
        }
        return revision;
    }
}
