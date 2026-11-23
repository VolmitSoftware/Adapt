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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.EntityRef;
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
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.Map;
import java.util.UUID;

public class HunterPredatorFocus extends SimpleAdaptation<HunterPredatorFocus.Config> {
  private static final Rgb FOCUS_RED = new Rgb(190, 40, 40);
  private final Map<UUID, RampState> ramps = playerState();

  public HunterPredatorFocus() {
    super("hunter-predator-focus");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("target"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("target"))
        .key("challenge_hunter_predator_focus_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("spyglass"))
            .key("challenge_hunter_predator_focus_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
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
    statLore(v, C.YELLOW, "+ ", Form.pc(getConfig().perStackBonus, 0), 2);
    statLore(v, C.GREEN, "+ ", Form.pc(getConfig().perStackBonus * Math.max(0, cap - 1), 0), 3);
  }

  static int nextStacks(int previousStacks, boolean sameTarget, boolean withinWindow, int cap) {
    int safeCap = Math.max(1, cap);
    if (!sameTarget || !withinWindow || previousStacks < 1) {
      return 1;
    }
    return Math.min(previousStacks + 1, safeCap);
  }

  static double damageMultiplier(int stacks, double perStackBonus) {
    return 1.0D + (Math.max(0, stacks - 1) * Math.max(0.0D, perStackBonus));
  }

  int getRampCap(int level) {
    return Math.max(1, getConfig().rampCapBase + (int) Math.round(getLevelPercent(level) * getConfig().rampCapFactor));
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    EntityRef target = attack.target();
    int cap = getRampCap(attack.level());
    long now = M.ms();

    RampState state = ramps.computeIfAbsent(p.uuid(), key -> new RampState());
    boolean sameTarget = target.uuid().equals(state.target);
    boolean withinWindow = now - state.lastHitAt <= getConfig().decayMillis;
    int stacks = nextStacks(state.stacks, sameTarget, withinWindow, cap);
    state.target = target.uuid();
    state.stacks = stacks;
    state.lastHitAt = now;
    AbilityGlyphs.duration(p.uuid(), this, now, getConfig().decayMillis, stacks, cap);

    double multiplier = damageMultiplier(stacks, getConfig().perStackBonus);
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
          .particle(Particles.CRIT_MAGIC, 5, 0, 0.25D, 0, 0.3D, 0.0D)
          .chord(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.6F, 1.5F, Sounds.BLOCK_NOTE_BLOCK_PLING, 0.4F, 1.9F);
    }
  }

  @ConfigDescription("Repeated strikes on the same target ramp your damage; switching targets resets the ramp.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus melee damage added per ramp stack for the Hunter Predator Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double perStackBonus = 0.07;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base ramp stack cap before level scaling for the Hunter Predator Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rampCapBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional ramp stack cap gained across levels for the Hunter Predator Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rampCapFactor = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds of inactivity before the ramp decays for the Hunter Predator Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long decayMillis = 3500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Silent xp granted per ramped hit for the Hunter Predator Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerRampedHit = 2;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }

  private static final class RampState {
    private UUID target;
    private int stacks;
    private long lastHitAt;
  }
}
