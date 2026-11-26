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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.UUID;


public class TragoulThorns extends SimpleAdaptation<TragoulThorns.Config> {
  private static final Rgb THORN_CRIMSON = new Rgb(140, 10, 10);
  private static final Rgb THORN_LETHAL = new Rgb(120, 0, 0);
  private final Cooldowns cooldowns = cooldowns();
  private final TragoulReflection reflection = new TragoulReflection(this,
      (PlayerRef owner, double dealt, boolean killed) -> {
        addStat(owner, "tragoul.thorns.damage-reflected", dealt);
        if (killed) {
          grantOnce(owner, "challenge_tragoul_thorns_kill");
        }
      });

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

        LivingRef attacker = TragoulReflection.attacker(e.damager());
        if (attacker != null) {
          cooldowns.mark(id, 1500L);
          reflection.reflect(p, attacker, getConfig().damageMultiplierPerLevel * level,
              getConfig().ignorePassiveMobs || preferenceEnabled(p, TragoulPreferences.PASSIVE));
        }
      });
    }
  }

  static void reflectFx(Adaptation<?> owner, LivingRef attacker) {
    Fx.now(owner, attacker, FxPriority.COMBAT)
        .ring(Particles.CRIT_MAGIC, 0.6D, 10, 1.0D)
        .dustBurst(THORN_CRIMSON, 4, 0.4D, 1.0F)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.5F, 1.4F, Sounds.ENTITY_ARROW_HIT, 0.4F, 0.8F);
  }

  static void lethalFx(Adaptation<?> owner, LivingRef attacker) {
    Fx.now(owner, attacker, FxPriority.COMBAT)
        .particle(Particles.DAMAGE_INDICATOR, 6, 0, 1.0D, 0, 0.3D, 0.05D)
        .dustBurst(THORN_LETHAL, 10, 0.4D, 1.0F)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.7F, 0.7F, Sounds.BLOCK_BONE_BLOCK_BREAK, 0.5F, 1.2F);
  }

  @ConfigDescription("Reflect damage back to your attacker.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from reflected damage, including inherited servant thorns.", impact = "When enabled, neutral species remain protected even when provoked; player targeting is unchanged.")
    boolean ignorePassiveMobs = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Multiplier Per Level for the Tragoul Thorns adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageMultiplierPerLevel = 1.75;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
