package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.util.common.misc.CustomModelPort;
import art.arcane.adapt.util.reflect.registries.ItemFlags;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class BukkitCustomModelPort implements CustomModelPort {
  @Override
  public void setCustomModelData(ItemRef item, int model) {
    ItemStack stack = BukkitRefs.item(item);
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return;
    }
    meta.setCustomModelData(model);
    stack.setItemMeta(meta);
  }

  @Override
  public void setHeadTexture(ItemRef item, String texture, URL skin) {
    ItemStack stack = BukkitRefs.item(item);
    if (!(stack.getItemMeta() instanceof SkullMeta skull)) {
      return;
    }
    PlayerProfile profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)));
    PlayerTextures textures = profile.getTextures();
    textures.setSkin(skin);
    profile.setTextures(textures);
    skull.setOwnerProfile(profile);
    stack.setItemMeta(skull);
  }

  @Override
  public void hideAdditionalTooltip(ItemRef item) {
    ItemFlag hideAdditionalTooltip = ItemFlags.HIDE_ADDITIONAL_TOOLTIP;
    if (hideAdditionalTooltip == null) {
      return;
    }
    ItemStack stack = BukkitRefs.item(item);
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return;
    }
    meta.addItemFlags(hideAdditionalTooltip);
    stack.setItemMeta(meta);
  }
}
