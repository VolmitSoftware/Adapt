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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ExcavationMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ExcavationTreasureHunter extends SimpleAdaptation<ExcavationTreasureHunter.Config> {
  private volatile TableCache tableCache;

  public ExcavationTreasureHunter() {
    super("excavation-treasure-hunter");
    registerConfiguration(Config.class);
    setIcon(Material.EMERALD);
    setInterval(3370);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.EMERALD)
        .key("challenge_excavation_treasure_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_treasure_500", "excavation.treasure-hunter.treasures-found", 500, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getTreasureChance(level), 1), 1);
    v.addLore(C.GRAY + AdaptLanguage.text(ExcavationMessages.TREASURE_HUNTER_LORE2));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    if (!isTreasureBlock(e.getBlock().getType())) {
      return;
    }

    Player p = e.getPlayer();
    if (!isShovel(p.getInventory().getItemInMainHand())) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.getBlock().getLocation());
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
    Location drop = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
    e.getBlock().getWorld().dropItemNaturally(drop, new ItemStack(entry.material(), amount));

    boolean rare = entry.weight() <= 6.0D;
    int sparkle = rare ? 14 : 6;
    float pling = rare ? 1.9f : 1.4f;
    fx(drop, FxPriority.TRANSITION)
        .particle(Particle.WAX_ON, sparkle, 0, 0.1D, 0, 0.25D, 0.02D)
        .particle(Particles.ITEM_CRACK, 3, 0, 0.15D, 0, 0.15D, 0.02D, new ItemStack(entry.material()))
        .chord(Sound.ITEM_BRUSH_BRUSHING_SAND_COMPLETE, 0.8f, 1.2f, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, pling);
    if (rare) {
      fx(drop, FxPriority.TRANSITION)
          .particle(Particle.END_ROD, 4, 0, 0.2D, 0, 0.2D, 0.03D)
          .sound(Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 2.0f);
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

      Material material = Material.matchMaterial(parts[0].trim());
      if (material == null) {
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

  private boolean isTreasureBlock(Material type) {
    return switch (type) {
      case SAND, RED_SAND, GRAVEL, MUD, CLAY -> true;
      default -> false;
    };
  }

  private double getTreasureChance(int level) {
    return Math.min(getConfig().maxTreasureChance, getConfig().treasureChanceBase + (getLevelPercent(level) * getConfig().treasureChanceFactor));
  }


  private record LootEntry(Material material, double weight, int min, int max) {
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
