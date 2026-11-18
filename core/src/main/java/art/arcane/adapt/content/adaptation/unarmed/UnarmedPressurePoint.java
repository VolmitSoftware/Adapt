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

package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.UnarmedMessages;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

public class UnarmedPressurePoint extends SimpleAdaptation<UnarmedPressurePoint.Config> {
  private static final Rgb PRESSURE_COLOR = Rgb.of(0x3A4B9F);
  private static final Id SLOWNESS = Id.minecraft("slowness");
  private static final Id WEAKNESS = Id.minecraft("weakness");

  public UnarmedPressurePoint() {
    super("unarmed-pressure-point");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("tripwire_hook"));
    setInterval(4733);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_unarmed_pressure_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond"))
            .key("challenge_unarmed_pressure_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_pressure_500", "unarmed.pressure-point.pressure-strikes", 500, 400);
    registerMilestone("challenge_unarmed_pressure_5k", "unarmed.pressure-point.pressure-strikes", 5000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getMaxSlownessAmplifier(level) + 1, 1);
    if (isWeaknessUnlocked(level)) {
      statLore(v, getConfig().maxWeaknessAmplifier + 1, 2);
    } else {
      v.addLore(C.GRAY + AdaptLanguage.text(UnarmedMessages.PRESSURE_POINT_LORE3));
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    if (isTool(p.inventory().mainHand()) || isTool(p.inventory().offHand())) {
      return;
    }

    if (!(attack.target() instanceof LivingRef victim)) {
      return;
    }

    int level = attack.level();
    int slowAmplifier = applyStack(victim, SLOWNESS, getMaxSlownessAmplifier(level), getConfig().slownessDurationTicks);
    boolean weakened = false;
    if (isWeaknessUnlocked(level)) {
      applyStack(victim, WEAKNESS, getConfig().maxWeaknessAmplifier, getConfig().weaknessDurationTicks);
      weakened = true;
    }

    Loc hit = victim.location().add(0, 1.2D, 0);
    fx(hit, FxPriority.COMBAT)
        .particle(Particles.CRIT, 3, 0, 0, 0, 0.05D, 0.02D)
        .particle(Particles.WAX_ON, 1, 0, 0, 0, 0.02D, 0.0D)
        .chord(Sounds.BLOCK_POINTED_DRIPSTONE_LAND, 0.6F, 1.6F, Sounds.ENTITY_PLAYER_HURT, 0.2F, 1.8F);
    fx(victim.location().add(0, 0.1D, 0), FxPriority.COMBAT)
        .dustBurst(PRESSURE_COLOR, 3 + Math.min(3, slowAmplifier), 0.25D, 0.9F);
    if (weakened) {
      fx(hit, FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 4, 0, 0, 0, 0.05D, 0.01D)
          .sound(Sounds.BLOCK_SCULK_SENSOR_CLICKING, 0.4F, 0.7F);
    }
    xp(p, getConfig().xpPerStrike);
    addStat(p, "unarmed.pressure-point.pressure-strikes", 1);
  }

  private int applyStack(LivingRef victim, Id type, int maxAmplifier, int durationTicks) {
    EffectInstance existing = victim.effect(type);
    int amplifier = existing == null ? 0 : Math.min(maxAmplifier, existing.amplifier() + 1);
    victim.addEffect(new EffectInstance(type, durationTicks, amplifier, false, true, true));
    return amplifier;
  }

  private int getMaxSlownessAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().maxSlownessAmplifierBase + (getLevelPercent(level) * getConfig().maxSlownessAmplifierFactor)));
  }

  private boolean isWeaknessUnlocked(int level) {
    return getLevelPercent(level) >= getConfig().weaknessUnlockPercent;
  }


  @ConfigDescription("Bare-hand hits apply stacking slowness, with weakness at higher levels.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base maximum slowness amplifier at level 1.", impact = "Higher values allow stronger slowness stacking early.")
    double maxSlownessAmplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional maximum slowness amplifier granted at max level.", impact = "Higher values allow stronger slowness stacking as levels increase.")
    double maxSlownessAmplifierFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Slowness duration in ticks per pressure strike.", impact = "Higher values keep targets slowed for longer.")
    int slownessDurationTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Level percent required before weakness stacking unlocks.", impact = "Lower values unlock the weakness effect at earlier levels.")
    double weaknessUnlockPercent = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum weakness amplifier once unlocked.", impact = "Higher values allow stronger weakness stacking.")
    int maxWeaknessAmplifier = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Weakness duration in ticks per pressure strike.", impact = "Higher values keep targets weakened for longer.")
    int weaknessDurationTicks = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per pressure strike.", impact = "Higher values speed up unarmed skill progression from strikes.")
    double xpPerStrike = 3.1;

    public Config() {
      baseCost = 3;
      costFactor = 0.5;
      initialCost = 4;
    }
  }
}
