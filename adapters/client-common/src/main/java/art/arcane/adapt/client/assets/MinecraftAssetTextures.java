package art.arcane.adapt.client.assets;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;

/** {@link AssetTextures} over the client's resource manager and texture manager. */
public final class MinecraftAssetTextures implements AssetTextures {
    @Override
    public boolean local(String id) {
        Identifier location = Identifier.tryParse(id);
        return location != null && Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
    }

    @Override
    public boolean register(String id, byte[] png) {
        Identifier location = Identifier.tryParse(id);
        if (location == null) {
            return false;
        }
        try {
            NativeImage image = NativeImage.read(png);
            Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(location::toString, image));
            return true;
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    @Override
    public void release(String id) {
        Identifier location = Identifier.tryParse(id);
        if (location != null) {
            Minecraft.getInstance().getTextureManager().release(location);
        }
    }
}
