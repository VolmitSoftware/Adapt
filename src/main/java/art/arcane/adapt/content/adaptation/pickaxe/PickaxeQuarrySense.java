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

package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.content.integration.hiddenore.HiddenOreLink;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.world.WorldBlockScanScheduler;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PickaxeQuarrySense extends SimpleAdaptation<PickaxeQuarrySense.Config> {
  private static final int MAX_SCAN_RADIUS = 32;
  private static final int MAX_BLOCK_CHECKS_PER_ACTIVATION = 4096;
  private static final int MAX_HIGHLIGHTS_PER_ACTIVATION = 16;
  private final Map<UUID, UUID> activeScans = new ConcurrentHashMap<>();

  public PickaxeQuarrySense() {
    super("pickaxe-quarry-sense");
    registerConfiguration(Config.class);
    setIcon(Material.MAP);
    setInterval(1200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPYGLASS)
        .key("challenge_pickaxe_quarry_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_quarry_200", "pickaxe.quarry-sense.scans", 200, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getScanRadius(level)), 1);
    statLore(v, Form.pc(getDurabilityCostPercent(level), 2), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
      return;
    }

    if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isEligiblePickaxe(hand) || p.hasCooldown(hand.getType())) {
      return;
    }

    if (activeScans.containsKey(p.getUniqueId())) {
      e.setCancelled(true);
      return;
    }

    fx(p.getEyeLocation(), FxPriority.GAMEPLAY)
        .particle(Particles.ENCHANTMENT_TABLE, 14, 0, 0, 0, 0.22, 0.15)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.35f);

    int durabilityCost = getDurabilityCost(hand, level);
    if (!canApplyPickaxeCost(hand, durabilityCost)) {
      showPickaxeCostFailure(p);
      return;
    }

    int scanRadius = getScanRadius(level);
    startScan(p, p.getLocation(), hand.getType(), level, scanRadius);
    e.setCancelled(true);
  }

  private void startScan(Player p, Location origin, Material pickaxeType, int level, int radius) {
    World world = origin.getWorld();
    if (world == null) {
      return;
    }

    ArrayList<WorldBlockScanScheduler.AdditionalMatch> hiddenMatches = new ArrayList<>();
    for (HiddenOreLink.VeinTarget vein : HiddenOreLink.veins(origin, radius)) {
      Location at = vein.location();
      if (at.getWorld() != world
          || at.getBlockY() < world.getMinHeight()
          || at.getBlockY() >= world.getMaxHeight()) {
        continue;
      }
      double dx = at.getX() - origin.getX();
      double dy = at.getY() - origin.getY();
      double dz = at.getZ() - origin.getZ();
      double distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);
      if (distanceSquared <= (double) radius * radius) {
        hiddenMatches.add(new WorldBlockScanScheduler.AdditionalMatch(
            at.getBlockX(), at.getBlockY(), at.getBlockZ(), vein.display(), distanceSquared
        ));
      }
    }

    int checks = Math.min(MAX_BLOCK_CHECKS_PER_ACTIVATION, Math.max(1, getConfig().maxBlockChecks));
    WorldBlockScanScheduler.ScanRequest request = WorldBlockScanScheduler.ScanRequest.builder(origin)
        .radius(radius)
        .denseRadius(getConfig().denseScanRadius)
        .maxSamples(checks)
        .maxResults(getMaxHighlights(level))
        .seed(ThreadLocalRandom.current().nextInt())
        .additionalMatches(hiddenMatches)
        .matcher(this::isQuarryOre)
        .completion(result -> completeScan(p, pickaxeType, radius, result))
        .build();
    UUID scanId = WorldBlockScanScheduler.submit(this, p.getUniqueId(), request);
    activeScans.put(p.getUniqueId(), scanId);
  }

  private void completeScan(Player p, Material pickaxeType, int scanRadius,
                            WorldBlockScanScheduler.ScanResult result) {
    UUID playerId = p.getUniqueId();
    if (!result.scanId().equals(activeScans.get(playerId))) {
      return;
    }

    boolean scheduled = J.runEntity(p, () -> {
      if (!activeScans.remove(playerId, result.scanId()) || !p.isOnline()) {
        return;
      }

      int activeLevel = getActiveLevel(p);
      if (!canCompleteScan(activeLevel, result.world(), p.getWorld())) {
        return;
      }

      List<WorldBlockScanScheduler.Match> ores = result.matches();
      int cooldownTicks = getCooldownTicks(activeLevel);
      if (ores.isEmpty()) {
        showEmptyScan(p);
        p.setCooldown(pickaxeType, cooldownTicks);
        return;
      }

      ItemStack currentHand = p.getInventory().getItemInMainHand();
      int durabilityCost = getDurabilityCost(currentHand, activeLevel);
      if (currentHand.getType() != pickaxeType
          || !isEligiblePickaxe(currentHand)
          || !canApplyPickaxeCost(currentHand, durabilityCost)
          || !applyPickaxeCost(p, currentHand, durabilityCost)) {
        showPickaxeCostFailure(p);
        p.setCooldown(pickaxeType, cooldownTicks);
        return;
      }

      int highlightTicks = getHighlightTicks(activeLevel);
      for (WorldBlockScanScheduler.Match ore : ores) {
        showOreMarker(p, result.world(), ore, highlightTicks);
      }
      p.setCooldown(pickaxeType, cooldownTicks);
      showSuccessfulScan(p, scanRadius, ores.size());
    });
    if (!scheduled) {
      activeScans.remove(playerId, result.scanId());
    }
  }

  static boolean canCompleteScan(int activeLevel, World scannedWorld, World currentWorld) {
    return activeLevel > 0 && scannedWorld != null && scannedWorld.equals(currentWorld);
  }

  private void showEmptyScan(Player p) {
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 12, 0, 0, 0, 0.22, 0.02)
        .ring(Particles.SMOKE, 0.6D, 10, 0)
        .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.75f);
  }

  private void showSuccessfulScan(Player p, int scanRadius, int oreCount) {
    timeline(p.getLocation().add(0, 1, 0))
        .duration(8)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(Math.min(48, scanRadius + 8))
        .frame((fxE, tick, progress) -> {
          fxE.dome(Particle.GLOW, scanRadius * 0.5D * progress, Math.min(48, 12 + scanRadius));
          if (tick == 0 || tick == 3 || tick == 6) {
            fxE.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, (float) (1.0D + (progress * 0.8D)));
          }
        })
        .start();
    fx(p.getLocation(), FxPriority.GAMEPLAY).sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.6f);
    xp(p, oreCount * getConfig().xpPerFoundOre);
    addStat(p, "pickaxe.quarry-sense.scans", 1);
  }

  private void showOreMarker(Player p, World world, WorldBlockScanScheduler.Match ore, int durationTicks) {
    Location location = new Location(world, ore.x(), ore.y(), ore.z());
    J.runAt(location, () -> showOreMarkerAtRegion(p, world, ore, location, durationTicks));
  }

  private void showOreMarkerAtRegion(Player p, World world, WorldBlockScanScheduler.Match ore, Location location,
                                     int durationTicks) {
    if (!world.isChunkLoaded(ore.x() >> 4, ore.z() >> 4)) {
      return;
    }
    if (!ore.supplied() && world.getBlockAt(ore.x(), ore.y(), ore.z()).getType() != ore.material()) {
      return;
    }

    ViewerDisplayDirector.showBlock(
        getName(),
        "ore-" + ore.x() + ":" + ore.y() + ":" + ore.z(),
        p,
        location,
        ore.material().createBlockData(),
        oreColor(ore.material()),
        durationTicks
    );
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.getPlayer().getUniqueId();
    activeScans.remove(playerId);
    WorldBlockScanScheduler.cancel(this, playerId);
    ViewerDisplayDirector.clearViewer(getName(), playerId);
  }

  @Override
  public void unregister() {
    activeScans.clear();
    WorldBlockScanScheduler.cancelOwner(this);
    ViewerDisplayDirector.clearChannel(getName());
    super.unregister();
  }

  static Color oreColor(Material material) {
    String name = material.name();
    if (name.contains("REDSTONE")) {
      return Color.fromRGB(255, 60, 60);
    }
    if (name.contains("DIAMOND")) {
      return Color.fromRGB(90, 230, 235);
    }
    if (name.contains("EMERALD")) {
      return Color.fromRGB(70, 230, 100);
    }
    if (name.contains("GOLD")) {
      return Color.fromRGB(255, 205, 55);
    }
    if (name.contains("LAPIS")) {
      return Color.fromRGB(55, 105, 230);
    }
    if (name.contains("COPPER")) {
      return Color.fromRGB(220, 120, 75);
    }
    if (name.contains("IRON")) {
      return Color.fromRGB(225, 205, 185);
    }
    return Color.fromRGB(170, 220, 235);
  }

  private boolean isEligiblePickaxe(ItemStack hand) {
    if (!isItem(hand)) {
      return false;
    }

    return switch (hand.getType()) {
      case IRON_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE -> true;
      default -> false;
    };
  }

  private boolean isQuarryOre(Material type) {
    return switch (type) {
      case COPPER_ORE, DEEPSLATE_COPPER_ORE, COAL_ORE, GOLD_ORE, IRON_ORE,
           DIAMOND_ORE, LAPIS_ORE, EMERALD_ORE, NETHER_QUARTZ_ORE,
           NETHER_GOLD_ORE, REDSTONE_ORE, DEEPSLATE_COAL_ORE,
           DEEPSLATE_IRON_ORE, DEEPSLATE_GOLD_ORE, DEEPSLATE_LAPIS_ORE,
           DEEPSLATE_DIAMOND_ORE, DEEPSLATE_EMERALD_ORE,
           DEEPSLATE_REDSTONE_ORE -> true;
      default -> false;
    };
  }

  private boolean canApplyPickaxeCost(ItemStack hand, int durabilityCost) {
    if (!(hand.getItemMeta() instanceof Damageable damageable)) {
      return false;
    }
    if (!getConfig().costsReduceMaxDurability) {
      return (long) damageable.getDamage() + durabilityCost < hand.getType().getMaxDurability();
    }

    int fallbackMax = Math.max(1, hand.getType().getMaxDurability());
    int currentMax = damageable.hasMaxDamage() ? Math.max(1, damageable.getMaxDamage()) : fallbackMax;
    int currentDamage = Math.max(0, damageable.getDamage());
    return (long) currentMax - durabilityCost > currentDamage + 1L;
  }

  private void showPickaxeCostFailure(Player p) {
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 8, 0, 0, 0, 0.2, 0.03)
        .dustBurst(Color.fromRGB(0x555555), 6, 0.2, 1.0f)
        .sound(Sound.BLOCK_ANVIL_PLACE, 0.5f, 0.55f);
  }

  private boolean applyPickaxeCost(Player p, ItemStack hand, int durabilityCost) {
    if (getConfig().costsReduceMaxDurability) {
      return tryReduceMaxDurability(p, hand, durabilityCost);
    }

    return tryDamagePickaxe(p, hand, durabilityCost);
  }

  private boolean tryDamagePickaxe(Player p, ItemStack hand, int durabilityCost) {
    if (!(hand.getItemMeta() instanceof Damageable damageable)) {
      return false;
    }

    int maxDurability = hand.getType().getMaxDurability();
    int currentDamage = damageable.getDamage();
    if (currentDamage + durabilityCost >= maxDurability) {
      return false;
    }

    damageable.setDamage(currentDamage + durabilityCost);
    hand.setItemMeta(damageable);
    p.getInventory().setItemInMainHand(hand);
    return true;
  }

  private boolean tryReduceMaxDurability(Player p, ItemStack hand, int durabilityCost) {
    if (!(hand.getItemMeta() instanceof Damageable damageable)) {
      return false;
    }

    int fallbackMax = Math.max(1, hand.getType().getMaxDurability());
    int currentMax = damageable.hasMaxDamage() ? Math.max(1, damageable.getMaxDamage()) : fallbackMax;
    int currentDamage = Math.max(0, damageable.getDamage());
    int newMax = currentMax - durabilityCost;
    if (newMax <= currentDamage + 1) {
      return false;
    }

    damageable.setMaxDamage(Math.max(1, newMax));
    hand.setItemMeta(damageable);
    p.getInventory().setItemInMainHand(hand);
    return true;
  }

  private int getScanRadius(int level) {
    return Math.min(MAX_SCAN_RADIUS, Math.max(4, (int) Math.round(getConfig().scanRadiusBase + (getLevelPercent(level) * getConfig().scanRadiusFactor))));
  }

  private int getMaxHighlights(int level) {
    return Math.min(MAX_HIGHLIGHTS_PER_ACTIVATION, Math.max(1, (int) Math.round(getConfig().maxHighlightsBase + (getLevelPercent(level) * getConfig().maxHighlightsFactor))));
  }

  private int getHighlightTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().highlightTicksBase + (getLevelPercent(level) * getConfig().highlightTicksFactor)));
  }

  private int getCooldownTicks(int level) {
    return Math.max(10, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  private int getDurabilityCost(ItemStack hand, int level) {
    int maxDurability = Math.max(1, hand.getType().getMaxDurability());
    return Math.max(1, (int) Math.round(maxDurability * getDurabilityCostPercent(level)));
  }

  private double getDurabilityCostPercent(int level) {
    return Math.max(getConfig().minDurabilityCostPercent,
        getConfig().durabilityCostPercentBase - (getLevelPercent(level) * getConfig().durabilityCostPercentFactor));
  }


  @ConfigDescription("Sneak-right-click a block with an iron+ pickaxe to reveal nearby ores as private glowing block displays.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Costs Reduce Max Durability for the Pickaxe Quarry Sense adaptation.", impact = "True reduces max durability instead of adding normal damage.")
    boolean costsReduceMaxDurability = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Scan Radius Base for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double scanRadiusBase = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Scan Radius Factor for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double scanRadiusFactor = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum world block checks made by one Quarry Sense activation.", impact = "Higher values improve ore detection but increase total budgeted scan work; values above the hard safety cap are clamped.")
    int maxBlockChecks = 2048;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius searched completely before the remaining Quarry Sense budget is spread across the full range.", impact = "Higher values prioritize nearby accuracy; lower values reserve more samples for distant ore.")
    int denseScanRadius = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Highlights Base for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHighlightsBase = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Highlights Factor for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHighlightsFactor = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Highlight Ticks Base for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double highlightTicksBase = 90;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Highlight Ticks Factor for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double highlightTicksFactor = 90;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Percent Base for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durabilityCostPercentBase = 0.006;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Percent Factor for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durabilityCostPercentFactor = 0.0045;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Durability Cost Percent for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minDurabilityCostPercent = 0.001;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Found Ore for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerFoundOre = 6;

    public Config() {
      costFactor = 0.7;
      initialCost = 4;
    }
  }
}
