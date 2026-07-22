/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.EnchantingMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class EnchantingQuickEnchant extends SimpleAdaptation<EnchantingQuickEnchant.Config> {
  public EnchantingQuickEnchant() {
    super("enchanting-quick-enchant");
    registerConfiguration(Config.class);
    setIcon(Material.WRITABLE_BOOK);
    setInterval(15100);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENCHANTED_BOOK)
        .key("challenge_enchanting_quick_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.BOOKSHELF)
            .key("challenge_enchanting_quick_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_enchanting_quick_100", "enchanting.quick-enchant.books-applied", 100, 300);
    registerMilestone("challenge_enchanting_quick_1k", "enchanting.quick-enchant.books-applied", 1000, 1000);
  }

  private int getTotalLevelCount(int level) {
    return level + (level > getConfig().maxPowerBonusLimit ? level / getConfig().maxPowerBonus1PerLevels : 0);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getTotalLevelCount(level), 1);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(InventoryClickEvent e) {
    if (e.getClickedInventory() == null) {
      return;
    }

    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    if (e.getAction() != InventoryAction.SWAP_WITH_CURSOR || e.getClick() != ClickType.LEFT) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (e.getAction().equals(InventoryAction.SWAP_WITH_CURSOR)
        && e.getClick().equals(ClickType.LEFT)
        && (e.getSlotType().equals(InventoryType.SlotType.CONTAINER)
        || e.getSlotType().equals(InventoryType.SlotType.ARMOR)
        || e.getSlotType().equals(InventoryType.SlotType.QUICKBAR))
        && e.getCursor() != null
        && e.getCurrentItem() != null
        && e.getCursor().getType().equals(Material.ENCHANTED_BOOK)
        && !e.getCurrentItem().getType().equals(Material.BOOK)
        && !e.getCurrentItem().getType().equals(Material.ENCHANTED_BOOK)
        && e.getCursor().getItemMeta() != null
        && e.getCursor().getItemMeta() instanceof EnchantmentStorageMeta eb
        && e.getCurrentItem().getItemMeta() != null
        && e.getCurrentItem().getAmount() == 1
        && e.getCursor().getAmount() == 1) {
      e.setCancelled(true);
      ItemStack item = e.getCurrentItem();
      ItemStack book = e.getCursor();
      KMap<Enchantment, Integer> itemEnchants = new KMap<>(item.getEnchantments());
      KMap<Enchantment, Integer> bookEnchants = new KMap<>(eb.getStoredEnchants());
      KMap<Enchantment, Integer> newEnchants = itemEnchants.copy();
      KMap<Enchantment, Integer> addEnchants = new KMap<>();
      int power = itemEnchants.values().stream().mapToInt(i -> i).sum();

      if (bookEnchants.isEmpty()) {
        return;
      }

      for (Enchantment i : bookEnchants.k()) {
        if (itemEnchants.containsKey(i)) {
          continue;
        }

        if (!i.canEnchantItem(item)) {
          continue;
        }

        boolean conflicts = false;
        for (Enchantment existing : newEnchants.k()) {
          if (existing.conflictsWith(i) || i.conflictsWith(existing)) {
            conflicts = true;
            break;
          }
        }

        if (conflicts) {
          continue;
        }

        power += bookEnchants.get(i);
        newEnchants.put(i, bookEnchants.get(i));
        addEnchants.put(i, bookEnchants.get(i));
        bookEnchants.remove(i);
      }

      if (power > getTotalLevelCount(level)) {
        Adapt.actionbar(p, C.RED + AdaptLanguage.text(
            EnchantingMessages.QUICK_ENCHANT_LIMIT,
            trusted("power", getTotalLevelCount(level))
        ));
        fx(p.getEyeLocation(), FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 6, 0.2D)
            .particle(Particles.CRIT_MAGIC, 2, 0, 0.2D, 0, 0.15D, 0.02D)
            .chord(Sound.BLOCK_CONDUIT_DEACTIVATE, 0.8F, 1.7F, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
        return;
      }

      if (!itemEnchants.equals(newEnchants)) {
        ItemMeta im = item.getItemMeta();

        if (im instanceof EnchantmentStorageMeta sm) {
          sm.getStoredEnchants().keySet().forEach(sm::removeStoredEnchant);
          newEnchants.forEach((ec, l) -> sm.addStoredEnchant(ec, l, true));
        } else {
          im.getEnchants().keySet().forEach(im::removeEnchant);
          newEnchants.forEach((ec, l) -> im.addEnchant(ec, l, true));
        }

        xp(p, 50);
        item.setItemMeta(im);
        e.setCurrentItem(item);
        addStat(p, "enchanting.quick-enchant.books-applied", 1);
        actionbarSummary(p, addEnchants);
        timeline(p)
            .duration(8)
            .priority(FxPriority.TRANSITION)
            .cullRadius(16.0D)
            .frame((f, tick, progress) -> {
              f.helix(Particles.ENCHANTMENT_TABLE, (0.8D * (1.0D - progress)) + 0.1D, 1.4D, 3, progress * Math.PI * 2.0D);
              if (tick == 0) {
                f.sound(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.7F);
              }
              if ((tick & 1) == 0) {
                f.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, (float) (1.2D + (0.4D * progress)));
              }
            })
            .start();
        xp(p, 320 * addEnchants.values().stream().mapToInt((i) -> i).sum(), "quick-apply");

        if (bookEnchants.isEmpty()) {
          e.setCursor(null);
          fx(p.getEyeLocation(), FxPriority.TRANSITION)
              .particle(Particles.CRIT_MAGIC, 4, 0, 0, 0, 0.2D, 0.05D)
              .particle(Particles.END_ROD, 2, 0, 0.3D, 0, 0.1D, 0.02D)
              .sound(Sound.ITEM_BOOK_PAGE_TURN, 0.8F, 1.4F);
        } else if (!eb.getStoredEnchants().equals(bookEnchants)) {
          eb.getStoredEnchants().keySet().forEach(eb::removeStoredEnchant);
          bookEnchants.forEach((ec, l) -> eb.addStoredEnchant(ec, l, true));
          book.setItemMeta(eb);
          e.setCursor(book);
        }
      }
    }
  }

  private void actionbarSummary(Player p, KMap<Enchantment, Integer> added) {
    if (added.isEmpty()) {
      return;
    }

    StringBuilder summary = new StringBuilder();
    added.forEach((ench, lvl) -> {
      if (summary.length() > 0) {
        summary.append(C.GRAY).append(", ");
      }
      summary.append(C.LIGHT_PURPLE).append("+ ").append(ench.getKey().getKey()).append(' ').append(lvl);
    });
    Adapt.actionbar(p, summary.toString());
  }


  @ConfigDescription("Enchant items by clicking enchant books directly on them.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Power Bonus Limit for the Enchanting Quick Enchant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxPowerBonusLimit = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Power Bonus1Per Levels for the Enchanting Quick Enchant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxPowerBonus1PerLevels = 3;

    public Config() {
      baseCost = 6;
      costFactor = 0.9;
      maxLevel = 7;
      initialCost = 8;
    }
  }
}
