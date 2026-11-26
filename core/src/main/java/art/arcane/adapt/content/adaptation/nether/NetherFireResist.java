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
package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ThreadLocalRandom;

public class NetherFireResist extends SimpleAdaptation<NetherFireResist.Config> {
  private static final Rgb ORANGE = Rgb.of(0xFFA500);
  private final NetherFireFizzle fizzle = new NetherFireFizzle(this, cooldowns());

  public NetherFireResist() {
    super("nether-fire-resist");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("fire_charge"));
    setInterval(4333);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("fire_charge"))
        .key("challenge_nether_fire_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("magma_cream"))
            .key("challenge_nether_fire_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_nether_fire_200", "nether.fire-resist.negated", 200, 300);
    registerMilestone("challenge_nether_fire_5k", "nether.fire-resist.negated", 5000, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, C.RED, "+ ", Form.pc(getFireResist(level), 0), 1);
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(EntityDamageEvent e) {

    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (!NetherFireFizzle.isBurn(e.cause())) {
        return;
      }

      if (ThreadLocalRandom.current().nextDouble() < getFireResist(getLevel(p))) {
        e.setCancelled(true);
        addStat(p, "nether.fire-resist.negated", 1);
        fizzle.play(p);
      }
    });
  }

  public double getFireResist(double level) {
    return getConfig().fireResistBase + (getConfig().fireResistFactor * level);
  }


  static void fizzleFx(Adaptation<?> owner, PlayerRef p, boolean warded) {
    FxEmitter emit = Fx.now(owner, p.location(), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 5, 0D, 0.2D, 0D, 0.3D, 0.02D)
        .particle(Particles.SPLASH, 3, 0D, 0.1D, 0D, 0.3D, 0.0D)
        .sound(Sounds.BLOCK_FIRE_EXTINGUISH, 0.3F, 1.5F);
    if (warded) {
      emit.dustRing(ORANGE, 0.8D, 10, 1.0F).sound(Sounds.BLOCK_FIRE_AMBIENT, 0.25F, 0.9F);
    }
  }

  @Getter
  @Setter
  @ConfigDescription("Chance to negate the burning effect.")
  public static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fire Resist Base for the Nether Fire Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fireResistBase = 0.10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fire Resist Factor for the Nether Fire Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fireResistFactor = 0.25;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 3;
      initialCost = 2;
    }
  }
}
