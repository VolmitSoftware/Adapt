package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.util.common.misc.CustomModelPort;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlayerHeadItem;

public final class ModCustomModelPort implements CustomModelPort {
    private static final String HEAD_PROFILE_NAME = "adapt";
    private static final String CUSTOM_MODEL_DATA = "CustomModelData";

    @Override
    public void setCustomModelData(ItemRef item, int model) {
        ItemStack stack = ModRefs.item(item);
        if (!stack.isEmpty()) {
            stack.getOrCreateTag().putInt(CUSTOM_MODEL_DATA, model);
        }
    }

    @Override
    public void setHeadTexture(ItemRef item, String texture, URL skin) {
        ItemStack stack = ModRefs.item(item);
        if (!stack.is(Items.PLAYER_HEAD)) {
            return;
        }
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + skin.toExternalForm() + "\"}}}";
        String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)), HEAD_PROFILE_NAME);
        profile.getProperties().put("textures", new Property("textures", value));
        stack.getOrCreateTag().put(PlayerHeadItem.TAG_SKULL_OWNER, NbtUtils.writeGameProfile(new CompoundTag(), profile));
    }

    @Override
    public void hideAdditionalTooltip(ItemRef item) {
        item.hideTooltip(TooltipPart.ADDITIONAL);
    }
}
