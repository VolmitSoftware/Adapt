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
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
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

import java.util.Map;
import java.util.Set;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
    statLore(v, C.GREEN, "+ ", Form.f(getConfig().skillXpPerCurse, 1), 1);
  }

  static double cleanseReward(double xpPerCurse, int curseCount) {
    return Math.max(0.0D, xpPerCurse) * Math.max(0, curseCount);
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    if (e.getRawSlot() != 2 || e.getView().getTopInventory().getType() != InventoryType.GRINDSTONE) {
      return;
    }

    if (getActiveLevel(p) <= 0) {
      return;
    }

    if (!p.isSneaking()) {
      return;
    }

    Inventory top = e.getView().getTopInventory();
    CleansePlan plan = planCleanse(top.getItem(0), top.getItem(1));
    if (plan == null) {
      return;
    }

    performCleanse(p, plan, e, top);
  }

  private void performCleanse(Player p, CleansePlan plan, InventoryClickEvent e, Inventory top) {
    e.setCancelled(true);
    if (!consumeOne(p, top, plan.sourceSlot())) {
      return;
    }

    e.setCurrentItem(null);

    Map<Integer, ItemStack> overflow = p.getInventory().addItem(plan.cleanedItem());
    overflow.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
    addStat(p, "enchanting.curse-cleansing.curses-removed", plan.cursesRemoved());
    xp(p, cleanseReward(getConfig().skillXpPerCurse, plan.cursesRemoved()));
    Adapt.actionbar(p, C.GREEN + AdaptLanguage.text(
        EnchantingMessages.CURSE_CLEANSING_MESSAGE,
        trusted("count", plan.cursesRemoved())
    ));
    cleanseFx(p);
    J.runEntity(p, p::updateInventory, 1);
  }

  static CleansePlan planCleanse(ItemStack slotA, ItemStack slotB) {
    int sourceSlot = selectSourceSlot(hasStrippableCurse(slotA), hasStrippableCurse(slotB));
    if (sourceSlot < 0) {
      return null;
    }

    ItemStack source = sourceSlot == 0 ? slotA : slotB;
    return new CleansePlan(sourceSlot, stripCurses(source), countCurses(source));
  }

  static int selectSourceSlot(boolean firstCursed, boolean secondCursed) {
    if (firstCursed) {
      return 0;
    }
    return secondCursed ? 1 : -1;
  }

  static boolean hasStrippableCurse(ItemStack item) {
    if (item == null || item.getType() == Material.AIR) {
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

  static int countCurses(ItemStack item) {
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

  static ItemStack stripCurses(ItemStack item) {
    ItemStack cleaned = item.clone();
    cleaned.setAmount(1);
    Map<Enchantment, Integer> directEnchantments = cleaned.getEnchantments();
    removePresent(CurseSet.VALUES, directEnchantments::containsKey, cleaned::removeEnchantment);

    ItemMeta meta = cleaned.getItemMeta();
    if (meta instanceof EnchantmentStorageMeta stored) {
      removePresent(CurseSet.VALUES, stored::hasStoredEnchant, stored::removeStoredEnchant);
      cleaned.setItemMeta(stored);
    }

    return cleaned;
  }

  static <T> void removePresent(Iterable<T> candidates, Predicate<T> present, Consumer<T> remove) {
    for (T candidate : candidates) {
      if (present.test(candidate)) {
        remove.accept(candidate);
      }
    }
  }

  private boolean consumeOne(Player p, Inventory inventory, int slot) {
    ItemStack source = inventory.getItem(slot);
    return payItemCost(p, "catalyst",
        source == null ? null : new ItemStack(source.getType()), 1, () -> {
          if (!isItem(source) || source.getAmount() <= 1) {
            inventory.setItem(slot, null);
            return true;
          }

          ItemStack remainder = source.clone();
          remainder.setAmount(source.getAmount() - 1);
          inventory.setItem(slot, remainder);
          return true;
        });
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

  record CleansePlan(int sourceSlot, ItemStack cleanedItem, int cursesRemoved) {
  }

  @ConfigDescription("While sneaking, taking a grindstone result removes curses from the original item first, preserves its other properties, and grants Enchanting XP.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enchanting skill XP granted for each curse removed.", impact = "Higher values reward curse cleansing with more Enchanting XP.")
    double skillXpPerCurse = 30;

    public Config() {
      baseCost = 5;
      costFactor = 0.8;
      maxLevel = 4;
      initialCost = 5;
    }
  }
}
