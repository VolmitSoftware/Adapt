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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;

public class SwordsHamstring extends SimpleAdaptation<SwordsHamstring.Config> {
  public static final Id ID = Id.adapt("sword/hamstring");
  private static final Rgb HAMSTRING = Rgb.of(0x8FE3FF);

  public SwordsHamstring() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("lead"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_swords_hamstring_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_sword"))
            .key("challenge_swords_hamstring_2500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_swords_hamstring_200", "swords.hamstring.hamstrings", 200, 400);
    registerMilestone("challenge_swords_hamstring_2500", "swords.hamstring.hamstrings", 2500, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getSlowTier(level) + 1D, 0), 1);
    statLore(v, Format.duration(getDurationTicks(level) * 50D, 1), 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, ItemKinds::isSword);
    if (combat == null) {
      return;
    }

    LivingRef target = combat.target();
    if (!isFleeing(target)) {
      return;
    }

    int duration = getDurationTicks(combat.level());
    if (duration <= 0) {
      return;
    }

    AdaptAttributeService.get().applyTimed(
        target,
        getId().toString(),
        "slow",
        Attributes.MOVEMENT_SPEED,
        slowSpeedScalar(getSlowTier(combat.level())),
        AttributeOperation.ADD_MULTIPLIED_TOTAL,
        duration);
    if (target instanceof PlayerRef playerTarget) {
      playerTarget.setSprinting(false);
    }

    addStat(combat.attacker(), "swords.hamstring.hamstrings", 1);
    xp(combat.attacker(), getConfig().xpPerHamstring);
    fx(target.location().add(0, 0.3D, 0), FxPriority.COMBAT)
        .dustBurst(HAMSTRING, 6, 0.3D, 1.0F)
        .particle(Particles.CRIT, 4, 0.2D, 0, 0.2D, 0.05D, 0.02D)
        .chord(Sounds.BLOCK_HONEY_BLOCK_SLIDE, 0.5F, 0.8F, Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 0.35F, 0.7F);
  }

  private boolean isFleeing(LivingRef target) {
    if (target instanceof PlayerRef playerTarget && playerTarget.isSprinting()) {
      return true;
    }

    Vec3 velocity = target.velocity();
    double horizontal = Math.sqrt((velocity.x() * velocity.x()) + (velocity.z() * velocity.z()));
    return horizontal >= getConfig().fleeSpeedThreshold;
  }

  static double slowSpeedScalar(int amplifier) {
    return -Math.min(1.0D, 0.15D * (Math.max(0, amplifier) + 1.0D));
  }

  private int getSlowTier(int level) {
    return Math.max(0, (int) Math.round(getConfig().slowTierBase + (getLevelPercent(level) * getConfig().slowTierFactor)));
  }

  private int getDurationTicks(int level) {
    return Math.max(10, (int) Math.round(getConfig().durationTicksBase + (getLevelPercent(level) * getConfig().durationTicksFactor)));
  }


  @ConfigDescription("Hitting a sprinting or fleeing target slows it and briefly stops it sprinting.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Slow Tier Base for the Swords Hamstring adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double slowTierBase = 0;
    @ConfigDoc(value = "Controls Slow Tier Factor for the Swords Hamstring adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double slowTierFactor = 2;
    @ConfigDoc(value = "Controls Duration Ticks Base for the Swords Hamstring adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksBase = 40;
    @ConfigDoc(value = "Controls Duration Ticks Factor for the Swords Hamstring adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksFactor = 80;
    @ConfigDoc(value = "Horizontal speed at which a target counts as fleeing.", impact = "Lower values slow targets more readily; higher values require faster movement.")
    double fleeSpeedThreshold = 0.14;
    @ConfigDoc(value = "Controls Xp Per Hamstring for the Swords Hamstring adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerHamstring = 5;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 1;
    }
  }
}
