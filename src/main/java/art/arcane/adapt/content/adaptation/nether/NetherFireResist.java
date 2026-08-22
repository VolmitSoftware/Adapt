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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class NetherFireResist extends SimpleAdaptation<NetherFireResist.Config> {
  private final Cooldowns fizzleCooldowns = cooldowns();

  public NetherFireResist() {
    super("nether-fire-resist");
    registerConfiguration(Config.class);
    setIcon(Material.FIRE_CHARGE);
    setInterval(4333);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.FIRE_CHARGE)
        .key("challenge_nether_fire_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.MAGMA_CREAM)
            .key("challenge_nether_fire_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_nether_fire_200", "nether.fire-resist.negated", 200, 300);
    registerMilestone("challenge_nether_fire_5k", "nether.fire-resist.negated", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, C.RED, "+ ", Form.pc(getFireResist(level), 0), 1);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(EntityDamageEvent e) {

    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (e.getCause() != EntityDamageEvent.DamageCause.FIRE && e.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK) {
        return;
      }


      if (ThreadLocalRandom.current().nextDouble() < getFireResist(getLevel(p))) {
        e.setCancelled(true);
        addStat(p, "nether.fire-resist.negated", 1);
        UUID id = p.getUniqueId();
        if (fizzleCooldowns.remaining(id, 900L) > 0) {
          return;
        }
        boolean warded = fizzleCooldowns.isReady(id, 3000L);
        fizzleCooldowns.mark(id);
        FxEmitter emit = fx(p.getLocation(), FxPriority.TRANSITION)
            .particle(Particles.SMOKE, 5, 0D, 0.2D, 0D, 0.3D, 0.02D)
            .particle(Particle.SPLASH, 3, 0D, 0.1D, 0D, 0.3D, 0.0D)
            .sound(Sound.BLOCK_FIRE_EXTINGUISH, 0.3F, 1.5F);
        if (warded) {
          emit.dustRing(Color.ORANGE, 0.8D, 10, 1.0F).sound(Sound.BLOCK_FIRE_AMBIENT, 0.25F, 0.9F);
        }
      }
    });
  }

  public double getFireResist(double level) {
    return getConfig().fireResistBase + (getConfig().fireResistFactor * level);
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
      costFactor = 0.75;
      maxLevel = 3;
      initialCost = 6;
    }
  }
}
