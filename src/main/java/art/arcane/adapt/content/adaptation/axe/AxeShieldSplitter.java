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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
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

public class AxeShieldSplitter extends SimpleAdaptation<AxeShieldSplitter.Config> {

  public AxeShieldSplitter() {
    super("axe-shield-splitter");
    registerConfiguration(Config.class);
    setIcon(Material.SHIELD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_axe_shield_splitter_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_axe_shield_splitter_250", "axe.shield-splitter.shields-broken", 250, 700);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, C.YELLOW, "+ ", Form.duration(getDisableTicks(level) * 50D, 1), 1);
    statLore(v, C.RED, "+ ", Form.pc(getBonusDamagePct(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isAxe);
    if (combat == null) {
      return;
    }

    LivingEntity target = combat.target();
    if (!isActivelyBlocking(target)) {
      return;
    }

    Player p = combat.attacker();
    int level = combat.level();
    e.setDamage(e.getDamage() * (1D + getBonusDamagePct(level)));

    if (target instanceof Player blocker) {
      blocker.setCooldown(Material.SHIELD, getDisableTicks(level));
    }

    fx(target.getLocation().add(0D, target.getHeight() * 0.6D, 0D), FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 8, 0.3D)
        .chord(Sound.ITEM_SHIELD_BREAK, 0.8F, 0.9F, Sound.ITEM_AXE_STRIP, 0.5F, 0.7F);

    J.runEntity(p, () -> rewardBreak(p));
  }

  private void rewardBreak(Player p) {
    if (!p.isOnline()) {
      return;
    }
    addStat(p, "axe.shield-splitter.shields-broken", 1);
    xp(p, getConfig().xpPerBreak);
  }

  private boolean isActivelyBlocking(LivingEntity target) {
    if (target instanceof Player blocker) {
      return blocker.isBlocking();
    }
    return PaperCompat.isHandRaised(target) && hasShield(target);
  }

  private boolean hasShield(LivingEntity target) {
    EntityEquipment equipment = target.getEquipment();
    return equipment != null
        && (equipment.getItemInOffHand().getType() == Material.SHIELD
        || equipment.getItemInMainHand().getType() == Material.SHIELD);
  }

  private int getDisableTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().disableTicksBase + (getLevelPercent(level) * getConfig().disableTicksFactor)));
  }

  private double getBonusDamagePct(int level) {
    return getConfig().bonusDamagePctBase + (getLevelPercent(level) * getConfig().bonusDamagePctFactor);
  }

  @ConfigDescription("Axe hits on blocking foes disable their shield longer and deal bonus damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base shield-disable duration in ticks before level scaling.", impact = "Higher values keep struck shields disabled longer.")
    double disableTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra shield-disable ticks granted by leveling.", impact = "Higher values extend shield-disable duration with level.")
    double disableTicksFactor = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base bonus damage fraction against a blocking target.", impact = "Higher values punish blocking foes harder at low levels.")
    double bonusDamagePctBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra bonus damage fraction granted by leveling.", impact = "Higher values reward leveling with more damage against blockers.")
    double bonusDamagePctFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per shield broken open.", impact = "Higher values accelerate skill progression from splitting shields.")
    double xpPerBreak = 5;

    public Config() {
      baseCost = 4;
      costFactor = 0.5;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
