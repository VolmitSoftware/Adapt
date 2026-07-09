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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.UUID;

public class ChronosDejaVu extends SimpleAdaptation<ChronosDejaVu.Config> {
  private final Map<UUID, DamageMemory> memory = playerState();
  private final Cooldowns fxCooldown = cooldowns();

  public ChronosDejaVu() {
    super("chronos-deja-vu");
    registerConfiguration(Config.class);
    setIcon(Material.ITEM_FRAME);
    setInterval(60000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ITEM_FRAME)
        .key("challenge_chronos_deja_vu_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_chronos_deja_vu_500", "chronos.deja-vu.damage-absorbed", 500, 700);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Math.round(getReductionFraction(level) * 100D) + "% " + Localizer.dLocalize("chronos.deja_vu.lore1"));
    v.addLore(C.YELLOW + "+ " + Form.duration(getConfig().memoryWindowMillis, 1) + " " + Localizer.dLocalize("chronos.deja_vu.lore2"));
    v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos.deja_vu.lore3"));
  }

  private double getReductionFraction(int level) {
    return Math.min(getConfig().maxReductionFraction,
        getConfig().baseReductionFraction + (Math.max(1, level) * getConfig().reductionFractionPerLevel));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    EntityDamageEvent.DamageCause cause = e.getCause();
    long now = System.currentTimeMillis();
    DamageMemory previous = memory.put(p.getUniqueId(), new DamageMemory(cause, now));
    boolean familiar = previous != null && previous.cause() == cause && now - previous.timestamp() <= getConfig().memoryWindowMillis;
    if (!familiar) {
      if (previous == null || previous.cause() != cause) {
        fx(p, FxPriority.AMBIENT).dustBurst(1, 0.1D, 0.7F);
      }
      return;
    }

    double fraction = getReductionFraction(level);
    if (fraction <= 0D) {
      return;
    }

    double absorbed = e.getFinalDamage() * fraction;
    if (absorbed <= 0D) {
      return;
    }

    e.setDamage(Math.max(0D, e.getDamage() * (1D - fraction)));

    addStat(p, "chronos.deja-vu.damage-absorbed", absorbed);
    xpSilent(p, absorbed * getConfig().xpPerAbsorbedDamage, "chronos:deja-vu");

    if (fxCooldown.isReady(p.getUniqueId(), getConfig().fxCooldownMillis)) {
      fxCooldown.mark(p.getUniqueId());
      Location echo = p.getLocation().add(0, 1, 0);
      fx(echo, FxPriority.COMBAT)
          .ring(Particle.REVERSE_PORTAL, 0.9D, 6, 0.0D)
          .particle(Particles.ENCHANTMENT_TABLE, 2, 0, 0.4D, 0, 0.35D, 0.01D)
          .chord(Sound.BLOCK_BEACON_AMBIENT, 0.3F, 1.4F, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.25F, 1.2F);
    }
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    long window = getConfig().memoryWindowMillis;
    memory.entrySet().removeIf(entry -> now - entry.getValue().timestamp() > window);
  }

  @ConfigDescription("Remember the last source of pain; taking the same kind of damage again shortly after hurts less.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Window in milliseconds during which a repeated damage cause counts as familiar.", impact = "Higher values keep the damage memory alive longer between hits.")
    long memoryWindowMillis = 8000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base fraction of repeated damage that is absorbed.", impact = "Higher values reduce familiar damage more before level scaling.")
    double baseReductionFraction = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra absorbed fraction per adaptation level.", impact = "Higher values make leveling reduce familiar damage faster.")
    double reductionFractionPerLevel = 0.09;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on the absorbed damage fraction.", impact = "Higher values allow more of a familiar hit to be absorbed.")
    double maxReductionFraction = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per point of absorbed damage.", impact = "Higher values grant more skill XP from familiar hits.")
    double xpPerAbsorbedDamage = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum milliseconds between familiar-hit effect emissions.", impact = "Higher values replay the deja vu effects less often during rapid repeated damage.")
    long fxCooldownMillis = 1500;

    public Config() {
      costFactor = 0.35;
      initialCost = 3;
    }
  }

  private record DamageMemory(EntityDamageEvent.DamageCause cause, long timestamp) {
  }
}
