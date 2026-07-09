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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantingGrindstoneRecovery extends SimpleAdaptation<EnchantingGrindstoneRecovery.Config> {
  public EnchantingGrindstoneRecovery() {
    super("enchanting-grindstone-recovery");
    registerConfiguration(Config.class);
    setIcon(Material.GRINDSTONE);
    setInterval(1700);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GRINDSTONE)
        .key("challenge_enchanting_grindstone_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.GRINDSTONE)
            .key("challenge_enchanting_grindstone_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_enchanting_grindstone_50", "enchanting.grindstone-recovery.enchants-recovered", 50, 300);
    registerMilestone("challenge_enchanting_grindstone_500", "enchanting.grindstone-recovery.enchants-recovered", 500, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getRecoverChance(level), 0), 1);
    statLore(v, Form.f(getBonusXp(level), 1), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 3);
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
    if (level <= 0 || p.hasCooldown(Material.GRINDSTONE)) {
      return;
    }

    ItemStack source = getEnchantedSource(e.getView().getTopInventory().getItem(0), e.getView().getTopInventory().getItem(1));
    if (source == null) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getRecoverChance(level)) {
      return;
    }

    ItemStack recovered = makeBook(source.getEnchantments());
    if (recovered == null) {
      return;
    }

    Map<Integer, ItemStack> overflow = p.getInventory().addItem(recovered);
    overflow.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
    int xp = Math.max(0, (int) Math.round(getBonusXp(level)));
    if (xp > 0) {
      p.giveExp(xp);
    }

    p.setCooldown(Material.GRINDSTONE, getCooldownTicks(level));
    recoverySuccessFx(p, level);
    xp(p, getConfig().skillXpOnRecovery);
    addStat(p, "enchanting.grindstone-recovery.enchants-recovered", 1);
  }

  private void recoverySuccessFx(Player p, int level) {
    int rods = 2 + (int) Math.round(getLevelPercent(level) * 3.0D);
    boolean bright = getLevelPercent(level) > 0.66D;
    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.helix(Particles.ENCHANTMENT_TABLE, (0.5D * (1.0D - progress)) + 0.2D, 1.6D, 3, progress * Math.PI * 2.0D);
          if (tick == 0) {
            f.particle(Particles.CRIT_MAGIC, 3, 0, 1.0D, 0, 0.3D, 0.05D)
                .sound(Sound.BLOCK_GRINDSTONE_USE, 0.95F, 1.15F);
          }
          if (tick == 4) {
            f.column(Particles.END_ROD, rods, 1.4D)
                .chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.35F, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.45F);
            if (bright) {
              f.particle(Particle.FLASH, 1, 0, 1.0D, 0, 0, 0);
            }
          }
        })
        .start();
  }

  private ItemStack getEnchantedSource(ItemStack a, ItemStack b) {
    if (isEnchanted(a)) {
      return a;
    }

    if (isEnchanted(b)) {
      return b;
    }

    return null;
  }

  private boolean isEnchanted(ItemStack item) {
    return isItem(item) && !item.getEnchantments().isEmpty();
  }

  private ItemStack makeBook(Map<Enchantment, Integer> source) {
    if (source.isEmpty()) {
      return null;
    }

    List<Map.Entry<Enchantment, Integer>> entries = new ArrayList<>(source.entrySet());
    Map.Entry<Enchantment, Integer> picked = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
    ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
    EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
    if (meta == null) {
      return null;
    }

    int safeLevel = Math.max(1, Math.min(picked.getValue(), picked.getKey().getMaxLevel()));
    meta.addStoredEnchant(picked.getKey(), safeLevel, true);
    book.setItemMeta(meta);
    return book;
  }

  private double getRecoverChance(int level) {
    return Math.min(getConfig().maxRecoverChance, getConfig().recoverChanceBase + (getLevelPercent(level) * getConfig().recoverChanceFactor));
  }

  private double getBonusXp(int level) {
    return getConfig().bonusXpBase + (getLevelPercent(level) * getConfig().bonusXpFactor);
  }

  private int getCooldownTicks(int level) {
    return Math.max(10, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Using a grindstone can recover one removed enchant on a book with bonus XP.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Recover Chance Base for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double recoverChanceBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Recover Chance Factor for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double recoverChanceFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Recover Chance for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRecoverChance = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Xp Base for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusXpBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Xp Factor for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusXpFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 70;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skill Xp On Recovery for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnRecovery = 13;

    public Config() {
      costFactor = 0.74;
      initialCost = 4;
    }
  }
}
