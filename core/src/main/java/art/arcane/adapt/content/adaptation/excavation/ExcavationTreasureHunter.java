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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.value.MaterialNames;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class ExcavationTreasureHunter extends SimpleAdaptation<ExcavationTreasureHunter.Config> {
  private static final Set<String> TREASURE_BLOCKS = Set.of("sand", "red_sand", "gravel", "mud", "clay");
  private volatile TableCache tableCache;

  public ExcavationTreasureHunter() {
    super("excavation-treasure-hunter");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("emerald"));
    setInterval(3370);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("emerald"))
        .key("challenge_excavation_treasure_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_treasure_500", "excavation.treasure-hunter.treasures-found", 500, 500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getTreasureChance(level), 1), 1);
    v.addLore(C.GRAY + AdaptLanguage.text(ExcavationMessages.TREASURE_HUNTER_LORE2));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (!isTreasureBlock(e.block().type())) {
      return;
    }

    PlayerRef p = e.player();
    if (!isShovel(p.inventory().mainHand())) {
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

    TableCache table = getLootTable();
    if (table.entries().isEmpty()) {
      return;
    }

    LootEntry entry = pickWeighted(table, random.nextDouble() * table.totalWeight());
    int amount = entry.min() >= entry.max() ? entry.min() : random.nextInt(entry.min(), entry.max() + 1);
    Loc drop = e.block().center();
    ItemOps items = AdaptCore.platform().host().items();
    e.block().world().dropItemNaturally(drop.position(), items.create(entry.material(), amount));

    boolean rare = entry.weight() <= 6.0D;
    int sparkle = rare ? 14 : 6;
    float pling = rare ? 1.9f : 1.4f;
    fx(drop, FxPriority.TRANSITION)
        .particle(Particles.WAX_ON, sparkle, 0, 0.1D, 0, 0.25D, 0.02D)
        .particle(Particles.itemCrack(items.create(entry.material())), 3, 0, 0.15D, 0, 0.15D, 0.02D)
        .chord(Sounds.ITEM_BRUSH_BRUSHING_SAND_COMPLETE, 0.8f, 1.2f, Sounds.BLOCK_NOTE_BLOCK_PLING, 0.6f, pling);
    if (rare) {
      fx(drop, FxPriority.TRANSITION)
          .particle(Particles.END_ROD, 4, 0, 0.2D, 0, 0.2D, 0.03D)
          .sound(Sounds.ENTITY_PLAYER_LEVELUP, 0.3f, 2.0f);
    }
    addStat(p, "excavation.treasure-hunter.treasures-found", 1);
    xp(p, getConfig().xpPerTreasure);
  }

  private LootEntry pickWeighted(TableCache table, double roll) {
    List<LootEntry> entries = table.entries();
    double remaining = roll;
    for (LootEntry entry : entries) {
      remaining -= entry.weight();
      if (remaining <= 0) {
        return entry;
      }
    }

    return entries.get(entries.size() - 1);
  }

  private TableCache getLootTable() {
    List<String> source = getConfig().lootTable;
    TableCache local = tableCache;
    if (local != null && local.source() == source) {
      return local;
    }

    List<LootEntry> parsed = new ArrayList<>(source.size());
    double totalWeight = 0;
    for (String raw : source) {
      String[] parts = raw.split(":");
      if (parts.length < 2) {
        continue;
      }

      Id material = MaterialNames.match(parts[0].trim());
      if (material == null || !AdaptCore.platform().host().items().isItem(material)) {
        continue;
      }

      double weight = parseDouble(parts[1]);
      if (weight <= 0) {
        continue;
      }

      int min = parts.length > 2 ? Math.max(1, (int) parseDouble(parts[2])) : 1;
      int max = parts.length > 3 ? Math.max(min, (int) parseDouble(parts[3])) : min;
      parsed.add(new LootEntry(material, weight, min, max));
      totalWeight += weight;
    }

    TableCache built = new TableCache(source, parsed, totalWeight);
    tableCache = built;
    return built;
  }

  private double parseDouble(String raw) {
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private boolean isTreasureBlock(Id type) {
    return type.isMinecraft() && TREASURE_BLOCKS.contains(type.path());
  }

  private double getTreasureChance(int level) {
    return Math.min(getConfig().maxTreasureChance, getConfig().treasureChanceBase + (getLevelPercent(level) * getConfig().treasureChanceFactor));
  }


  private record LootEntry(Id material, double weight, int min, int max) {
  }

  private record TableCache(List<String> source, List<LootEntry> entries,
                            double totalWeight) {
  }

  @ConfigDescription("Digging sand, gravel, mud, or clay with a shovel can unearth archaeology treasure.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Treasure Chance Base for the Excavation Treasure Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double treasureChanceBase = 0.01;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Treasure Chance Factor for the Excavation Treasure Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double treasureChanceFactor = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Treasure Chance for the Excavation Treasure Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxTreasureChance = 0.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Weighted loot table entries formatted as MATERIAL:weight:min:max.", impact = "Adding entries or raising weights changes which treasures drop and how often.")
    List<String> lootTable = new ArrayList<>(List.of(
        "BONE:30:1:2",
        "FLINT:30:1:2",
        "CLAY_BALL:15:1:3",
        "ANGLER_POTTERY_SHERD:6:1:1",
        "ARMS_UP_POTTERY_SHERD:6:1:1",
        "SKULL_POTTERY_SHERD:4:1:1",
        "EMERALD:3:1:1"
    ));
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Treasure for the Excavation Treasure Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTreasure = 12;

    public Config() {
      costFactor = 0.7;
      initialCost = 4;
    }
  }
}
