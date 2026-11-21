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
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.value.MaterialNames;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class ExcavationGraveDigger extends SimpleAdaptation<ExcavationGraveDigger.Config> {
  private static final DataKey<Byte> GRAVE_MOB_KEY = DataKey.adapt("excavation_grave_mob", DataCodecs.BYTE);
  private static final Set<String> GRAVE_SOIL = Set.of("dirt", "grass_block", "coarse_dirt", "rooted_dirt", "podzol",
      "mycelium", "dirt_path");
  private static final Id BONE_BLOCK = Id.minecraft("bone_block");
  private static final Id SKELETON_SKULL = Id.minecraft("skeleton_skull");
  private final Cooldowns graveCooldowns = cooldowns();
  private volatile TableCache tableCache;

  public ExcavationGraveDigger() {
    super("excavation-grave-digger");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bone"));
    setInterval(4310);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_excavation_gravedigger_300")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_gravedigger_300", "excavation.grave-digger.bones-unearthed", 300, 450);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getLootChance(level), 1), 1);
    statLore(v, C.RED, "+ ", Form.pc(getGraveChance(level), 2), 2);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    if (!CombatXp.isGraveMob(e.entity())) {
      return;
    }

    fx(e.entity().location().add(0, 0.5, 0), FxPriority.TRANSITION)
        .particle(Particles.SOUL, 8, 0, 0.3D, 0, 0.3D, 0.01D)
        .particle(Particles.ASH, 3, 0, 0.2D, 0, 0.2D, 0.01D)
        .sound(Sounds.BLOCK_SOUL_SAND_BREAK, 0.6f, 0.8f);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (!isGraveSoil(e.block().type())) {
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
    Loc center = e.block().center();

    if (random.nextDouble() < getLootChance(level)) {
      dropLoot(p, center, random);
    }

    if (random.nextDouble() < getGraveChance(level)
        && graveCooldowns.isReady(p.uuid(), (long) getConfig().graveCooldownMillis)) {
      graveCooldowns.mark(p.uuid(), (long) getConfig().graveCooldownMillis);
      disturbGrave(p, e.block().location(), random);
    }
  }

  private void dropLoot(PlayerRef p, Loc center, ThreadLocalRandom random) {
    TableCache table = getLootTable();
    if (table.entries().isEmpty()) {
      return;
    }

    LootEntry entry = pickWeighted(table, random.nextDouble() * table.totalWeight());
    int amount = entry.min() >= entry.max() ? entry.min() : random.nextInt(entry.min(), entry.max() + 1);
    ItemOps items = AdaptCore.platform().host().items();
    center.world().dropItemNaturally(center.position(), items.create(entry.material(), amount));
    boolean bony = isBoneLoot(entry.material());
    fx(center, FxPriority.TRANSITION)
        .particle(Particles.ASH, 8, 0, 0, 0, 0.25D, 0.01D)
        .particle(bony ? Particles.SOUL : Particles.ENCHANTMENT_TABLE, bony ? 5 : 3, 0, 0.3D, 0, 0.2D, 0.02D)
        .chord(Sounds.BLOCK_ROOTED_DIRT_BREAK, 0.7f, 0.8f, Sounds.BLOCK_BONE_BLOCK_BREAK, 0.6f, 1.0f);
    addStat(p, "excavation.grave-digger.bones-unearthed", 1);
    xp(p, getConfig().xpPerLoot);
  }

  private boolean isBoneLoot(Id material) {
    return material.equals(BONE_BLOCK) || material.equals(SKELETON_SKULL);
  }

  private void disturbGrave(PlayerRef p, Loc blockLocation, ThreadLocalRandom random) {
    Loc spawnAt = blockLocation.add(0.5, 0, 0.5);
    Id graveType = random.nextBoolean() ? Id.minecraft("zombie") : Id.minecraft("skeleton");
    EntityRef grave = spawnAt.world().spawn(graveType, spawnAt, (EntityRef spawned) -> {
      spawned.data().set(GRAVE_MOB_KEY, (byte) 1);
      if (spawned instanceof MobRef mob) {
        mob.setTarget(p);
      }
    });
    if (grave == null) {
      return;
    }

    BlockStateRef soilData = AdaptCore.platform().host().items().blockState(Id.minecraft("dirt"));
    timeline(grave.location().add(0, 0.1, 0))
        .duration(10)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          if (tick == 0) {
            fx.particle(Particles.blockCrack(soilData), 10, 0, 0.2D, 0, 0.3D, 0.15D)
                .column(Particles.SMOKE, 6, 1.5D)
                .sound(Sounds.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 0.4f);
          }
          if (tick == 5) {
            fx.particle(Particles.SOUL, 14, 0, 1.0D, 0, 0.3D, 0.02D)
                .chord(Sounds.ENTITY_SKELETON_HURT, 0.8f, 0.6f, Sounds.AMBIENT_CAVE, 0.5f, 0.5f);
          }
        })
        .start();
    addStat(p, "excavation.grave-digger.graves-disturbed", 1);
    xp(p, getConfig().xpPerGrave);
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

  private boolean isGraveSoil(Id type) {
    return type.isMinecraft() && GRAVE_SOIL.contains(type.path());
  }

  private double getLootChance(int level) {
    return Math.min(getConfig().maxLootChance, getConfig().lootChanceBase + (getLevelPercent(level) * getConfig().lootChanceFactor));
  }

  private double getGraveChance(int level) {
    return Math.min(getConfig().maxGraveChance, getConfig().graveChanceBase + (getLevelPercent(level) * getConfig().graveChanceFactor));
  }


  private record LootEntry(Id material, double weight, int min, int max) {
  }

  private record TableCache(List<String> source, List<LootEntry> entries,
                            double totalWeight) {
  }

  @ConfigDescription("Digging earthen ground can unearth bone loot, and rarely disturbs a hostile grave.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Loot Chance Base for the Excavation Grave Digger adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lootChanceBase = 0.008;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Loot Chance Factor for the Excavation Grave Digger adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lootChanceFactor = 0.035;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Loot Chance for the Excavation Grave Digger adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxLootChance = 0.045;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Grave Chance Base for the Excavation Grave Digger adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double graveChanceBase = 0.001;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Grave Chance Factor for the Excavation Grave Digger adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double graveChanceFactor = 0.004;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Grave Chance for the Excavation Grave Digger adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxGraveChance = 0.005;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Grave Cooldown Millis for the Excavation Grave Digger adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double graveCooldownMillis = 45000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Weighted loot table entries formatted as MATERIAL:weight:min:max.", impact = "Adding entries or raising weights changes which bone loot drops and how often.")
    List<String> lootTable = new ArrayList<>(List.of(
        "BONE:40:1:2",
        "BONE_MEAL:25:2:4",
        "ROTTEN_FLESH:20:1:2",
        "BONE_BLOCK:6:1:1",
        "SKELETON_SKULL:2:1:1"
    ));
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Loot for the Excavation Grave Digger adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerLoot = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Grave for the Excavation Grave Digger adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerGrave = 35;

    public Config() {
      costFactor = 0.68;
      initialCost = 4;
    }
  }
}
