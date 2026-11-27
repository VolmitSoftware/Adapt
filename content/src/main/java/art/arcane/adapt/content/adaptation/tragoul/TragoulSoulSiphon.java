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
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;


public class TragoulSoulSiphon extends SimpleAdaptation<TragoulSoulSiphon.Config> {
  public static final Id ID = Id.adapt("tragoul/soul_siphon");
  private final TragoulSiphon siphon = new TragoulSiphon(this, playerState(), this::getHealPercent,
      this::getHealCapPerSecond, (PlayerRef owner, double healed) -> {
        addStat(owner, "tragoul.soul-siphon.health-siphoned", healed);
        xp(owner, getConfig().xpPerHeal);
      });

  public TragoulSoulSiphon() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("soul_lantern"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("soul_lantern"))
        .key("challenge_tragoul_siphon_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("soul_campfire"))
            .key("challenge_tragoul_siphon_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_siphon_500", "tragoul.soul-siphon.health-siphoned", 500, 400);
    registerMilestone("challenge_tragoul_siphon_10k", "tragoul.soul-siphon.health-siphoned", 10000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getHealPercent(level), 0), 1);
    statLore(v, TextFormat.YELLOW, "* ", Format.f(getHealCapPerSecond(level), 1), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent event) {
    siphon.capture(event);
  }

  @Override
  public void unregister() {
    siphon.stop();
    super.unregister();
  }

  private double getHealPercent(int level) {
    return Math.max(0D, getConfig().healPercentBase + (getLevelPercent(level) * getConfig().healPercentFactor));
  }

  private double getHealCapPerSecond(int level) {
    return Math.max(0.5D, getConfig().healCapPerSecondBase + (getLevelPercent(level) * getConfig().healCapPerSecondFactor));
  }

  static void cappedFx(Adaptation<?> owner, PlayerRef attacker) {
    Fx.now(owner, attacker.location().add(0, 1.2D, 0), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 2, 0.2D)
        .sound(Sounds.BLOCK_FIRE_EXTINGUISH, 0.15F, 1.4F);
  }

  static void siphonFx(Adaptation<?> owner, PlayerRef attacker, Loc victimLocation) {
    Loc attackerChest = attacker.location().add(0, 1.2D, 0);
    Fx.now(owner, victimLocation.add(0, 1.0D, 0), FxPriority.COMBAT)
        .particle(Particles.SOUL, 3, 0, 0, 0, 0.15D, 0.02D)
        .line(Particles.SOUL, attackerChest.x(), attackerChest.y(), attackerChest.z(), 4)
        .sound(Sounds.PARTICLE_SOUL_ESCAPE, 0.3F, 1.4F);
  }

  @ConfigDescription("Every valid damage source attributed to you heals you for part of its final damage, capped per second.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Fraction of final attributed damage returned as healing before level scaling.", impact = "Higher values increase baseline lifesteal across melee, projectiles, abilities, and other attributed sources.")
    double healPercentBase = 0.05;
    @ConfigDoc(value = "Additional attributed-damage lifesteal fraction granted at max level.", impact = "Higher values increase level-scaled lifesteal growth for every supported damage source.")
    double healPercentFactor = 0.32;
    @ConfigDoc(value = "Maximum health restored per second before level scaling.", impact = "Lower values harden the anti-abuse healing cap across rapid and multi-target damage.")
    double healCapPerSecondBase = 2.0;
    @ConfigDoc(value = "Additional per-second healing cap granted at max level.", impact = "Higher values let high levels sustain more healing per second.")
    double healCapPerSecondFactor = 6.5;
    @ConfigDoc(value = "XP granted per successful siphon heal.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerHeal = 3;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }

}
