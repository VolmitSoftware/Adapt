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
import art.arcane.adapt.api.adaptation.Cooldowns;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ExcavationGraveDigger extends SimpleAdaptation<ExcavationGraveDigger.Config> {
  private static final NamespacedKey GRAVE_MOB_KEY = NamespacedKey.fromString("adapt:excavation_grave_mob");
  private final Cooldowns graveCooldowns = cooldowns();
  private volatile TableCache tableCache;

  public static boolean isGraveMob(Entity entity) {
    return entity.getPersistentDataContainer().has(GRAVE_MOB_KEY, PersistentDataType.BYTE);
  }

  public ExcavationGraveDigger() {
    super("excavation-grave-digger");
    registerConfiguration(Config.class);
    setIcon(Material.BONE);
    setInterval(4310);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE)
        .key("challenge_excavation_gravedigger_300")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_gravedigger_300", "excavation.grave-digger.bones-unearthed", 300, 450);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getLootChance(level), 1), 1);
    statLore(v, C.RED, "+ ", Form.pc(getGraveChance(level), 2), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    if (!isGraveMob(e.getEntity())) {
      return;
    }

    fx(e.getEntity().getLocation().add(0, 0.5, 0), FxPriority.TRANSITION)
        .particle(Particle.SOUL, 8, 0, 0.3D, 0, 0.3D, 0.01D)
        .particle(Particle.ASH, 3, 0, 0.2D, 0, 0.2D, 0.01D)
        .sound(Sound.BLOCK_SOUL_SAND_BREAK, 0.6f, 0.8f);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    if (!isGraveSoil(e.getBlock().getType())) {
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
    Location center = e.getBlock().getLocation().add(0.5, 0.5, 0.5);

    if (random.nextDouble() < getLootChance(level)) {
      dropLoot(p, center, random);
    }

    if (random.nextDouble() < getGraveChance(level)
        && graveCooldowns.isReady(p.getUniqueId(), (long) getConfig().graveCooldownMillis)) {
      graveCooldowns.mark(p.getUniqueId());
      disturbGrave(p, e.getBlock().getLocation(), random);
    }
  }

  private void dropLoot(Player p, Location center, ThreadLocalRandom random) {
    TableCache table = getLootTable();
    if (table.entries().isEmpty()) {
      return;
    }

    LootEntry entry = pickWeighted(table, random.nextDouble() * table.totalWeight());
    int amount = entry.min() >= entry.max() ? entry.min() : random.nextInt(entry.min(), entry.max() + 1);
    center.getWorld().dropItemNaturally(center, new ItemStack(entry.material(), amount));
    boolean bony = isBoneLoot(entry.material());
    fx(center, FxPriority.TRANSITION)
        .particle(Particle.ASH, 8, 0, 0, 0, 0.25D, 0.01D)
        .particle(bony ? Particle.SOUL : Particles.ENCHANTMENT_TABLE, bony ? 5 : 3, 0, 0.3D, 0, 0.2D, 0.02D)
        .chord(Sound.BLOCK_ROOTED_DIRT_BREAK, 0.7f, 0.8f, Sound.BLOCK_BONE_BLOCK_BREAK, 0.6f, 1.0f);
    addStat(p, "excavation.grave-digger.bones-unearthed", 1);
    xp(p, getConfig().xpPerLoot);
  }

  private boolean isBoneLoot(Material material) {
    return switch (material) {
      case BONE_BLOCK, SKELETON_SKULL -> true;
      default -> false;
    };
  }

  private void disturbGrave(Player p, Location blockLocation, ThreadLocalRandom random) {
    Location spawnAt = blockLocation.add(0.5, 0, 0.5);
    Monster grave = random.nextBoolean()
        ? spawnAt.getWorld().spawn(spawnAt, Zombie.class, z -> {
          z.getPersistentDataContainer().set(GRAVE_MOB_KEY, PersistentDataType.BYTE, (byte) 1);
          z.setTarget(p);
        })
        : spawnAt.getWorld().spawn(spawnAt, Skeleton.class, s -> {
          s.getPersistentDataContainer().set(GRAVE_MOB_KEY, PersistentDataType.BYTE, (byte) 1);
          s.setTarget(p);
        });

    BlockData soilData = Material.DIRT.createBlockData();
    timeline(grave.getLocation().add(0, 0.1, 0))
        .duration(10)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          if (tick == 0) {
            fx.particle(Particles.BLOCK_CRACK, 10, 0, 0.2D, 0, 0.3D, 0.15D, soilData)
                .column(Particles.SMOKE, 6, 1.5D)
                .sound(Sound.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 0.4f);
          }
          if (tick == 5) {
            fx.particle(Particle.SOUL, 14, 0, 1.0D, 0, 0.3D, 0.02D)
                .chord(Sound.ENTITY_SKELETON_HURT, 0.8f, 0.6f, Sound.AMBIENT_CAVE, 0.5f, 0.5f);
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

  private boolean isGraveSoil(Material type) {
    return switch (type) {
      case DIRT, GRASS_BLOCK, COARSE_DIRT, ROOTED_DIRT, PODZOL, MYCELIUM,
           DIRT_PATH -> true;
      default -> false;
    };
  }

  private double getLootChance(int level) {
    return Math.min(getConfig().maxLootChance, getConfig().lootChanceBase + (getLevelPercent(level) * getConfig().lootChanceFactor));
  }

  private double getGraveChance(int level) {
    return Math.min(getConfig().maxGraveChance, getConfig().graveChanceBase + (getLevelPercent(level) * getConfig().graveChanceFactor));
  }

  @Override
  public void onTick() {

  }

  private record LootEntry(Material material, double weight, int min, int max) {
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
