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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.NetherMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ThreadLocalRandom;

public class NetherWitherResist extends SimpleAdaptation<NetherWitherResist.Config> {
  private final NetherWitherWard ward = new NetherWitherWard(this::fx, cooldowns());

  public NetherWitherResist() {
    super("nether-wither-resist");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_chestplate"));
    setInterval(9283);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wither_rose"))
        .key("challenge_nether_wither_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("nether_star"))
            .key("challenge_nether_wither_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_nether_wither_100", "nether.wither-resist.negated", 100, 300);
    registerMilestone("challenge_nether_wither_1k", "nether.wither-resist.negated", 1000, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    int chance = (int) (getConfig().basePieceChance + getConfig().getChanceAddition() * level);
    statLore(v, chance + "%", 1);
    v.addLore(C.GRAY + AdaptLanguage.text(NetherMessages.WITHER_RESIST_PASSIVE));
  }

  @Handle(ignoreCancelled = false)
  public void onEntityDamage(EntityDamageEvent e) {
    if (e.cause() == DamageCause.WITHER && e.entity() instanceof PlayerRef p) {
      withAdaptedPlayer(p, e, () -> {
        double chance = getTotalChange(p);
        double roll = ThreadLocalRandom.current().nextDouble(100D);
        if (winsChanceRoll(chance, roll)) {
          e.setCancelled(true);
          addStat(p, "nether.wither-resist.negated", 1);
          ward.feedback(p, chance >= 100D);
        }
      });
    }
  }

  public static boolean winsChanceRoll(double chance, double roll) {
    if (!Double.isFinite(chance) || !Double.isFinite(roll) || roll < 0D || roll >= 100D) {
      return false;
    }

    double boundedChance = Math.max(0D, Math.min(100D, chance));
    return roll < boundedChance;
  }

  private double getTotalChange(PlayerRef p) {
    double perPiece = getConfig().basePieceChance + getConfig().chanceAddition * getLevel(p);
    int pieces = NetherWitherWard.netheritePieces(p);
    double total = 0.0D;
    for (int piece = 0; piece < pieces; piece++) {
      total += perPiece;
    }
    return total;
  }

  static void wardFx(FxEmitter emit, boolean mastery) {
    emit.dome(Particles.SOUL, mastery ? 1.2D : 0.9D, mastery ? 20 : 12)
        .dome(Particles.SCULK_SOUL, mastery ? 1.2D : 0.9D, 6)
        .burst(Particles.CRIT, 4, 0.6D)
        .chord(Sounds.PARTICLE_SOUL_ESCAPE, 0.35F, 0.8F, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.4F, 0.5F);
    if (mastery) {
      emit.column(Particles.END_ROD, 6, 1.4D).sound(Sounds.PARTICLE_SOUL_ESCAPE, 0.35F, 1.1F);
    }
  }

  @Getter
  @Setter
  @ConfigDescription("Wearing Netherite Armor has a chance to negate the wither effect.")
  public static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Piece Chance for the Nether Wither Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double basePieceChance = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Chance Addition for the Nether Wither Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double chanceAddition = 5;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 1;
    }
  }
}
