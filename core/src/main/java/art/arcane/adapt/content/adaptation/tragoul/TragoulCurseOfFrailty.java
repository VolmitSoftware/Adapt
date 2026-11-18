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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.EntityGuardPort;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TragoulCurseOfFrailty extends SimpleAdaptation<TragoulCurseOfFrailty.Config> {
  private static final Rgb FRAILTY_GREEN = new Rgb(90, 120, 40);
  private static final Id WEAKNESS = Id.minecraft("weakness");
  private static final Id SLOWNESS = Id.minecraft("slowness");
  private static final int MAX_ATTACKER_COOLDOWNS = 16_384;
  private final Map<UUID, Long> attackerCooldowns = new ConcurrentHashMap<>();

  public TragoulCurseOfFrailty() {
    super("tragoul-curse-of-frailty");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("fermented_spider_eye"));
    setInterval(5000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("fermented_spider_eye"))
        .key("challenge_tragoul_frailty_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("wither_skeleton_skull"))
            .key("challenge_tragoul_frailty_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_frailty_100", "tragoul.curse-of-frailty.curses-applied", 100, 400);
    registerMilestone("challenge_tragoul_frailty_1k", "tragoul.curse-of-frailty.curses-applied", 1000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(TragoulMessages.CURSE_OF_FRAILTY_LORE1));
    statLore(v, Form.duration(getCurseDurationTicks(level) * 50D, 1), 2);
    if (getLevelPercent(level) >= getConfig().slownessUnlockPercent) {
      v.addLore(C.YELLOW + AdaptLanguage.text(TragoulMessages.CURSE_OF_FRAILTY_LORE3));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    LivingRef attacker = null;
    EntityRef damager = e.damager();
    if (damager instanceof LivingRef living) {
      attacker = living;
    } else if (damager instanceof ProjectileRef projectile && projectile.shooter() instanceof LivingRef shooter) {
      attacker = shooter;
    }

    if (attacker == null || attacker.uuid().equals(p.uuid())) {
      return;
    }

    LivingRef target = attacker;
    long now = System.currentTimeMillis();
    withAdaptedPlayer(p, () -> prepareCurseOwnerOwned(p, target, now));
  }

  private void prepareCurseOwnerOwned(PlayerRef player, LivingRef target, long now) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    UUID targetId = target.uuid();
    long cooldownUntil = now + Math.max(250L, getConfig().perAttackerCooldownMillis);
    if (!reserveCooldown(targetId, now, cooldownUntil)) {
      return;
    }

    UUID playerId = player.uuid();
    if (!Tasks.runEntity(target, () -> captureTargetOwned(player, playerId, target, targetId, level, cooldownUntil))) {
      attackerCooldowns.remove(targetId, cooldownUntil);
    }
  }

  private void captureTargetOwned(PlayerRef player, UUID playerId, LivingRef target, UUID targetId, int level, long cooldownUntil) {
    if (!target.isValid() || target.isDead()) {
      attackerCooldowns.remove(targetId, cooldownUntil);
      return;
    }

    TargetSnapshot snapshot = new TargetSnapshot(
        target.location(),
        target instanceof PlayerRef,
        isProtectedTarget(playerId, target)
    );
    if (!Tasks.runEntity(player, () -> validateCurseOwnerOwned(player, target, targetId, level, cooldownUntil, snapshot))) {
      attackerCooldowns.remove(targetId, cooldownUntil);
    }
  }

  private void validateCurseOwnerOwned(PlayerRef player, LivingRef target, UUID targetId, int level, long cooldownUntil, TargetSnapshot snapshot) {
    if (!player.isOnline() || getActiveLevel(player) <= 0 || snapshot.protectedTarget()) {
      attackerCooldowns.remove(targetId, cooldownUntil);
      return;
    }

    boolean allowed = snapshot.playerTarget()
        ? canPVP(player, snapshot.location())
        : canPVE(player, snapshot.location());
    if (!allowed) {
      attackerCooldowns.remove(targetId, cooldownUntil);
      return;
    }

    double levelPercent = getLevelPercent(level);
    int duration = getCurseDurationTicks(level);
    int weaknessAmplifier = levelPercent >= 0.8 ? 1 : 0;
    boolean heavy = levelPercent >= getConfig().slownessUnlockPercent;
    int slownessAmplifier = Math.max(0, Math.min(4, getConfig().slownessAmplifier));
    if (!Tasks.runEntity(target, () -> applyCurseTargetOwned(player, target, targetId, cooldownUntil, duration,
        weaknessAmplifier, heavy, slownessAmplifier))) {
      attackerCooldowns.remove(targetId, cooldownUntil);
    }
  }

  private void applyCurseTargetOwned(PlayerRef player, LivingRef target, UUID targetId, long cooldownUntil, int duration,
                                     int weaknessAmplifier, boolean heavy, int slownessAmplifier) {
    if (!target.isValid() || target.isDead()) {
      attackerCooldowns.remove(targetId, cooldownUntil);
      return;
    }

    target.addEffect(new EffectInstance(
        WEAKNESS,
        duration,
        weaknessAmplifier,
        true,
        true,
        true
    ));
    if (heavy) {
      target.addEffect(new EffectInstance(
          SLOWNESS,
          duration,
          slownessAmplifier,
          true,
          true,
          true
      ));
    }
    playCurse(target, heavy);
    Tasks.runEntity(player, () -> finishCurseOwnerOwned(player));
  }

  private void finishCurseOwnerOwned(PlayerRef player) {
    if (!player.isOnline()) {
      return;
    }
    addStat(player, "tragoul.curse-of-frailty.curses-applied", 1);
    xp(player, getConfig().xpPerCurse);
  }

  private boolean reserveCooldown(UUID targetId, long now, long cooldownUntil) {
    while (true) {
      Long current = attackerCooldowns.get(targetId);
      if (current != null && current > now) {
        return false;
      }
      if (current == null) {
        if (attackerCooldowns.size() >= MAX_ATTACKER_COOLDOWNS) {
          return false;
        }
        if (attackerCooldowns.putIfAbsent(targetId, cooldownUntil) == null) {
          return true;
        }
        continue;
      }
      if (attackerCooldowns.replace(targetId, current, cooldownUntil)) {
        return true;
      }
    }
  }

  private boolean isProtectedTarget(UUID playerId, LivingRef target) {
    if (target.isInvulnerable() || AdaptPorts.get(EntityGuardPort.class, EntityGuardPort.NONE).isShielded(target)
        || MinionIdentity.isMinion(target)) {
      return true;
    }
    if (!target.has(EntityTrait.TAMEABLE)) {
      return false;
    }
    UUID owner = target.tameOwner();
    return owner != null && playerId.equals(owner);
  }

  private void playCurse(LivingRef target, boolean heavy) {
    timeline(target)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.dustRing(FRAILTY_GREEN, 1.5 - (1.0 * progress), 8, 0.1F);
          if (tick == 0) {
            fx.particle(Particles.WARPED_SPORE, 20, 0, 1.0, 0, 0.3, 0.02);
            fx.particle(Particles.WITCH, 3, 0, 1.8, 0, 0.2, 0.01);
            fx.chord(Sounds.ENTITY_ELDER_GUARDIAN_CURSE, 0.35F, 1.7F, Sounds.ENTITY_ZOMBIE_INFECT, 0.25F, 0.7F);
            if (heavy) {
              fx.burst(Particles.SMOKE, 3, 0.2);
            }
          }
        })
        .start();
  }

  private int getCurseDurationTicks(int level) {
    return Math.max(40, (int) Math.round(getConfig().curseDurationTicksBase + (getLevelPercent(level) * getConfig().curseDurationTicksFactor)));
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    attackerCooldowns.entrySet().removeIf(i -> i.getValue() <= now);
  }

  private record TargetSnapshot(Loc location, boolean playerTarget, boolean protectedTarget) {
  }

  @ConfigDescription("Enemies that strike you are cursed with weakness, and slowness at higher levels.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Curse duration in ticks before level scaling.", impact = "Higher values keep attackers weakened longer.")
    double curseDurationTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional curse duration ticks granted at max level.", impact = "Higher values increase level-scaled duration growth.")
    double curseDurationTicksFactor = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Level progress required before slowness is added to the curse.", impact = "Lower values unlock the slowness component earlier.")
    double slownessUnlockPercent = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Amplifier of the slowness component once unlocked.", impact = "Higher values slow cursed attackers more.")
    int slownessAmplifier = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Per-attacker cooldown in milliseconds between curses.", impact = "Higher values stop one attacker from being re-cursed in quick succession.")
    long perAttackerCooldownMillis = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per curse applied.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerCurse = 5;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
