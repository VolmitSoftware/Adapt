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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.concurrent.ThreadLocalRandom;
public class AgilityCatReflexes extends SimpleAdaptation<AgilityCatReflexes.Config> {
  public static final Id ID = Id.adapt("agility/cat_reflexes");
  public AgilityCatReflexes() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("rabbit_hide"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("rabbit_hide"))
        .key("challenge_agility_cat_reflexes_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("phantom_membrane"))
            .key("challenge_agility_cat_reflexes_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_cat_reflexes_100", "agility.cat-reflexes.dodges", 100, 300);
    registerMilestone("challenge_agility_cat_reflexes_1k", "agility.cat-reflexes.dodges", 1000, 1200);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getDodgeChance(level), 0), 1);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || !(e.damager() instanceof ProjectileRef projectile)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (!p.isSprinting()) {
        return;
      }

      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      if (!(ThreadLocalRandom.current().nextDouble() < getDodgeChance(level))) {
        return;
      }

      e.setCancelled(true);
      AgilityDodge.sidestep(this, p, projectile);

      addStat(p, "agility.cat-reflexes.dodges", 1);
    });
  }

  private double getDodgeChance(int level) {
    return Math.min(getConfig().maxDodgeChance,
        getConfig().dodgeChanceBase + (getLevelPercent(level) * getConfig().dodgeChanceFactor));
  }

  @ConfigDescription("A chance while sprinting to dodge incoming projectiles entirely.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base projectile dodge chance while sprinting before level scaling.", impact = "Higher values dodge more arrows even at low level.")
    double dodgeChanceBase = 0.08;
    @ConfigDoc(value = "Additional projectile dodge chance granted at max level.", impact = "Higher values widen the dodge-chance gain from leveling.")
    double dodgeChanceFactor = 0.3;
    @ConfigDoc(value = "Hard cap on projectile dodge chance regardless of level.", impact = "Higher values allow a higher maximum dodge rate.")
    double maxDodgeChance = 0.35;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }


  static void sidestepFx(Adaptation<?> owner, ProjectileRef projectile) {
    Fx.now(owner, projectile.location(), FxPriority.COMBAT)
        .burst(Particles.CRIT, 6, 0.2D)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 0.5F, 1.7F, Sounds.ENTITY_ARROW_HIT_PLAYER, 0.3F, 1.9F);
  }
}
