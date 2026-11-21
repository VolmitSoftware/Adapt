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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.adaptation.Cooldowns;
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
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.UUID;


public class TragoulThorns extends SimpleAdaptation<TragoulThorns.Config> {
  private static final int MAX_TARGET_RECHECKS = 4;
  private static final Rgb THORN_CRIMSON = new Rgb(140, 10, 10);
  private static final Rgb THORN_LETHAL = new Rgb(120, 0, 0);
  private static final Id THORNS_DAMAGE = Id.minecraft("thorns");
  private final Cooldowns cooldowns = cooldowns();

  public TragoulThorns() {
    super("tragoul-thorns");
    registerConfiguration(TragoulThorns.Config.class);
    setIcon(Id.minecraft("cactus"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("cactus"))
        .key("challenge_tragoul_thorns_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("iron_chestplate"))
            .key("challenge_tragoul_thorns_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_thorns_500", "tragoul.thorns.damage-reflected", 500, 400);
    registerMilestone("challenge_tragoul_thorns_5k", "tragoul.thorns.damage-reflected", 5000, 1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("cactus"))
        .key("challenge_tragoul_thorns_kill")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TragoulPreferences.PASSIVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getConfig().damageMultiplierPerLevel * level, 2), 1);
  }


  @Handle
  public void on(EntityDamageByEntityEvent e) {
    if (TragoulReactiveDamage.isActive()) {
      return;
    }
    if (e.entity() instanceof PlayerRef p) {
      withAdaptedPlayer(p, e, () -> {
        int level = getActiveLevel(p);
        if (level <= 0) {
          return;
        }

        UUID id = p.uuid();
        if (!cooldowns.isReady(id, 1500L)) {
          return;
        }

        LivingRef le = null;
        EntityRef damager = e.damager();
        if (damager instanceof LivingRef living) {
          le = living;
        } else if (damager instanceof ProjectileRef projectile && projectile.shooter() instanceof LivingRef shooter) {
          le = shooter;
        }

        if (le != null) {
          cooldowns.mark(id, 1500L);
          Reflection reflection = new Reflection(p, le, getConfig().damageMultiplierPerLevel * level,
              getConfig().ignorePassiveMobs || preferenceEnabled(p, TragoulPreferences.PASSIVE));
          LivingRef attacker = le;
          Tasks.runEntity(attacker, () -> captureReflectionTargetOwned(reflection));
        }
      });
    }
  }

  private void captureReflectionTargetOwned(Reflection reflection) {
    LivingRef attacker = reflection.attacker();
    if (!attacker.isValid() || attacker.isDead()
        || !AdaptationDamageTargets.allows(attacker, reflection.ignorePassiveMobs())
        || isProtectedFriendlyOwned(reflection.owner().uuid(), attacker)) {
      return;
    }
    ReflectionTarget target = new ReflectionTarget(attacker.location(), attacker instanceof PlayerRef, 0);
    Tasks.runEntity(reflection.owner(), () -> authorizeReflectionOwnerOwned(reflection, target));
  }

  private void authorizeReflectionOwnerOwned(Reflection reflection, ReflectionTarget target) {
    PlayerRef owner = reflection.owner();
    if (!owner.isOnline() || !isRuntimeRegistered() || getActiveLevel(owner) <= 0) {
      return;
    }
    boolean allowed = target.playerTarget() ? canPVP(owner, target.location()) : canPVE(owner, target.location());
    if (allowed) {
      Tasks.runEntity(reflection.attacker(), () -> applyReflectionTargetOwned(reflection, target));
    }
  }

  private void applyReflectionTargetOwned(Reflection reflection, ReflectionTarget authorization) {
    LivingRef attacker = reflection.attacker();
    if (!isRuntimeRegistered() || !attacker.isValid() || attacker.isDead()
        || isProtectedFriendlyOwned(reflection.owner().uuid(), attacker)
        || !AdaptationDamageTargets.allows(attacker, reflection.ignorePassiveMobs())) {
      return;
    }
    Loc current = attacker.location();
    if (!sameTargetBlock(current, authorization.location())) {
      if (authorization.rechecks() < MAX_TARGET_RECHECKS) {
        ReflectionTarget moved = new ReflectionTarget(current, attacker instanceof PlayerRef, authorization.rechecks() + 1);
        Tasks.runEntity(reflection.owner(), () -> authorizeReflectionOwnerOwned(reflection, moved));
      }
      return;
    }
    double before = attacker.health() + attacker.absorption();
    TragoulReactiveDamage.apply(() -> attacker.damage(reflection.damage(), THORNS_DAMAGE, reflection.owner()));
    double dealt = Math.max(0D, before - attacker.health() - attacker.absorption());
    if (dealt <= 0D) {
      return;
    }
    fx(attacker, FxPriority.COMBAT)
        .ring(Particles.CRIT_MAGIC, 0.6D, 10, 1.0D)
        .dustBurst(THORN_CRIMSON, 4, 0.4D, 1.0F)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.5F, 1.4F, Sounds.ENTITY_ARROW_HIT, 0.4F, 0.8F);
    boolean killed = attacker.isDead();
    if (killed) {
      fx(attacker, FxPriority.COMBAT)
          .particle(Particles.DAMAGE_INDICATOR, 6, 0, 1.0D, 0, 0.3D, 0.05D)
          .dustBurst(THORN_LETHAL, 10, 0.4D, 1.0F)
          .chord(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.7F, 0.7F, Sounds.BLOCK_BONE_BLOCK_BREAK, 0.5F, 1.2F);
    }
    Tasks.runEntity(reflection.owner(), () -> {
      if (reflection.owner().isOnline()) {
        addStat(reflection.owner(), "tragoul.thorns.damage-reflected", dealt);
        if (killed) {
          grantOnce(reflection.owner(), "challenge_tragoul_thorns_kill");
        }
      }
    });
  }

  static boolean sameTargetBlock(Loc current, Loc authorized) {
    return current.world().equals(authorized.world())
        && current.blockX() == authorized.blockX()
        && current.blockY() == authorized.blockY()
        && current.blockZ() == authorized.blockZ();
  }

  private record Reflection(PlayerRef owner, LivingRef attacker, double damage, boolean ignorePassiveMobs) {
  }

  private record ReflectionTarget(Loc location, boolean playerTarget, int rechecks) {
  }



  @ConfigDescription("Reflect damage back to your attacker.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from reflected damage, including inherited servant thorns.", impact = "When enabled, neutral species remain protected even when provoked; player targeting is unchanged.")
    boolean ignorePassiveMobs = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Multiplier Per Level for the Tragoul Thorns adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageMultiplierPerLevel = 1.75;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
