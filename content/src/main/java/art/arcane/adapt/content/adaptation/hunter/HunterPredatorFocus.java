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

package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;


public class HunterPredatorFocus extends SimpleAdaptation<HunterPredatorFocus.Config> {
  public static final Id ID = Id.adapt("hunter/predator_focus");
  private static final Rgb FOCUS_RED = new Rgb(190, 40, 40);
  private final HunterRamp ramps = new HunterRamp(playerState());

  public HunterPredatorFocus() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("target"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("target"))
        .key("challenge_hunter_predator_focus_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("spyglass"))
            .key("challenge_hunter_predator_focus_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_hunter_predator_focus_500", "hunter.predator-focus.ramped-hits", 500, 400);
    registerMilestone("challenge_hunter_predator_focus_5k", "hunter.predator-focus.ramped-hits", 5000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    int cap = getRampCap(level);
    statLore(v, cap, 1);
    statLore(v, TextFormat.YELLOW, "+ ", Format.pc(getConfig().perStackBonus, 0), 2);
    statLore(v, TextFormat.GREEN, "+ ", Format.pc(getConfig().perStackBonus * Math.max(0, cap - 1), 0), 3);
  }

  int getRampCap(int level) {
    return Math.max(1, getConfig().rampCapBase + (int) Math.round(getLevelPercent(level) * getConfig().rampCapFactor));
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    AttackContext attack = resolveAttackContext(e);
    if (attack == null || !HunterRamp.ramps(e)) {
      return;
    }

    PlayerRef p = attack.attacker();
    EntityRef target = attack.target();
    int cap = getRampCap(attack.level());
    long now = System.currentTimeMillis();

    int stacks = ramps.hit(p.uuid(), target.uuid(), cap, 1, getConfig().decayMillis, now);
    HudStatus.duration(p.uuid(), this, now, getConfig().decayMillis, stacks, cap);

    double multiplier = HunterRamp.damageMultiplier(stacks, getConfig().perStackBonus);
    if (multiplier <= 1.0D) {
      return;
    }

    e.setDamage(e.damage() * multiplier);
    addStat(p, "hunter.predator-focus.ramped-hits", 1);
    xpSilent(p, getConfig().xpPerRampedHit);

    if (stacks >= cap && target instanceof LivingRef victim) {
      Loc center = victim.location().add(0, victim.height() * 0.5D, 0);
      fx(center, FxPriority.COMBAT)
          .dustBurst(FOCUS_RED, 8, 0.3D, 1.2F)
          .particle(Particles.CRIT, 5, 0, 0.25D, 0, 0.3D, 0.0D)
          .chord(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.6F, 1.5F, Sounds.BLOCK_NOTE_BLOCK_PLING, 0.4F, 1.9F);
    }
  }

  @ConfigDescription("Repeated strikes on the same target ramp your damage; switching targets resets the ramp.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Bonus melee damage added per ramp stack for the Hunter Predator Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double perStackBonus = 0.07;
    @ConfigDoc(value = "Base ramp stack cap before level scaling for the Hunter Predator Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rampCapBase = 3;
    @ConfigDoc(value = "Additional ramp stack cap gained across levels for the Hunter Predator Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rampCapFactor = 6;
    @ConfigDoc(value = "Milliseconds of inactivity before the ramp decays for the Hunter Predator Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long decayMillis = 3500;
    @ConfigDoc(value = "Silent xp granted per ramped hit for the Hunter Predator Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerRampedHit = 2;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
