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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
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
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.UUID;

public class TragoulMarrowArmor extends SimpleAdaptation<TragoulMarrowArmor.Config> {
  private static final Rgb BONE_WHITE = new Rgb(235, 230, 210);
  private static final Id BONE = Id.minecraft("bone");
  private static final Id BONE_BLOCK = Id.minecraft("bone_block");
  private final Cooldowns cooldowns = cooldowns();
  private final Cooldowns clickFx = cooldowns();

  public TragoulMarrowArmor() {
    super("tragoul-marrow-armor");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bone_meal"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone_meal"))
        .key("challenge_tragoul_marrow_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("bone_block"))
            .key("challenge_tragoul_marrow_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_marrow_500", "tragoul.marrow-armor.damage-absorbed", 500, 400);
    registerMilestone("challenge_tragoul_marrow_5k", "tragoul.marrow-armor.damage-absorbed", 5000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TragoulPreferences.BONES);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(TragoulMessages.MARROW_ARMOR_LORE1));
    statLore(v, Form.pc(getAbsorbPercent(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getInternalCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    if (e.finalDamage() < getConfig().minDamageToTrigger) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      UUID id = p.uuid();
      if (!cooldowns.isReady(id, getInternalCooldownMillis(level))) {
        return;
      }

      ItemRef bone = AdaptCore.platform().host().items().create(BONE);
      if (!p.inventory().containsAtLeast(bone, preference(p, TragoulPreferences.BONES).required(1))) {
        if (clickFx.isReady(id, 1500L)) {
          clickFx.mark(id);
          fx(p.location().add(0, 1.0, 0), FxPriority.TRANSITION)
              .burst(Particles.SMOKE, 1, 0.1)
              .sound(Sounds.BLOCK_BONE_BLOCK_HIT, 0.3F, 0.6F);
        }
        return;
      }

      if (!payItemCost(p, "absorb", bone, 1, () -> {
        p.inventory().removeSimilar(bone, 1);
        return true;
      })) {
        return;
      }
      cooldowns.mark(id);
      double absorbed = e.damage() * getAbsorbPercent(level);
      e.setDamage(Math.max(0, e.damage() - absorbed));
      playMarrowShield(p);
      addStat(p, "tragoul.marrow-armor.damage-absorbed", absorbed);
      xp(p, getConfig().xpPerAbsorb);
    });
  }

  private void playMarrowShield(PlayerRef p) {
    ParticleSpec boneCrack = Particles.blockCrack(AdaptCore.platform().host().items().blockState(BONE_BLOCK));
    timeline(p)
        .duration(4)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          if (tick < 3) {
            fx.dustRing(BONE_WHITE, 1.1 - (0.15 * tick), 14, 1.2F);
            fx.dustHelix(BONE_WHITE, 0.8, 2.0, 8, progress * Math.PI, 1.0F);
          }
          if (tick == 0) {
            fx.sound(Sounds.BLOCK_BONE_BLOCK_PLACE, 0.5F, 0.8F);
          }
          if (tick == 3) {
            fx.particle(boneCrack, 4, 0, 1.0, 0, 0.35, 0.05);
            fx.sound(Sounds.BLOCK_BONE_BLOCK_BREAK, 0.8F, 1.1F);
          }
        })
        .start();
  }

  private double getAbsorbPercent(int level) {
    return Math.min(getConfig().maxAbsorbPercent,
        Math.max(0, getConfig().absorbPercentBase + (getLevelPercent(level) * getConfig().absorbPercentFactor)));
  }

  private long getInternalCooldownMillis(int level) {
    return Math.max(500L, (long) Math.round(getConfig().internalCooldownMillisBase - (getLevelPercent(level) * getConfig().internalCooldownMillisFactor)));
  }


  @ConfigDescription("Consume a bone from your inventory to absorb part of incoming damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum final damage required before a bone is consumed.", impact = "Higher values ignore chip damage and save bones for real hits.")
    double minDamageToTrigger = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of the hit absorbed before level scaling.", impact = "Higher values absorb more damage per bone.")
    double absorbPercentBase = 0.20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional absorbed fraction granted at max level.", impact = "Higher values increase level-scaled absorption growth.")
    double absorbPercentFactor = 0.30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on the absorbed fraction of a hit.", impact = "Prevents full damage immunity at high levels.")
    double maxAbsorbPercent = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Internal cooldown in milliseconds before level scaling.", impact = "Higher values stop damage bursts from draining the bone supply.")
    double internalCooldownMillisBase = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds granted at max level.", impact = "Higher values let high levels absorb hits more often.")
    double internalCooldownMillisFactor = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per absorbed hit.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerAbsorb = 8;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
