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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
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
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.Map;
import java.util.UUID;

public class ChronosDejaVu extends SimpleAdaptation<ChronosDejaVu.Config> {
  public static final Id ID = Id.adapt("chronos/deja_vu");
  private final Map<UUID, DamageMemory> memory = playerState();
  private final Cooldowns fxCooldown = cooldowns();

  public ChronosDejaVu() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("item_frame"));
    setInterval(60000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("item_frame"))
        .key("challenge_chronos_deja_vu_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_chronos_deja_vu_500", "chronos.deja-vu.damage-absorbed", 500, 700);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Math.round(getReductionFraction(level) * 100D) + "%", 1);
    statLore(v, TextFormat.YELLOW, "+ ", Format.duration(getConfig().memoryWindowMillis, 1), 2);
    v.addLore(TextFormat.GRAY + "* " + Text.key("adapt.chronos.deja_vu.lore3").resolve());
  }

  private double getReductionFraction(int level) {
    return Math.min(getConfig().maxReductionFraction,
        getConfig().baseReductionFraction + (Math.max(1, level) * getConfig().reductionFractionPerLevel));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    DamageCause cause = e.cause();
    long now = System.currentTimeMillis();
    DamageMemory previous = memory.put(p.uuid(), new DamageMemory(cause, now));
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

    double absorbed = e.finalDamage() * fraction;
    if (absorbed <= 0D) {
      return;
    }

    e.setDamage(Math.max(0D, e.damage() * (1D - fraction)));

    addStat(p, "chronos.deja-vu.damage-absorbed", absorbed);
    xpSilent(p, absorbed * getConfig().xpPerAbsorbedDamage, "chronos:deja-vu");

    if (fxCooldown.isReady(p.uuid(), getConfig().fxCooldownMillis)) {
      fxCooldown.mark(p.uuid());
      Loc echo = p.location().add(0, 1, 0);
      fx(echo, FxPriority.COMBAT)
          .ring(Particles.REVERSE_PORTAL, 0.9D, 6, 0.0D)
          .particle(Particles.ENCHANT, 2, 0, 0.4D, 0, 0.35D, 0.01D)
          .chord(Sounds.BLOCK_BEACON_AMBIENT, 0.3F, 1.4F, Sounds.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.25F, 1.2F);
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
    @ConfigDoc(value = "Window in milliseconds during which a repeated damage cause counts as familiar.", impact = "Higher values keep the damage memory alive longer between hits.")
    long memoryWindowMillis = 8000;
    @ConfigDoc(value = "Base fraction of repeated damage that is absorbed.", impact = "Higher values reduce familiar damage more before level scaling.")
    double baseReductionFraction = 0.15;
    @ConfigDoc(value = "Extra absorbed fraction per adaptation level.", impact = "Higher values make leveling reduce familiar damage faster.")
    double reductionFractionPerLevel = 0.09;
    @ConfigDoc(value = "Hard cap on the absorbed damage fraction.", impact = "Higher values allow more of a familiar hit to be absorbed.")
    double maxReductionFraction = 0.6;
    @ConfigDoc(value = "XP granted per point of absorbed damage.", impact = "Higher values grant more skill XP from familiar hits.")
    double xpPerAbsorbedDamage = 0.8;
    @ConfigDoc(value = "Minimum milliseconds between familiar-hit effect emissions.", impact = "Higher values replay the deja vu effects less often during rapid repeated damage.")
    long fxCooldownMillis = 1500;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }

  private record DamageMemory(DamageCause cause, long timestamp) {
  }
}
