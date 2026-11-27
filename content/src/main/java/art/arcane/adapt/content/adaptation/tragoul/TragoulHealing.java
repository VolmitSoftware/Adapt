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
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;


public class TragoulHealing extends SimpleAdaptation<TragoulHealing.Config> {
  public static final Id ID = Id.adapt("tragoul/healing");
  private static final Rgb HEAL_CRIMSON = new Rgb(150, 0, 10);
  private static final Rgb HEAL_PINK = new Rgb(210, 40, 40);
  private final TragoulDrain drain = new TragoulDrain(this, this::getDrainDamage,
      (PlayerRef defender, double healed) -> addStat(defender, "tragoul.healing.health-stolen", healed));

  public TragoulHealing() {
    super(ID);
    registerConfiguration(TragoulHealing.Config.class);
    setIcon(Id.minecraft("glistering_melon_slice"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("redstone"))
        .key("challenge_tragoul_healing_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("red_dye"))
            .key("challenge_tragoul_healing_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_healing_500", "tragoul.healing.health-stolen", 500, 400);
    registerMilestone("challenge_tragoul_healing_10k", "tragoul.healing.health-stolen", 10000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, TextFormat.RED, "- ", Format.f(getDrainDamage(level), 1), 1);
    v.addLore(TextFormat.GREEN + Text.key("adapt.tragoul.healing.lore2").resolve());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent event) {
    drain.capture(event);
  }

  @Override
  public void unregister() {
    drain.stop();
    super.unregister();
  }

  private double getDrainDamage(int level) {
    return TragoulDrain.drainDamage(getConfig().drainDamageStart, getConfig().drainDamageEnd, level, getMaxLevel());
  }

  static void drainFx(Adaptation<?> owner, Loc drainOrigin, PlayerRef defender) {
    Loc playerLocation = defender.location();
    Loc from = drainOrigin.add(0, 1.0D, 0);
    double dx = playerLocation.x() - from.x();
    double dy = (playerLocation.y() + 1.0D) - from.y();
    double dz = playerLocation.z() - from.z();
    ParticleData.DustTransition transition = new ParticleData.DustTransition(HEAL_CRIMSON, HEAL_PINK, 1.0F);
    FxTimeline.at(owner, from)
        .duration(5)
        .priority(FxPriority.TRAIL)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.particle(Particles.DUST_COLOR_TRANSITION, 2, dx * progress, dy * progress, dz * progress, 0.05D, 0D, transition);
          if (tick == 0) {
            fx.sound(Sounds.PARTICLE_SOUL_ESCAPE, 0.35F, 1.5F);
          }
        })
        .start();
    Fx.now(owner, defender.location().add(0, 1.0D, 0), FxPriority.TRANSITION)
        .dustBurst(HEAL_PINK, 3, 0.18D, 0.8F)
        .sound(Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.25F, 1.6F);
  }

  @ConfigDescription("Every eligible attacker that damages you loses a small fixed amount of life, which is restored to you.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Life drained from each attacker at adaptation level one.", impact = "Higher values make the passive retaliation stronger at low levels.")
    double drainDamageStart = 0.5;
    @ConfigDoc(value = "Life drained from each attacker at maximum adaptation level.", impact = "Higher values make the passive retaliation stronger at high levels.")
    double drainDamageEnd = 2.0;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
