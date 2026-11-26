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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AxeMessages;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import java.util.List;

public class AxeThrowingAxe extends SimpleAdaptation<AxeThrowingAxe.Config> {
  private final AxeThrow throwing = new AxeThrow(this, cooldowns(), new AxeThrow.Rules() {
    @Override
    public AxeThrow.Toss toss(int level, Id axe) {
      double factor = getLevelPercent(level);
      return new AxeThrow.Toss((long) getCooldownMs(factor), getThrowDamage(axe, factor), getThrowSpeed(factor),
          returnsAtLevel(level), getConfig().durabilityCost, getConfig().maxFlightTicks);
    }

    @Override
    public boolean ignorePassiveMobs() {
      return getConfig().ignorePassiveMobs;
    }

    @Override
    public void thrown(PlayerRef player) {
      addStat(player, "axe.throwing-axe.thrown", 1);
    }

    @Override
    public void hit(PlayerRef owner, LivingRef target) {
      addStat(owner, "axe.throwing-axe.hits", 1);
      xp(owner, getConfig().xpPerHit);
    }

    @Override
    public void launchFx(Loc spawn, Vec3 direction, ProjectileRef axe, int flightTicks) {
      fx(spawn, FxPriority.GAMEPLAY)
          .trail(Particles.CRIT_MAGIC, direction.x(), direction.y(), direction.z(), 1.3D, 6)
          .chord(Sounds.ITEM_TRIDENT_THROW, 0.8F, 1.2F, Sounds.ITEM_AXE_STRIP, 0.4F, 0.7F);
      timeline(axe)
          .duration(flightTicks)
          .priority(FxPriority.TRAIL)
          .cullRadius(40)
          .frame((f, tick, progress) -> {
            f.particle(Particles.CRIT_MAGIC, 2, 0D, 0D, 0D, 0.05D, 0.0D);
            if ((tick & 1) == 0) {
              f.particle(Particles.SMOKE, 1, 0D, 0D, 0D, 0.02D, 0.0D);
            }
          })
          .start();
    }

    @Override
    public void breakFx(Loc impact) {
      fx(impact, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 6, 0.25D)
          .sound(Sounds.ENTITY_ITEM_BREAK, 0.8F, 0.9F);
    }

    @Override
    public void hitFx(LivingRef target) {
      fx(target.location().add(0D, target.height() * 0.6D, 0D), FxPriority.COMBAT)
          .burst(Particles.CRIT_MAGIC, 8, 0.35D)
          .chord(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.8F, 1.0F, Sounds.ITEM_AXE_STRIP, 0.4F, 0.8F);
    }

    @Override
    public void returnFx(PlayerRef owner) {
      fx(owner.eyeLocation(), FxPriority.TRANSITION)
          .burst(Particles.ENCHANTMENT_TABLE, 8, 0.3D)
          .chord(Sounds.ITEM_TRIDENT_RETURN, 0.7F, 1.1F, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, 1.4F);
    }
  });

  public AxeThrowingAxe() {
    super("axe-throwing-axe");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_axe"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_axe"))
        .key("challenge_axe_throw_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_axe"))
            .key("challenge_axe_throw_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_axe_throw_500", "axe.throwing-axe.hits", 500, 500);
    registerMilestone("challenge_axe_throw_5k", "axe.throwing-axe.hits", 5000, 1800);
  }

  @Override
  protected void onRuntimeActivated() {
    throwing.activate();
  }

  @Override
  public void unregister() {
    throwing.close();
    super.unregister();
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.SNEAK, AxePreferences.IGNORE_PASSIVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    double f = getLevelPercent(level);
    statLore(v, C.RED, "+ ", Form.f(getDamageMultiplier(f), 2), 1);
    statLore(v, C.GREEN, "+ ", Form.f(getThrowSpeed(f), 2), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMs(f), 1), 3);
    if (returnsAtLevel(level)) {
      v.addLore(C.AQUA + "" + AdaptLanguage.text(AxeMessages.THROWING_AXE_LORE4));
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    throwing.interact(e);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    if (!isAxe(p.inventory().mainHand()) || getActiveLevel(p) <= 0) {
      return;
    }

    throwing.blockBroken(p);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileHitEvent e) {
    throwing.projectileHit(e);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityRemoveEvent e) {
    throwing.entityRemoved(e);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent e) {
    throwing.entityDamaged(e);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    throwing.entityDied(e);
  }

  @Handle(ignoreCancelled = false)
  public void on(PlayerJoinEvent event) {
    throwing.joined(event.player());
  }

  @Handle(ignoreCancelled = false)
  public void on(PlayerQuitEvent event) {
    throwing.quit(event.player());
  }

  private boolean returnsAtLevel(int level) {
    return getLevelPercent(level) >= getConfig().returnUnlockLevelPercent;
  }

  private double getThrowSpeed(double factor) {
    return getConfig().throwSpeedBase + (factor * getConfig().throwSpeedFactor);
  }

  private double getDamageMultiplier(double factor) {
    return getConfig().damageMultiplierBase + (factor * getConfig().damageMultiplierFactor);
  }

  private double getThrowDamage(Id axe, double factor) {
    return AxeThrow.meleeBaseDamage(axe) * getDamageMultiplier(factor);
  }

  private double getCooldownMs(double factor) {
    return Math.max(250D, getConfig().cooldownMsBase - (factor * getConfig().cooldownMsLevelReduction));
  }

  @ConfigDescription("Left-click air with an axe to hurl it as a spinning projectile that deals its melee damage.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from thrown axe impacts after a ricochet.", impact = "When enabled, bounced axes cannot damage protected mobs. Direct throws and player targeting are unchanged.")
    boolean ignorePassiveMobs = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base fraction of the axe's melee damage dealt on a throw hit.", impact = "Higher values make thrown axes hit harder at every level.")
    double damageMultiplierBase = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra melee-damage fraction added by leveling this adaptation.", impact = "Higher values reward leveling with stronger throws.")
    double damageMultiplierFactor = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base flight velocity of a thrown axe in blocks per tick.", impact = "Higher values make thrown axes travel faster and flatter.")
    double throwSpeedBase = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra flight velocity added by leveling this adaptation.", impact = "Higher values increase throw speed scaling with level.")
    double throwSpeedFactor = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base throw cooldown in milliseconds before level scaling.", impact = "Higher values slow how often axes can be thrown.")
    double cooldownMsBase = 1200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds of cooldown removed at maximum level.", impact = "Higher values let higher levels throw more frequently.")
    double cooldownMsLevelReduction = 600;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Durability spent from the axe on each throw.", impact = "Higher values wear thrown axes out faster.")
    int durabilityCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks a thrown axe stays airborne before it is recovered automatically.", impact = "Higher values let throws travel farther before auto-recovery.")
    int maxFlightTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Level progress required before thrown axes return to your hand.", impact = "Lower values unlock the auto-return at an earlier level.")
    double returnUnlockLevelPercent = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per thrown-axe hit on an entity.", impact = "Higher values accelerate skill progression from throwing.")
    double xpPerHit = 6;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 4;
      initialCost = 2;
    }
  }
}
