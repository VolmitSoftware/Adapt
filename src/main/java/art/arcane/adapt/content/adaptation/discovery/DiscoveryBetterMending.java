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

package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class DiscoveryBetterMending extends SimpleAdaptation<DiscoveryBetterMending.Config> {
  public DiscoveryBetterMending() {
    super("discovery-better-mending");
    registerConfiguration(Config.class);
    setIcon(Material.PHANTOM_MEMBRANE);
    setInterval(2400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ANVIL)
        .key("challenge_discovery_mending_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENCHANTED_GOLDEN_APPLE)
            .key("challenge_discovery_mending_100k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_mending_10k", "discovery.better-mending.durability-restored", 10000, 400);
    registerMilestone("challenge_discovery_mending_100k", "discovery.better-mending.durability-restored", 100000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRepairPerXp(level)), 1);
    statLore(v, getMaxXpSpend(level), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Action action = e.getAction();
    if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
      return;
    }

    Player p = e.getPlayer();
    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!canMend(hand) || p.hasCooldown(hand.getType())) {
      return;
    }

    Damageable damageable = (Damageable) hand.getItemMeta();
    if (damageable == null || damageable.getDamage() <= 0) {
      return;
    }

    int availableXp = p.calculateTotalExperiencePoints();
    if (availableXp <= 0) {
      FxPresets.failFizzle(this, p);
      return;
    }

    double repairPerXp = getRepairPerXp(level);
    int maxXpSpend = Math.min(getMaxXpSpend(level), availableXp);
    int currentDamage = damageable.getDamage();
    int xpNeeded = (int) Math.ceil(currentDamage / repairPerXp);
    int xpSpent = Math.min(maxXpSpend, xpNeeded);
    if (xpSpent <= 0) {
      FxPresets.failFizzle(this, p);
      return;
    }

    int repaired = Math.max(1, (int) Math.round(xpSpent * repairPerXp));
    int newDamage = Math.max(0, currentDamage - repaired);

    if (!payExperienceCost(p, "experience", xpSpent, () -> spendExperiencePoints(p, xpSpent))) {
      return;
    }

    damageable.setDamage(newDamage);
    hand.setItemMeta(damageable);
    p.getInventory().setItemInMainHand(hand);
    p.setCooldown(hand.getType(), getCooldownTicks(level));
    e.setCancelled(true);

    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16)
        .frame((f, tick, progress) -> {
          f.helix(Particle.WAX_ON, 0.4D, 1.6D, 5, progress * Math.PI * 2.0D);
          if ((tick & 1) == 0) {
            f.particle(Particles.ENCHANTMENT_TABLE, 2, 0, 0.9, 0, 0.2, 0.01);
            f.sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, (float) (1.2D + (progress * 0.4D)));
          }
        })
        .onComplete(() -> {
          if (newDamage <= 0) {
            fx(p.getLocation(), FxPriority.TRANSITION)
                .particle(Particles.TOTEM, 8, 0, 0, 0, 0.15, 0.1)
                .dustRing(Color.AQUA, 0.6D, 12, 1.1F)
                .chord(Sound.BLOCK_ANVIL_USE, 0.5F, 1.65F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.9F);
          }
        })
        .start();

    xp(p, Math.max(1D, (currentDamage - newDamage) * getConfig().skillXpPerDurability));
    addStat(p, "discovery.better-mending.durability-restored", restoredDurability(currentDamage, repaired));
  }

  private boolean canMend(ItemStack hand) {
    if (!isItem(hand) || hand.getType().getMaxDurability() <= 0) {
      return false;
    }

    if (!hand.containsEnchantment(Enchantment.MENDING)) {
      return false;
    }

    if (!(hand.getItemMeta() instanceof Damageable damageable)) {
      return false;
    }

    return damageable.getDamage() > 0;
  }

  static boolean spendExperiencePoints(Player player, int amount) {
    if (player == null || amount <= 0) {
      return false;
    }

    int available = player.calculateTotalExperiencePoints();
    if (available < amount) {
      return false;
    }

    player.setExperienceLevelAndProgress(available - amount);
    return true;
  }

  static int restoredDurability(int currentDamage, int requestedRepair) {
    int normalizedDamage = Math.max(0, currentDamage);
    int newDamage = Math.max(0, normalizedDamage - Math.max(0, requestedRepair));
    return normalizedDamage - newDamage;
  }

  private double getRepairPerXp(int level) {
    return Math.max(0.1, getConfig().repairPerXpBase + (getLevelPercent(level) * getConfig().repairPerXpFactor));
  }

  private int getMaxXpSpend(int level) {
    return Math.max(1, (int) Math.round(getConfig().maxXpSpendBase + (getLevelPercent(level) * getConfig().maxXpSpendFactor)));
  }

  private int getCooldownTicks(int level) {
    return Math.max(6, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksReduction)));
  }

  @ConfigDescription("Sneak-left-click to spend XP and directly mend the Mending item in your hand.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Repair Per Xp Base for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double repairPerXpBase = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Repair Per Xp Factor for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double repairPerXpFactor = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Xp Spend Base for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxXpSpendBase = 14.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Xp Spend Factor for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxXpSpendFactor = 130.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 38.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Reduction for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksReduction = 26.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skill Xp Per Durability for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpPerDurability = 0.35;

    public Config() {
      costFactor = 0.8;
      maxLevel = 6;
      initialCost = 4;
    }
  }
}
