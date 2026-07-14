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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EnchantingCurseCleansing extends SimpleAdaptation<EnchantingCurseCleansing.Config> {
  public EnchantingCurseCleansing() {
    super("enchanting-curse-cleansing");
    registerConfiguration(Config.class);
    setIcon(Material.GRINDSTONE);
    setInterval(1900);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GRINDSTONE)
        .key("challenge_enchanting_cleanse_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SHEARS)
            .key("challenge_enchanting_cleanse_100")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_enchanting_cleanse_10", "enchanting.curse-cleansing.curses-removed", 10, 300);
    registerMilestone("challenge_enchanting_cleanse_100", "enchanting.curse-cleansing.curses-removed", 100, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, C.YELLOW, "* ", getXpCost(level), 1);
  }

  static int cleanseCost(double base, double factor, int min, double levelPercent) {
    return Math.max(min, (int) Math.round(base - (levelPercent * factor)));
  }

  private int getXpCost(int level) {
    return cleanseCost(getConfig().xpCostBase, getConfig().xpCostFactor, getConfig().minXpCost, getLevelPercent(level));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    if (e.getRawSlot() != 2 || e.getView().getTopInventory().getType() != InventoryType.GRINDSTONE) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!p.isSneaking()) {
      return;
    }

    Inventory top = e.getView().getTopInventory();
    ItemStack result = e.getCurrentItem();
    ItemStack slotA = top.getItem(0);
    ItemStack slotB = top.getItem(1);

    if (hasStrippableCurse(result)) {
      performCleanse(p, level, result, e, () -> {
        top.setItem(0, null);
        top.setItem(1, null);
        e.setCurrentItem(null);
      });
      return;
    }

    if (isItem(result)) {
      return;
    }

    if (hasStrippableCurse(slotA) && !isItem(slotB)) {
      performCleanse(p, level, slotA, e, () -> top.setItem(0, null));
    } else if (hasStrippableCurse(slotB) && !isItem(slotA)) {
      performCleanse(p, level, slotB, e, () -> top.setItem(1, null));
    }
  }

  private void performCleanse(Player p, int level, ItemStack target, InventoryClickEvent e, Runnable consumeInputs) {
    int cost = getXpCost(level);
    if (p.getLevel() < cost) {
      FxPresets.failFizzle(this, p);
      return;
    }

    e.setCancelled(true);
    int removed = countCurses(target);
    ItemStack cleaned = stripCurses(target);
    consumeInputs.run();

    Map<Integer, ItemStack> overflow = p.getInventory().addItem(cleaned);
    overflow.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
    p.setLevel(Math.max(0, p.getLevel() - cost));
    addStat(p, "enchanting.curse-cleansing.curses-removed", removed);
    xp(p, getConfig().skillXpOnCleanse * removed);
    Adapt.actionbar(p, C.GREEN + "- " + removed + " " + Localizer.dLocalize("enchanting.curse_cleansing.cleansed"));
    cleanseFx(p);
    J.runEntity(p, p::updateInventory, 1);
  }

  private boolean hasStrippableCurse(ItemStack item) {
    if (!isItem(item)) {
      return false;
    }

    for (Enchantment curse : CurseSet.VALUES) {
      if (item.getEnchantments().containsKey(curse)) {
        return true;
      }
    }

    ItemMeta meta = item.getItemMeta();
    if (meta instanceof EnchantmentStorageMeta stored) {
      for (Enchantment curse : CurseSet.VALUES) {
        if (stored.hasStoredEnchant(curse)) {
          return true;
        }
      }
    }

    return false;
  }

  private int countCurses(ItemStack item) {
    int count = 0;
    for (Enchantment curse : CurseSet.VALUES) {
      if (item.getEnchantments().containsKey(curse)) {
        count++;
      }
    }

    ItemMeta meta = item.getItemMeta();
    if (meta instanceof EnchantmentStorageMeta stored) {
      for (Enchantment curse : CurseSet.VALUES) {
        if (stored.hasStoredEnchant(curse)) {
          count++;
        }
      }
    }

    return count;
  }

  private ItemStack stripCurses(ItemStack item) {
    ItemStack cleaned = item.clone();
    cleaned.setAmount(1);
    for (Enchantment curse : CurseSet.VALUES) {
      cleaned.removeEnchantment(curse);
    }

    ItemMeta meta = cleaned.getItemMeta();
    if (meta instanceof EnchantmentStorageMeta stored) {
      List<Enchantment> toRemove = new ArrayList<>(2);
      for (Enchantment curse : CurseSet.VALUES) {
        if (stored.hasStoredEnchant(curse)) {
          toRemove.add(curse);
        }
      }
      toRemove.forEach(stored::removeStoredEnchant);
      cleaned.setItemMeta(stored);
    }

    return cleaned;
  }

  private void cleanseFx(Player p) {
    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.helix(Particles.END_ROD, (0.7D * (1.0D - progress)) + 0.15D, 1.5D, 3, progress * Math.PI * 2.0D);
          if (tick == 0) {
            f.burst(Particles.SMOKE, 8, 0.35D)
                .chord(Sound.BLOCK_GRINDSTONE_USE, 0.8F, 0.7F, Sound.BLOCK_CONDUIT_DEACTIVATE, 0.5F, 1.4F);
          }
          if (tick == 4) {
            f.column(Particles.VILLAGER_HAPPY, 5, 1.4D)
                .chord(Sound.BLOCK_BEACON_DEACTIVATE, 0.5F, 1.3F, Sound.ENTITY_PLAYER_LEVELUP, 0.4F, 1.6F);
          }
        })
        .start();
  }


  private static final class CurseSet {
    private static final Set<Enchantment> VALUES = Set.of(Enchantment.BINDING_CURSE, Enchantment.VANISHING_CURSE);
  }


  @ConfigDescription("While sneaking, clicking a grindstone result strips Curse of Binding and Vanishing for a steep XP level cost.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Cost Base for the Enchanting Curse Cleansing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpCostBase = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Cost Factor for the Enchanting Curse Cleansing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpCostFactor = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Xp Cost for the Enchanting Curse Cleansing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minXpCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skill Xp On Cleanse for the Enchanting Curse Cleansing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnCleanse = 30;

    public Config() {
      baseCost = 5;
      costFactor = 0.8;
      maxLevel = 4;
      initialCost = 5;
    }
  }
}
