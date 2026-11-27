package art.arcane.adapt.client.ui;

import art.arcane.adapt.client.assets.ReceivedAssets;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;

/**
 * {@link UiResources} over the client's resource manager, so resource packs (server packs included) override the mod's
 * own files, and art the server sent ({@link ReceivedAssets}) fills in what no pack has. A reload opens every pack
 * again, which changes {@link #revision()}, as does every received file.
 */
public final class MinecraftUiResources implements UiResources {
    public static final UiSpriteCache SPRITES = new UiSpriteCache(new MinecraftUiResources());

    private MinecraftUiResources() {
    }

    @Override
    public InputStream open(String id) throws IOException {
        byte[] received = ReceivedAssets.SHARED.bytes(id);
        if (received != null) {
            return new ByteArrayInputStream(received);
        }
        Identifier location = Identifier.tryParse(id);
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
        int revision = System.identityHashCode(manager) * 31 + ReceivedAssets.SHARED.revision();
        Iterator<PackResources> packs = manager.listPacks().iterator();
        while (packs.hasNext()) {
            revision = revision * 31 + System.identityHashCode(packs.next());
        }
        return revision;
    }
}
