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

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.Set;

public class ExcavationSoftFall extends SimpleAdaptation<ExcavationSoftFall.Config> {
  private static final Set<String> SOFT_GROUND = Set.of("dirt", "grass_block", "coarse_dirt", "rooted_dirt", "podzol",
      "mycelium", "dirt_path", "farmland", "sand", "red_sand", "gravel", "clay", "mud", "muddy_mangrove_roots",
      "soul_sand", "soul_soil", "snow", "snow_block");

  public ExcavationSoftFall() {
    super("excavation-soft-fall");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("sand"));
    setInterval(3530);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("sand"))
        .key("challenge_excavation_softfall_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_softfall_1k", "excavation.soft-fall.damage-prevented", 1000, 500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getReduction(level), 0), 1);
    v.addLore(C.GRAY + AdaptLanguage.text(ExcavationMessages.SOFT_FALL_LORE2));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (e.cause() != DamageCause.FALL || !(e.entity() instanceof PlayerRef p)) {
      return;
    }

    BlockRef feet = p.world().blockAt(p.location());
    if (!isSoftGround(feet.type()) && !isSoftGround(feet.relative(BlockFace.DOWN).type())) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      double reduction = getReduction(level);
      double prevented = e.damage() * reduction;
      if (prevented <= 0) {
        return;
      }

      e.setDamage(Math.max(0, e.damage() - prevented));
      boolean fullNegate = false;
      if (e.damage() <= 0.01) {
        e.setCancelled(true);
        fullNegate = true;
      }

      BlockRef soft = isSoftGround(feet.type()) ? feet : feet.relative(BlockFace.DOWN);
      BlockStateRef groundData = soft.state();
      Loc at = p.location();
      int puff = (int) Math.min(20.0D, 4.0D + prevented);
      float pitch = (float) Math.max(0.5D, 0.9D - (prevented * 0.02D));
      fx(at, FxPriority.TRANSITION)
          .particle(Particles.CLOUD, puff, 0, 0.1D, 0, 0.3D, 0.02D)
          .particle(Particles.blockCrack(groundData), Math.min(12, 3 + (int) prevented), 0, 0.1D, 0, 0.3D, 0.05D)
          .chord(Sounds.BLOCK_ROOTED_DIRT_BREAK, 0.6f, pitch, Sounds.BLOCK_SAND_BREAK, 0.4f, 0.8f);

      if (fullNegate) {
        cushionRing(at);
      }

      addStat(p, "excavation.soft-fall.damage-prevented", prevented);
      xp(p, prevented * getConfig().xpPerDamagePrevented);
    });
  }

  private void cushionRing(Loc center) {
    timeline(center)
        .duration(4)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.CLOUD, 0.3D + (1.2D * progress), 12, 0.1D);
          if (tick == 0) {
            fx.sound(Sounds.BLOCK_WOOL_FALL, 0.5f, 1.2f);
          }
        })
        .start();
  }

  private boolean isSoftGround(Id type) {
    return type.isMinecraft() && SOFT_GROUND.contains(type.path());
  }

  private double getReduction(int level) {
    return Math.min(getConfig().maxReduction, getConfig().reductionBase + (getLevelPercent(level) * getConfig().reductionFactor));
  }


  @ConfigDescription("Landing on soft diggable ground reduces fall damage, up to full negation.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reduction Base for the Excavation Soft Fall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reduction Factor for the Excavation Soft Fall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionFactor = 0.85;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Reduction for the Excavation Soft Fall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReduction = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Damage Prevented for the Excavation Soft Fall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDamagePrevented = 3.0;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
