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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class StealthCutpurse extends SimpleAdaptation<StealthCutpurse.Config> {
  private static final EnumSet<EntityType> TARGETS = EnumSet.of(
      EntityType.PILLAGER,
      EntityType.VINDICATOR,
      EntityType.PIGLIN,
      EntityType.PIGLIN_BRUTE);

  private final NamespacedKey pickedKey;

  public StealthCutpurse() {
    super("stealth-cutpurse");
    registerConfiguration(Config.class);
    setIcon(Material.SHEARS);
    pickedKey = new NamespacedKey(Adapt.instance, "cutpurse_picked");
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GOLD_NUGGET)
        .key("challenge_stealth_cutpurse_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.EMERALD)
            .key("challenge_stealth_cutpurse_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_stealth_cutpurse_100", "stealth.cutpurse.pockets-picked", 100, 400);
    registerMilestone("challenge_stealth_cutpurse_1k", "stealth.cutpurse.pockets-picked", 1000, 1500);
  }

  static double computeStealChance(double base, double factor, double maxChance, double percent) {
    return Math.min(maxChance, base + (percent * factor));
  }

  static int computeLootStacks(double base, double factor, double percent) {
    return Math.max(1, (int) Math.round(base + (percent * factor)));
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getStealChance(level), 0), 1);
    statLore(v, Form.f(getLootStacks(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.MeleeContext combat = resolveMeleeContext(e);
    if (combat == null) {
      return;
    }

    Player attacker = combat.attacker();
    LivingEntity target = combat.target();
    if (!attacker.isSneaking() || !TARGETS.contains(target.getType()) || !(target instanceof Mob mob)) {
      return;
    }

    PersistentDataContainer pdc = target.getPersistentDataContainer();
    if (pdc.has(pickedKey, PersistentDataType.BYTE)) {
      return;
    }

    int level = combat.level();
    if (ThreadLocalRandom.current().nextDouble() >= getStealChance(level)) {
      return;
    }

    LootTable lootTable = mob.getLootTable();
    if (lootTable == null) {
      return;
    }

    Location targetLocation = target.getLocation().clone();
    LootContext context = new LootContext.Builder(targetLocation)
        .lootedEntity(target)
        .killer(attacker)
        .luck((float) getLootQuality(level))
        .build();

    Collection<org.bukkit.inventory.ItemStack> rolled = lootTable.populateLoot(ThreadLocalRandom.current(), context);
    List<org.bukkit.inventory.ItemStack> stolen = new ArrayList<>();
    int maxStacks = getLootStacks(level);
    for (org.bukkit.inventory.ItemStack stack : rolled) {
      if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
        continue;
      }
      stolen.add(stack.clone());
      if (stolen.size() >= maxStacks) {
        break;
      }
    }

    if (stolen.isEmpty()) {
      return;
    }

    pdc.set(pickedKey, PersistentDataType.BYTE, (byte) 1);
    J.runEntity(attacker, () -> deliver(attacker, targetLocation, stolen));
  }

  private void deliver(Player p, Location targetLocation, List<org.bukkit.inventory.ItemStack> stolen) {
    if (!p.isOnline()) {
      dropAll(targetLocation, stolen);
      return;
    }

    Map<Integer, org.bukkit.inventory.ItemStack> leftovers =
        p.getInventory().addItem(stolen.toArray(new org.bukkit.inventory.ItemStack[0]));
    if (!leftovers.isEmpty()) {
      dropAll(targetLocation, new ArrayList<>(leftovers.values()));
    }

    addStat(p, "stealth.cutpurse.pockets-picked", 1);
    xp(p, getConfig().xpOnSteal);
    Location eye = p.getEyeLocation();
    fx(targetLocation.clone().add(0, 1.0D, 0), FxPriority.TRAIL)
        .line(Particles.ENCHANTMENT_TABLE, eye.getX(), eye.getY(), eye.getZ(), 5)
        .chord(Sound.ENTITY_ITEM_PICKUP, 0.6F, 1.5F, Sound.ENTITY_VILLAGER_NO, 0.3F, 1.2F);
  }

  private void dropAll(Location location, List<org.bukkit.inventory.ItemStack> stacks) {
    if (location.getWorld() == null || stacks.isEmpty()) {
      return;
    }
    J.runAt(location, () -> {
      if (location.getWorld() == null) {
        return;
      }
      for (org.bukkit.inventory.ItemStack stack : stacks) {
        if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
          location.getWorld().dropItem(location, stack);
        }
      }
    });
  }

  private double getStealChance(int level) {
    return computeStealChance(getConfig().stealChanceBase, getConfig().stealChanceFactor, getConfig().stealChanceMax, getLevelPercent(level));
  }

  private double getLootQuality(int level) {
    return getConfig().lootQualityBase + (getLevelPercent(level) * getConfig().lootQualityFactor);
  }

  private int getLootStacks(int level) {
    return computeLootStacks(getConfig().lootStacksBase, getConfig().lootStacksFactor, getLevelPercent(level));
  }

  @ConfigDescription("Sneak-hits on pillagers, vindicators, and piglins can pick their pockets, stealing loot without a kill.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base chance to pick a pocket on a qualifying sneak-hit.", impact = "Higher values steal more often.")
    double stealChanceBase = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra steal chance gained across levels.", impact = "Higher values raise steal chance more per level.")
    double stealChanceFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum steal chance after scaling.", impact = "Caps how reliable pickpocketing becomes.")
    double stealChanceMax = 0.9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base loot luck used when rolling stolen loot.", impact = "Higher values bias steals toward rarer drops.")
    double lootQualityBase = 0.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra loot luck gained across levels.", impact = "Higher values improve stolen loot quality more per level.")
    double lootQualityFactor = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base number of loot stacks taken per successful steal.", impact = "Higher values steal more items at once.")
    double lootStacksBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra loot stacks gained across levels.", impact = "Higher values steal more items at higher levels.")
    double lootStacksFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Experience granted per successful steal.", impact = "Higher values level the adaptation faster.")
    double xpOnSteal = 15;

    public Config() {
      baseCost = 4;
      costFactor = 0.4;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
