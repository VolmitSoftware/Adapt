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
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantingArcaneSiphon extends SimpleAdaptation<EnchantingArcaneSiphon.Config> {
  public EnchantingArcaneSiphon() {
    super("enchanting-arcane-siphon");
    registerConfiguration(Config.class);
    setIcon(Material.SOUL_LANTERN);
    setInterval(2600);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SOUL_LANTERN)
        .key("challenge_enchanting_siphon_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENCHANTED_BOOK)
            .key("challenge_enchanting_siphon_250")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_enchanting_siphon_25", "enchanting.arcane-siphon.books-siphoned", 25, 400);
    registerMilestone("challenge_enchanting_siphon_250", "enchanting.arcane-siphon.books-siphoned", 250, 1200);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getDropChance(level), 0), 1);
    statLore(v, getQualityBonus(level), 2);
  }

  static double siphonDropChance(double base, double factor, double max, double levelPercent) {
    return Math.min(max, base + (levelPercent * factor));
  }

  static int siphonQualityBonus(double factor, double levelPercent) {
    return (int) Math.floor(levelPercent * factor);
  }

  private double getDropChance(int level) {
    return siphonDropChance(getConfig().dropChanceBase, getConfig().dropChanceFactor, getConfig().maxDropChance, getLevelPercent(level));
  }

  private int getQualityBonus(int level) {
    return siphonQualityBonus(getConfig().qualityFactor, getLevelPercent(level));
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    LivingEntity victim = e.getEntity();
    Player killer = victim.getKiller();
    if (killer == null) {
      return;
    }

    int level = getActiveDamageLevel(killer, victim);
    if (level <= 0) {
      return;
    }
    if (!isEligibleVictim(victim instanceof Player, level, getConfig().maxLevel)) {
      return;
    }

    Map<Enchantment, Integer> gearEnchants = collectGearEnchants(victim);
    if (gearEnchants.isEmpty()) {
      return;
    }

    int enchantCount = gearEnchants.size();
    xp(killer, getConfig().bonusXpPerEnchant * enchantCount);

    if (ThreadLocalRandom.current().nextDouble() > getDropChance(level)) {
      return;
    }

    ItemStack book = makeSiphonBook(gearEnchants, getQualityBonus(level));
    if (book == null) {
      return;
    }

    e.getDrops().add(book);
    addStat(killer, "enchanting.arcane-siphon.books-siphoned", 1);
    siphonFx(victim.getLocation().add(0, 1.0D, 0));
  }

  static boolean isEligibleVictim(boolean playerVictim, int activeLevel, int maxLevel) {
    return activeLevel > 0 && (!playerVictim || activeLevel >= Math.max(1, maxLevel));
  }

  static Map<Enchantment, Integer> collectGearEnchants(EntityEquipment equipment) {
    Map<Enchantment, Integer> enchants = new HashMap<>();
    if (equipment == null) {
      return enchants;
    }

    accumulate(enchants, equipment.getHelmet());
    accumulate(enchants, equipment.getChestplate());
    accumulate(enchants, equipment.getLeggings());
    accumulate(enchants, equipment.getBoots());
    accumulate(enchants, equipment.getItemInMainHand());
    accumulate(enchants, equipment.getItemInOffHand());
    return enchants;
  }

  private Map<Enchantment, Integer> collectGearEnchants(LivingEntity entity) {
    return collectGearEnchants(entity.getEquipment());
  }

  private static void accumulate(Map<Enchantment, Integer> enchants, ItemStack item) {
    if (item == null || item.getAmount() <= 0 || item.getType() == Material.AIR) {
      return;
    }

    mergeHighest(enchants, item.getEnchantments());
  }

  static <T> void mergeHighest(Map<T, Integer> destination, Map<T, Integer> source) {
    for (Map.Entry<T, Integer> entry : source.entrySet()) {
      destination.merge(entry.getKey(), entry.getValue(), Math::max);
    }
  }

  private ItemStack makeSiphonBook(Map<Enchantment, Integer> pool, int qualityBonus) {
    if (pool.isEmpty()) {
      return null;
    }

    List<Map.Entry<Enchantment, Integer>> entries = new ArrayList<>(pool.entrySet());
    Map.Entry<Enchantment, Integer> picked = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
    ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
    EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
    int level = Math.max(1, Math.min(picked.getValue() + qualityBonus, picked.getKey().getMaxLevel()));
    meta.addStoredEnchant(picked.getKey(), level, true);
    book.setItemMeta(meta);
    return book;
  }

  private void siphonFx(Location location) {
    fx(location, FxPriority.COMBAT)
        .helix(Particles.ENCHANTMENT_TABLE, 0.6D, 1.4D, 4, 0)
        .particle(Particles.CRIT_MAGIC, 4, 0, 0.4D, 0, 0.3D, 0.05D)
        .chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.3F, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.5F);
  }


  @ConfigDescription("Killing entities in enchanted gear grants bonus XP and can siphon a book of their enchantments; player victims require max level.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Drop Chance Base for the Enchanting Arcane Siphon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dropChanceBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Drop Chance Factor for the Enchanting Arcane Siphon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dropChanceFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Drop Chance for the Enchanting Arcane Siphon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxDropChance = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Quality Factor for the Enchanting Arcane Siphon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double qualityFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Xp Per Enchant for the Enchanting Arcane Siphon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusXpPerEnchant = 12;

    public Config() {
      baseCost = 4;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
