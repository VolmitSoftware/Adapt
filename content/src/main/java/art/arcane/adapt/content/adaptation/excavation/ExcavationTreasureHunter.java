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
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ExcavationTreasureHunter extends SimpleAdaptation<ExcavationTreasureHunter.Config> {
  public static final Id ID = Id.adapt("excavation/treasure_hunter");
  private final ExcavationLoot loot = new ExcavationLoot();

  public ExcavationTreasureHunter() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("emerald"));
    setInterval(3370);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("emerald"))
        .key("challenge_excavation_treasure_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_treasure_500", "excavation.treasure-hunter.treasures-found", 500, 500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getTreasureChance(level), 1), 1);
    v.addLore(TextFormat.GRAY + Text.key("adapt.excavation.treasure_hunter.lore2").resolve());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (!ExcavationFinds.isTreasureBlock(e.block().type())) {
      return;
    }

    PlayerRef p = e.player();
    if (!ItemKinds.isShovel(p.inventory().mainHand())) {
      return;
    }

    BlockActionContext context = resolveBlockBreakContext(p, e.block().location());
    if (context == null) {
      return;
    }

    int level = context.level();
    ThreadLocalRandom random = ThreadLocalRandom.current();
    if (random.nextDouble() > getTreasureChance(level)) {
      return;
    }

    ExcavationLoot.Roll roll = loot.roll(getConfig().lootTable, random);
    if (roll == null) {
      return;
    }

    ExcavationFinds.unearthTreasure(this, e.block().center(), roll);
    addStat(p, "excavation.treasure-hunter.treasures-found", 1);
    xp(p, getConfig().xpPerTreasure);
  }

  private double getTreasureChance(int level) {
    return Math.min(getConfig().maxTreasureChance, getConfig().treasureChanceBase + (getLevelPercent(level) * getConfig().treasureChanceFactor));
  }


  static void unearthFx(Adaptation<?> owner, Loc drop, ItemRef find, boolean rare) {
    int sparkle = rare ? 14 : 6;
    float pling = rare ? 1.9f : 1.4f;
    Fx.now(owner, drop, FxPriority.TRANSITION)
        .particle(Particles.WAX_ON, sparkle, 0, 0.1D, 0, 0.25D, 0.02D)
        .particle(Particles.itemCrack(find), 3, 0, 0.15D, 0, 0.15D, 0.02D)
        .chord(Sounds.ITEM_BRUSH_BRUSHING_SAND_COMPLETE, 0.8f, 1.2f, Sounds.BLOCK_NOTE_BLOCK_PLING, 0.6f, pling);
    if (rare) {
      Fx.now(owner, drop, FxPriority.TRANSITION)
          .particle(Particles.END_ROD, 4, 0, 0.2D, 0, 0.2D, 0.03D)
          .sound(Sounds.ENTITY_PLAYER_LEVELUP, 0.3f, 2.0f);
    }
  }

  @ConfigDescription("Digging sand, gravel, mud, or clay with a shovel can unearth archaeology treasure.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Treasure Chance Base for the Excavation Treasure Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double treasureChanceBase = 0.01;
    @ConfigDoc(value = "Controls Treasure Chance Factor for the Excavation Treasure Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double treasureChanceFactor = 0.05;
    @ConfigDoc(value = "Controls Max Treasure Chance for the Excavation Treasure Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxTreasureChance = 0.06;
    @ConfigDoc(value = "Weighted loot table entries formatted as MATERIAL:weight:min:max.", impact = "Adding entries or raising weights changes which treasures drop and how often.")
    List<String> lootTable = new ArrayList<>(ExcavationFinds.TREASURE_TABLE);
    @ConfigDoc(value = "Controls Xp Per Treasure for the Excavation Treasure Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTreasure = 12;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
