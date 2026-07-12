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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.integration.hiddenore.HiddenOreLink;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.world.WorldBlockScanScheduler;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.entity.StackExclusion;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import fr.skytasul.glowingentities.GlowingEntities;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PickaxeQuarrySense extends SimpleAdaptation<PickaxeQuarrySense.Config> {
  private static final String MARKER_META = "adapt-quarry-sense-marker";
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
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_pickaxe_quarry_200", "pickaxe.quarry-sense.scans", 200, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getScanRadius(level)), 1);
    statLore(v, Form.pc(getDurabilityCostPercent(level), 2), 2);
    v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("pickaxe.quarry_sense.lore3"));
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

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (e.getEntity() instanceof Slime slime && slime.hasMetadata(MARKER_META)) {
      e.setCancelled(true);
    }
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
        .completion(result -> completeScan(p, pickaxeType, level, radius, result))
        .build();
    UUID scanId = WorldBlockScanScheduler.submit(this, p.getUniqueId(), request);
    activeScans.put(p.getUniqueId(), scanId);
  }

  private void completeScan(Player p, Material pickaxeType, int level, int scanRadius,
                            WorldBlockScanScheduler.ScanResult result) {
    UUID playerId = p.getUniqueId();
    if (!result.scanId().equals(activeScans.get(playerId))) {
      return;
    }

    boolean scheduled = J.runEntity(p, () -> {
      if (!activeScans.remove(playerId, result.scanId()) || !p.isOnline()) {
        return;
      }

      List<WorldBlockScanScheduler.Match> ores = result.matches();
      int cooldownTicks = getCooldownTicks(level);
      if (ores.isEmpty()) {
        showEmptyScan(p);
        p.setCooldown(pickaxeType, cooldownTicks);
        return;
      }

      ItemStack currentHand = p.getInventory().getItemInMainHand();
      int durabilityCost = getDurabilityCost(currentHand, level);
      if (currentHand.getType() != pickaxeType
          || !isEligiblePickaxe(currentHand)
          || !canApplyPickaxeCost(currentHand, durabilityCost)
          || !applyPickaxeCost(p, currentHand, durabilityCost)) {
        showPickaxeCostFailure(p);
        p.setCooldown(pickaxeType, cooldownTicks);
        return;
      }

      int highlightTicks = getHighlightTicks(level);
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
    Location center = ore.center(world);
    J.runAt(center, () -> showOreMarkerAtRegion(p, world, ore, center, durationTicks));
  }

  private void showOreMarkerAtRegion(Player p, World world, WorldBlockScanScheduler.Match ore, Location center,
                                     int durationTicks) {
    if (!world.isChunkLoaded(ore.x() >> 4, ore.z() >> 4)) {
      return;
    }
    if (!ore.supplied() && world.getBlockAt(ore.x(), ore.y(), ore.z()).getType() != ore.material()) {
      return;
    }

    GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
    if (glowingEntities == null) {
      showFallbackMarker(p, center, durationTicks);
      return;
    }

    Slime slime;
    try {
      slime = world.spawn(center, Slime.class, s -> {
        StackExclusion.exclude(s);
        s.setPersistent(false);
        s.setInvulnerable(true);
        s.setCollidable(false);
        s.setGravity(false);
        s.setSilent(true);
        s.setAI(false);
        s.setSize(2);
        s.setRotation(0, 0);
        s.setMetadata(MARKER_META, new FixedMetadataValue(Adapt.instance, true));
        s.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
      });
    } catch (Throwable error) {
      Adapt.verbose("Failed to spawn glowing marker for QuarrySense: "
          + error.getClass().getSimpleName()
          + (error.getMessage() == null ? "" : " - " + error.getMessage()));
      showFallbackMarker(p, center, durationTicks);
      return;
    }

    if (!J.runEntity(p, () -> enableGlow(glowingEntities, slime, p, center, durationTicks))) {
      J.runEntity(slime, slime::remove);
      return;
    }

    fx(center, FxPriority.GAMEPLAY)
        .ring(Particles.END_ROD, 0.6D, 10, 0.3D)
        .particle(Particle.GLOW, 8, 0, 0, 0, 0.2, 0.001)
        .sound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.25f, 1.4f);
    J.runEntity(p, () -> disableGlow(glowingEntities, slime, p), Math.max(1, durationTicks - 1));
    J.runEntity(slime, () -> {
      slime.remove();
    }, durationTicks);
  }

  private void enableGlow(GlowingEntities glowingEntities, Slime slime, Player p, Location center, int durationTicks) {
    try {
      synchronized (Adapt.glowingEntitiesLock()) {
        glowingEntities.setGlowing(slime, p, ChatColor.AQUA);
      }
    } catch (Throwable error) {
      Adapt.verbose("Failed to enable glowing marker for QuarrySense: "
          + error.getClass().getSimpleName()
          + (error.getMessage() == null ? "" : " - " + error.getMessage()));
      J.runEntity(slime, slime::remove);
      showFallbackMarker(p, center, durationTicks);
    }
  }

  private void disableGlow(GlowingEntities glowingEntities, Slime slime, Player p) {
    try {
      synchronized (Adapt.glowingEntitiesLock()) {
        glowingEntities.unsetGlowing(slime, p);
      }
    } catch (Throwable error) {
      Adapt.verbose("Failed to clear glowing marker for QuarrySense: "
          + error.getClass().getSimpleName()
          + (error.getMessage() == null ? "" : " - " + error.getMessage()));
    }
  }

  private void showFallbackMarker(Player p, Location loc, int durationTicks) {
    for (int t = 0; t <= durationTicks; t += 8) {
      J.runEntity(p, () -> {
        if (!p.isOnline()) {
          return;
        }

        fx(loc, FxPriority.AMBIENT)
            .particle(Particle.GLOW, 14, 0, 0, 0, 0.22, 0.001)
            .particle(Particles.END_ROD, 4, 0, 0, 0, 0.12, 0.001);
      }, t);
    }
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.getPlayer().getUniqueId();
    activeScans.remove(playerId);
    WorldBlockScanScheduler.cancel(this, playerId);
  }

  @Override
  public void unregister() {
    activeScans.clear();
    WorldBlockScanScheduler.cancelOwner(this);
    super.unregister();
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


  @ConfigDescription("Sneak-right-click a block with an iron+ pickaxe to highlight nearby ores.")
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
