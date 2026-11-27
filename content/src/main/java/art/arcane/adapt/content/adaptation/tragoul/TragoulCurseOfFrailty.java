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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.integration.Integrations;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.ability.Minions;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TragoulCurseOfFrailty extends SimpleAdaptation<TragoulCurseOfFrailty.Config> {
  public static final Id ID = Id.adapt("tragoul/curse_of_frailty");
  private static final Rgb FRAILTY_GREEN = new Rgb(90, 120, 40);
  private static final Id WEAKNESS = Id.minecraft("weakness");
  private static final Id SLOWNESS = Id.minecraft("slowness");
  private static final int MAX_ATTACKER_COOLDOWNS = 16_384;
  private final Map<UUID, Long> attackerCooldowns = new ConcurrentHashMap<>();

  public TragoulCurseOfFrailty() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("fermented_spider_eye"));
    setInterval(5000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("fermented_spider_eye"))
        .key("challenge_tragoul_frailty_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("wither_skeleton_skull"))
            .key("challenge_tragoul_frailty_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_frailty_100", "tragoul.curse-of-frailty.curses-applied", 100, 400);
    registerMilestone("challenge_tragoul_frailty_1k", "tragoul.curse-of-frailty.curses-applied", 1000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.tragoul.curse_of_frailty.lore1").resolve());
    statLore(v, Format.duration(getCurseDurationTicks(level) * 50D, 1), 2);
    if (getLevelPercent(level) >= getConfig().slownessUnlockPercent) {
      v.addLore(TextFormat.YELLOW + Text.key("adapt.tragoul.curse_of_frailty.lore3").resolve());
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
    if (!AdaptApi.scheduler().runEntity(target, () -> captureTargetOwned(player, playerId, target, targetId, level, cooldownUntil))) {
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
    if (!AdaptApi.scheduler().runEntity(player, () -> validateCurseOwnerOwned(player, target, targetId, level, cooldownUntil, snapshot))) {
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
    if (!AdaptApi.scheduler().runEntity(target, () -> applyCurseTargetOwned(player, target, targetId, cooldownUntil, duration,
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

    target.addEffect(EffectInstance.of(WEAKNESS, duration, weaknessAmplifier).withAmbient(true));
    if (heavy) {
      target.addEffect(EffectInstance.of(SLOWNESS, duration, slownessAmplifier).withAmbient(true));
    }
    playCurse(target, heavy);
    AdaptApi.scheduler().runEntity(player, () -> finishCurseOwnerOwned(player));
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
    if (target.isInvulnerable() || Integrations.isShielded(target)
        || Minions.isMinion(target)) {
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
    @ConfigDoc(value = "Curse duration in ticks before level scaling.", impact = "Higher values keep attackers weakened longer.")
    double curseDurationTicksBase = 60;
    @ConfigDoc(value = "Additional curse duration ticks granted at max level.", impact = "Higher values increase level-scaled duration growth.")
    double curseDurationTicksFactor = 100;
    @ConfigDoc(value = "Level progress required before slowness is added to the curse.", impact = "Lower values unlock the slowness component earlier.")
    double slownessUnlockPercent = 0.6;
    @ConfigDoc(value = "Amplifier of the slowness component once unlocked.", impact = "Higher values slow cursed attackers more.")
    int slownessAmplifier = 0;
    @ConfigDoc(value = "Per-attacker cooldown in milliseconds between curses.", impact = "Higher values stop one attacker from being re-cursed in quick succession.")
    long perAttackerCooldownMillis = 4000;
    @ConfigDoc(value = "XP granted per curse applied.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerCurse = 5;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
