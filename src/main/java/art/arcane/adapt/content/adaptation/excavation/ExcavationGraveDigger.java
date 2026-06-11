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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ExcavationGraveDigger extends SimpleAdaptation<ExcavationGraveDigger.Config> {
  private static final NamespacedKey GRAVE_MOB_KEY = NamespacedKey.fromString("adapt:excavation_grave_mob");
  private final Map<UUID, Long> graveCooldowns = new ConcurrentHashMap<>();
  private volatile TableCache tableCache;

  public static boolean isGraveMob(Entity entity) {
    return entity.getPersistentDataContainer().has(GRAVE_MOB_KEY, PersistentDataType.BYTE);
  }

  public ExcavationGraveDigger() {
    super("excavation-grave-digger");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("excavation.grave_digger.description"));
    setDisplayName(Localizer.dLocalize("excavation.grave_digger.name"));
    setIcon(Material.BONE);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(4310);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE)
        .key("challenge_excavation_gravedigger_300")
        .title(Localizer.dLocalize("advancement.challenge_excavation_gravedigger_300.title"))
        .description(Localizer.dLocalize("advancement.challenge_excavation_gravedigger_300.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_gravedigger_300", "excavation.grave-digger.bones-unearthed", 300, 450);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getLootChance(level), 1) + C.GRAY + " " + Localizer.dLocalize("excavation.grave_digger.lore1"));
    v.addLore(C.RED + "+ " + Form.pc(getGraveChance(level), 2) + C.GRAY + " " + Localizer.dLocalize("excavation.grave_digger.lore2"));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    graveCooldowns.remove(e.getPlayer().getUniqueId());
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

    if (random.nextDouble() < getGraveChance(level)) {
      long now = System.currentTimeMillis();
      long nextReady = graveCooldowns.getOrDefault(p.getUniqueId(), 0L);
      if (now >= nextReady) {
        graveCooldowns.put(p.getUniqueId(), now + (long) getConfig().graveCooldownMillis);
        disturbGrave(p, e.getBlock().getLocation(), random);
      }
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
    if (areParticlesEnabled()) {
      p.spawnParticle(Particle.ASH, center, 8, 0.25, 0.25, 0.25, 0.01);
    }

    SoundPlayer.of(p.getWorld()).play(center, Sound.BLOCK_ROOTED_DIRT_BREAK, 0.7f, 0.8f);
    getPlayer(p).getData().addStat("excavation.grave-digger.bones-unearthed", 1);
    xp(p, getConfig().xpPerLoot);
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

    if (areParticlesEnabled()) {
      p.spawnParticle(Particle.SOUL, grave.getLocation().add(0, 1, 0), 14, 0.3, 0.5, 0.3, 0.02);
    }

    SoundPlayer sp = SoundPlayer.of(p.getWorld());
    sp.play(spawnAt, Sound.ENTITY_SKELETON_HURT, 0.8f, 0.6f);
    sp.play(spawnAt, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 0.5f);
    getPlayer(p).getData().addStat("excavation.grave-digger.graves-disturbed", 1);
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

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  private record LootEntry(Material material, double weight, int min, int max) {
  }

  private record TableCache(List<String> source, List<LootEntry> entries,
                            double totalWeight) {
  }

  @NoArgsConstructor
  @ConfigDescription("Digging earthen ground can unearth bone loot, and rarely disturbs a hostile grave.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.68;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
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
  }
}
