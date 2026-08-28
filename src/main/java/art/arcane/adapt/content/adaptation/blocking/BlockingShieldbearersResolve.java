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

package art.arcane.adapt.content.adaptation.blocking;

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
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class BlockingShieldbearersResolve extends SimpleAdaptation<BlockingShieldbearersResolve.Config> {
  public BlockingShieldbearersResolve() {
    super("blocking-shieldbearers-resolve");
    registerConfiguration(Config.class);
    setIcon(Material.NETHERITE_AXE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_resolve_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_INGOT)
            .key("challenge_blocking_resolve_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_blocking_resolve_100", "blocking.shieldbearers-resolve.recoveries", 100, 600);
    registerMilestone("challenge_blocking_resolve_1k", "blocking.shieldbearers-resolve.recoveries", 1000, 2500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getRecoverySpeed(level), 0), 1);
    statLore(v, Form.f(getResistanceAmplifier(level) + 1, 0), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player p) || !p.isBlocking()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!(e.getDamager() instanceof LivingEntity attacker)) {
      return;
    }

    EntityEquipment equipment = attacker.getEquipment();
    if (equipment == null || !isAxe(equipment.getItemInMainHand())) {
      return;
    }

    long now = System.currentTimeMillis();
    if (getStorageLong(p, "resolveGuardUntil", 0L) > now) {
      return;
    }

    J.runEntity(p, () -> resolveDisable(p, level), 1);
  }

  private void resolveDisable(Player p, int level) {
    if (!p.isOnline()) {
      return;
    }

    int cooldown = p.getCooldown(Material.SHIELD);
    if (cooldown <= 0) {
      return;
    }

    long now = System.currentTimeMillis();
    if (getStorageLong(p, "resolveGuardUntil", 0L) > now) {
      return;
    }
    setStorage(p, "resolveGuardUntil", now + getConfig().reprocessGuardMillis);

    int reduced = (int) Math.round(cooldown * (1.0D - getRecoverySpeed(level)));
    reduced = Math.max(getConfig().minCooldownTicks, reduced);
    if (reduced < cooldown) {
      p.setCooldown(Material.SHIELD, reduced);
    }

    int resistanceTicks = Math.max(getConfig().minResistanceTicks, Math.min(cooldown, reduced));
    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, resistanceTicks, getResistanceAmplifier(level), false, false, true), true);

    addStat(p, "blocking.shieldbearers-resolve.recoveries", 1);
    xp(p, getConfig().xpOnResolve);
    fx(p.getLocation().add(0, 1.0D, 0), FxPriority.COMBAT)
        .ring(Particles.END_ROD, 0.6D, 8, 0)
        .burst(Particles.CRIT_MAGIC, 8, 0.35D)
        .chord(Sound.BLOCK_ANVIL_USE, 0.5F, 0.8F, Sound.ITEM_SHIELD_BLOCK, 0.8F, 1.1F, Sound.BLOCK_BEACON_ACTIVATE, 0.3F, 1.6F);
  }

  private double getRecoverySpeed(int level) {
    return Math.min(getConfig().maxRecoverySpeed, getConfig().recoverySpeedBase + (getLevelPercent(level) * getConfig().recoverySpeedFactor));
  }

  private int getResistanceAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().resistanceAmplifierBase + (getLevelPercent(level) * getConfig().resistanceAmplifierFactor)));
  }


  @ConfigDescription("When an axe disables your shield, brace with resistance and recover the shield faster.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Recovery Speed Base for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values shave more time off a disabled shield's cooldown.")
    double recoverySpeedBase = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Recovery Speed Factor for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values shave more time off a disabled shield's cooldown as you level up.")
    double recoverySpeedFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Recovery Speed for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values allow faster shield recovery at max level.")
    double maxRecoverySpeed = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Resistance Amplifier Base for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values grant a stronger resistance tier.")
    double resistanceAmplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Resistance Amplifier Factor for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values grant a stronger resistance tier as you level up.")
    double resistanceAmplifierFactor = 2.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Resistance Ticks for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values guarantee a longer minimum resistance window.")
    int minResistanceTicks = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Cooldown Ticks for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values keep a longer floor on the recovered shield cooldown.")
    int minCooldownTicks = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reprocess Guard Millis for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values prevent the same disable from being processed multiple times.")
    long reprocessGuardMillis = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Resolve for the Blocking Shieldbearers Resolve adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnResolve = 12;

    public Config() {
      costFactor = 0.7;
      initialCost = 4;
    }
  }
}
