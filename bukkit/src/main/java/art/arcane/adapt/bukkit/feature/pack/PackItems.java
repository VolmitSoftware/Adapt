package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.bukkit.canvas.CanvasItem;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitText;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.compat.PaperApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.UseCooldownComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Item stacks for pack slots: the icon's material, count, name and lore, dressed in the slot's pack look through
 * the Bukkit item meta (item model, custom model data, tooltip frame, hidden tooltip, cooldown group, glint), which
 * Paper and Spigot share. Canvas items carry name and lore as component JSON so every line keeps its shadow colour on
 * both; chest windows put components through Paper's meta and fall back to legacy text without shadows on Spigot.
 */
final class PackItems {
  private static final int VANILLA_MAX_STACK = 64;
  private static final float COOLDOWN_MARKER_SECONDS = 1.0F;

  private PackItems() {
  }

  static CanvasItem canvas(MenuIcon icon, SlotLook look) {
    ItemStack stack = dressed(icon, look);
    String name = icon.name() == null ? null : GsonComponentSerializer.gson().serialize(line(icon.name(), look));
    List<String> lore = new ArrayList<>(icon.lore().size());
    for (String line : icon.lore()) {
      lore.add(GsonComponentSerializer.gson().serialize(line(line, look)));
    }
    return new CanvasItem(stack, name, lore);
  }

  static ItemStack chest(MenuIcon icon, SlotLook look) {
    ItemStack stack = dressed(icon, look);
    if (icon.name() == null && icon.lore().isEmpty()) {
      return stack;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return stack;
    }
    if (PaperApi.isPaper()) {
      if (icon.name() != null) {
        PaperPackText.name(meta, line(icon.name(), look));
      }
      if (!icon.lore().isEmpty()) {
        List<Component> lines = new ArrayList<>(icon.lore().size());
        for (String line : icon.lore()) {
          lines.add(line(line, look));
        }
        PaperPackText.lore(meta, lines);
      }
    } else {
      if (icon.name() != null) {
        meta.setDisplayName(BukkitText.legacy(BukkitText.itemText(icon.name())));
      }
      if (!icon.lore().isEmpty()) {
        List<String> lines = new ArrayList<>(icon.lore().size());
        for (String line : icon.lore()) {
          lines.add(BukkitText.legacy(BukkitText.itemText(line)));
        }
        meta.setLore(lines);
      }
    }
    stack.setItemMeta(meta);
    return stack;
  }

  /** The stack a slot shows, without name and lore. */
  static ItemStack dressed(MenuIcon icon, SlotLook look) {
    ItemStack stack = base(icon);
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return stack;
    }
    if (icon.amount() > VANILLA_MAX_STACK) {
      meta.setMaxStackSize(icon.amount());
    }
    Id model = look != null && look.itemModel() != null ? look.itemModel() : icon.itemModel();
    if (model != null) {
      meta.setItemModel(BukkitRefs.key(model));
    }
    if (look != null && (!look.strings().isEmpty() || !look.floats().isEmpty() || !look.colours().isEmpty())) {
      CustomModelDataComponent data = meta.getCustomModelDataComponent();
      data.setStrings(look.strings());
      data.setFloats(look.floats());
      data.setColors(colours(look.colours()));
      meta.setCustomModelDataComponent(data);
    } else {
      if (icon.customModelData() != 0) {
        meta.setCustomModelData(icon.customModelData());
      }
    }
    if (look != null && look.tooltipStyle() != null) {
      meta.setTooltipStyle(NamespacedKey.fromString(look.tooltipStyle()));
    }
    if (look != null && look.hideTooltip()) {
      meta.setHideTooltip(true);
    }
    if (look != null && look.cooldownGroup() != null) {
      UseCooldownComponent cooldown = meta.getUseCooldown();
      cooldown.setCooldownSeconds(COOLDOWN_MARKER_SECONDS);
      cooldown.setCooldownGroup(BukkitRefs.key(look.cooldownGroup()));
      meta.setUseCooldown(cooldown);
    }
    if (icon.glint()) {
      meta.setEnchantmentGlintOverride(Boolean.TRUE);
    }
    stack.setItemMeta(meta);
    return stack;
  }

  /** One tooltip line with the look's shadow. */
  static Component line(String markup, SlotLook look) {
    Component text = BukkitText.itemText(markup);
    if (look == null || look.loreShadow() == SlotLook.VANILLA_SHADOW) {
      return text;
    }
    return text.shadowColor(ShadowColor.shadowColor(look.loreShadow()));
  }

  private static ItemStack base(MenuIcon icon) {
    if (icon.item() != null) {
      ItemStack prepared = BukkitRefs.item(icon.item());
      if (prepared != null && !prepared.getType().isAir()) {
        ItemStack copy = prepared.clone();
        copy.setAmount(icon.amount());
        return copy;
      }
    }
    Material material = BukkitRefs.material(icon.type());
    if (material == null || material.isAir() || !material.isItem()) {
      material = Material.BARRIER;
    }
    return new ItemStack(material, icon.amount());
  }

  private static List<Color> colours(List<Integer> rgb) {
    List<Color> colours = new ArrayList<>(rgb.size());
    for (Integer value : rgb) {
      colours.add(Color.fromRGB(value & 0xFFFFFF));
    }
    return colours;
  }
}
