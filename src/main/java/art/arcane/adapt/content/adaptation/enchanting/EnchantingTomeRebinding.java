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
import art.arcane.adapt.api.fx.FxPresets;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantingTomeRebinding extends SimpleAdaptation<EnchantingTomeRebinding.Config> {
  public EnchantingTomeRebinding() {
    super("enchanting-tome-rebinding");
    registerConfiguration(Config.class);
    setIcon(Material.WRITABLE_BOOK);
    setInterval(2100);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WRITABLE_BOOK)
        .key("challenge_enchanting_rebind_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENCHANTED_BOOK)
            .key("challenge_enchanting_rebind_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_enchanting_rebind_50", "enchanting.tome-rebinding.books-split", 50, 400);
    registerMilestone("challenge_enchanting_rebind_500", "enchanting.tome-rebinding.books-split", 500, 1200);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getLossChance(level), 0), 1);
    statLore(v, C.YELLOW, "* ", getXpCost(level), 2);
  }

  static double tomeLossChance(double base, double factor, double levelPercent) {
    return Math.max(0.0D, base - (levelPercent * factor));
  }

  static int tomeXpCost(double base, double factor, int min, double levelPercent) {
    return Math.max(min, (int) Math.round(base - (levelPercent * factor)));
  }

  private double getLossChance(int level) {
    return tomeLossChance(getConfig().lossChanceBase, getConfig().lossChanceFactor, getLevelPercent(level));
  }

  private int getXpCost(int level) {
    return tomeXpCost(getConfig().xpCostBase, getConfig().xpCostFactor, getConfig().minXpCost, getLevelPercent(level));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    if (e.getRawSlot() != 0
        || e.getClick() != ClickType.RIGHT
        || e.getView().getTopInventory().getType() != InventoryType.ANVIL
        || isItem(e.getCursor())) {
      return;
    }

    Inventory top = e.getView().getTopInventory();
    ItemStack book = top.getItem(0);
    if (!isMultiEnchantBook(book)) {
      return;
    }

    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0 || !p.isSneaking()) {
      return;
    }

    e.setCancelled(true);
    int cost = getXpCost(level);
    if (p.getLevel() < cost) {
      FxPresets.failFizzle(this, p);
      return;
    }

    EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
    List<Map.Entry<Enchantment, Integer>> enchants = new ArrayList<>(meta.getStoredEnchants().entrySet());
    if (ThreadLocalRandom.current().nextDouble() < getLossChance(level) && enchants.size() > 1) {
      enchants.remove(ThreadLocalRandom.current().nextInt(enchants.size()));
    }

    List<ItemStack> books = new ArrayList<>(enchants.size());
    for (Map.Entry<Enchantment, Integer> entry : enchants) {
      books.add(singleEnchantBook(entry.getKey(), entry.getValue()));
    }

    top.setItem(0, null);
    for (ItemStack single : books) {
      Map<Integer, ItemStack> overflow = p.getInventory().addItem(single);
      overflow.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
    }

    p.setLevel(Math.max(0, p.getLevel() - cost));
    addStat(p, "enchanting.tome-rebinding.books-split", 1);
    xp(p, getConfig().skillXpOnSplit * books.size());
    Adapt.actionbar(p, C.LIGHT_PURPLE + AdaptLanguage.text(
        EnchantingMessages.TOME_REBINDING_MESSAGE,
        trusted("count", books.size())
    ));
    splitFx(p);
    J.runEntity(p, p::updateInventory, 1);
  }

  private boolean isMultiEnchantBook(ItemStack item) {
    return isItem(item)
        && item.getType() == Material.ENCHANTED_BOOK
        && item.getItemMeta() instanceof EnchantmentStorageMeta meta
        && meta.getStoredEnchants().size() >= 2;
  }

  private ItemStack singleEnchantBook(Enchantment enchant, int storedLevel) {
    ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
    EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
    int safeLevel = Math.max(1, Math.min(storedLevel, enchant.getMaxLevel()));
    meta.addStoredEnchant(enchant, safeLevel, true);
    book.setItemMeta(meta);
    return book;
  }

  private void splitFx(Player p) {
    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.ring(Particles.ENCHANTMENT_TABLE, (0.4D + progress) * 0.9D, 10, 1.0D);
          if (tick == 0) {
            f.particle(Particles.CRIT_MAGIC, 4, 0, 0.6D, 0, 0.3D, 0.05D)
                .sound(Sound.ITEM_BOOK_PAGE_TURN, 0.9F, 1.2F);
          }
          if (tick == 4) {
            f.column(Particles.END_ROD, 4, 1.3D)
                .chord(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7F, 1.4F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.6F);
          }
        })
        .start();
  }


  @ConfigDescription("Sneak-right-click a multi-enchant book in an anvil to split it into single-enchant books.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Loss Chance Base for the Enchanting Tome Rebinding adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lossChanceBase = 0.9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Loss Chance Factor for the Enchanting Tome Rebinding adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lossChanceFactor = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Cost Base for the Enchanting Tome Rebinding adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpCostBase = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Cost Factor for the Enchanting Tome Rebinding adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpCostFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Xp Cost for the Enchanting Tome Rebinding adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minXpCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skill Xp On Split for the Enchanting Tome Rebinding adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnSplit = 14;

    public Config() {
      baseCost = 5;
      costFactor = 0.7;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
