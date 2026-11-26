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

package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.HerbalismMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Set;

public class HerbalismLuck extends SimpleAdaptation<HerbalismLuck.Config> {
  private static final Set<Id> GRASSES = Set.of(Id.minecraft("short_grass"), Id.minecraft("tall_grass"));
  private static final List<Id> LUCK_SEEDS = List.of(
      Id.minecraft("melon_seeds"),
      Id.minecraft("pumpkin_seeds"),
      Id.minecraft("cocoa_beans")
  );
  public static final List<Id> LUCK_FOOD = List.of(
      Id.minecraft("potato"),
      Id.minecraft("carrot"),
      Id.minecraft("beetroot"),
      Id.minecraft("apple")
  );
  private static final Set<Id> FLOWERS = Set.of(
      Id.minecraft("dandelion"),
      Id.minecraft("poppy"),
      Id.minecraft("blue_orchid"),
      Id.minecraft("allium"),
      Id.minecraft("azure_bluet"),
      Id.minecraft("red_tulip"),
      Id.minecraft("orange_tulip"),
      Id.minecraft("white_tulip"),
      Id.minecraft("pink_tulip"),
      Id.minecraft("oxeye_daisy"),
      Id.minecraft("cornflower"),
      Id.minecraft("lily_of_the_valley"),
      Id.minecraft("lilac"),
      Id.minecraft("rose_bush"),
      Id.minecraft("peony"),
      Id.minecraft("wither_rose")
  );

  private final HerbalismLuckyDrops drops = new HerbalismLuckyDrops(this, (PlayerRef p) -> {
    xp(p, 100);
    addStat(p, "herbalism.luck.lucky-drops", 1);
  });

  public HerbalismLuck() {
    super("herbalism-luck");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("emerald"));
    setInterval(8121);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("rabbit_foot"))
        .key("challenge_herbalism_luck_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("emerald"))
            .key("challenge_herbalism_luck_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_herbalism_luck_100", "herbalism.luck.lucky-drops", 100, 300);
    registerMilestone("challenge_herbalism_luck_2500", "herbalism.luck.lucky-drops", 2500, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(HerbalismMessages.LUCK_LORE0));
    statLore(v, "(" + Form.f(getEffectiveness(level), 1) + "%) +", 1);
    statLore(v, "(" + Form.f(getEffectiveness(level), 1) + "%) +", 2);
  }

  private double getEffectiveness(double factor) {
    return Math.min(getConfig().highChance, factor * factor + getConfig().lowChance);
  }

  @Handle(priority = EventPriority.NORMAL)
  public void on(BlockDropItemEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    Id brokenType = e.brokenState().type();
    if (GRASSES.contains(brokenType)) {
      drops.roll(p, e.block(), getEffectiveness(getLevel(p)), LUCK_SEEDS);
    }

    if (FLOWERS.contains(brokenType)) {
      drops.roll(p, e.block(), getEffectiveness(getLevel(p)), LUCK_FOOD);
    }
  }

  public static void luckySparkle(Adaptation<?> owner, Loc loc) {
    Fx.now(owner, loc.add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
        .dustRing(new Rgb(120, 230, 120), 0.5D, 12, 1.0F)
        .particle(Particles.VILLAGER_HAPPY, 6, 0, 0.3D, 0, 0.35D, 0.05D)
        .particle(Particles.WAX_ON, 2, 0, 0.3D, 0, 0.3D, 0.02D)
        .chord(Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.4F, Sounds.BLOCK_NOTE_BLOCK_CHIME, 0.4F, 1.8F);
  }

  @ConfigDescription("Breaking Grass or Flowers has a chance to drop random items.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Low Chance for the Herbalism Luck adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lowChance = 0.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls High Chance for the Herbalism Luck adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double highChance = 90;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 7;
      initialCost = 1;
    }
  }
}
