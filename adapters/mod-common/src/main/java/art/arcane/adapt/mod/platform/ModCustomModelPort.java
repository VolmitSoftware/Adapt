package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.util.common.misc.CustomModelPort;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ResolvableProfile;

public final class ModCustomModelPort implements CustomModelPort {
    private static final String HEAD_PROFILE_NAME = "adapt";

    @Override
    public void setCustomModelData(ItemRef item, int model) {
        ItemStack stack = ModRefs.item(item);
        if (!stack.isEmpty()) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) model), List.of(), List.of(), List.of()));
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
        PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value)));
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)), HEAD_PROFILE_NAME, properties);
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
    }

    @Override
    public void hideAdditionalTooltip(ItemRef item) {
        item.hideTooltip(TooltipPart.ADDITIONAL);
    }
}
