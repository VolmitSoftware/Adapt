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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.world.WorldBlockScanScheduler;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.entity.StackExclusion;
import art.arcane.volmlib.util.inventorygui.Element;
import fr.skytasul.glowingentities.GlowingEntities;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ExcavationSpelunker extends SimpleAdaptation<ExcavationSpelunker.Config> {
  private static final String MARKER_META = "adapt-spelunker-marker";
  private static final int MAX_SCAN_RADIUS = 32;
  private static final int MAX_BLOCK_CHECKS_PER_ACTIVATION = 8192;
  private static final int MAX_HIGHLIGHTS_PER_ACTIVATION = 16;
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, UUID> activeScans = new ConcurrentHashMap<>();

  public ExcavationSpelunker() {
    super("excavation-spelunker");
    registerConfiguration(ExcavationSpelunker.Config.class);
    setIcon(Material.GOLDEN_HELMET);
    setInterval(20388);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPYGLASS)
        .key("challenge_excavation_spelunker_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_ORE)
            .key("challenge_excavation_spelunker_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_excavation_spelunker_1k", "excavation.spelunker.ores-revealed", 1000, 400);
    registerMilestone("challenge_excavation_spelunker_25k", "excavation.spelunker.ores-revealed", 25000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("excavation.spelunker.lore1"));
    v.addLore(C.YELLOW + Localizer.dLocalize("excavation.spelunker.lore2") + getScanRadius(level));
    v.addLore(C.YELLOW + Localizer.dLocalize("excavation.spelunker.lore3"));
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }

    Player p = e.getPlayer();
    if (!hasGlowberries(p) || !hasOreInOffhand(p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!cooldowns.isReady(p.getUniqueId(), (long) (1000 * getConfig().cooldown))) {
      fx(p.getEyeLocation(), FxPriority.TRANSITION)
          .particle(Particle.SMOKE, 2, 0, 0, 0, 0.1D, 0.01D)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);
      return;
    }

    if (activeScans.containsKey(p.getUniqueId())) {
      return;
    }

    int radius = getScanRadius(level);
    Location origin = p.getLocation();
    Material targetOre = p.getInventory().getItemInOffHand().getType();
    startScan(p, origin, targetOre, radius);
    cooldowns.mark(p.getUniqueId());
  }

  private boolean hasGlowberries(Player player) {
    return player.getInventory().getItemInMainHand().getType() == Material.GLOW_BERRIES;
  }

  private void consumeGlowberry(Player player) {
    ItemStack berries = player.getInventory().getItemInMainHand();
    berries.setAmount(berries.getAmount() - 1);
    player.getInventory().setItemInMainHand(berries);
  }

  private boolean hasOreInOffhand(Player player) {
    Material offhandType = player.getInventory().getItemInOffHand().getType();
    return ItemListings.ores.contains(offhandType);
  }

  private void startScan(Player p, Location origin, Material targetOre, int radius) {
    timeline(origin)
        .duration(5)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particle.END_ROD, 0.5D + (2.0D * progress), 16, 1.0D);
          if (tick == 0) {
            fx.chord(Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.6f, 1.0f, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.4f);
          }
        })
        .start();

    int limit = Math.min(MAX_HIGHLIGHTS_PER_ACTIVATION, Math.max(1, getConfig().maxHighlights));
    int checks = Math.min(MAX_BLOCK_CHECKS_PER_ACTIVATION, Math.max(1, getConfig().maxBlockChecks));
    WorldBlockScanScheduler.ScanRequest request = WorldBlockScanScheduler.ScanRequest.builder(origin)
        .radius(radius)
        .denseRadius(getConfig().denseScanRadius)
        .maxSamples(checks)
        .maxResults(limit)
        .seed(ThreadLocalRandom.current().nextInt())
        .matcher(type -> type == targetOre)
        .completion(result -> completeScan(p, targetOre, result))
        .build();
    UUID scanId = WorldBlockScanScheduler.submit(this, p.getUniqueId(), request);
    activeScans.put(p.getUniqueId(), scanId);
  }

  private void completeScan(Player p, Material targetOre, WorldBlockScanScheduler.ScanResult result) {
    UUID playerId = p.getUniqueId();
    if (!result.scanId().equals(activeScans.get(playerId))) {
      return;
    }

    boolean scheduled = J.runEntity(p, () -> {
      if (!activeScans.remove(playerId, result.scanId()) || !p.isOnline()) {
        return;
      }

      List<WorldBlockScanScheduler.Match> matches = result.matches();
      if (matches.isEmpty()) {
        showScanFailure(p);
        return;
      }
      if (!hasGlowberries(p) || p.getInventory().getItemInOffHand().getType() != targetOre) {
        showScanFailure(p);
        return;
      }

      consumeGlowberry(p);
      ChatColor color = ItemListings.oreColorsChatColor.getOrDefault(targetOre, ChatColor.WHITE);
      GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
      for (WorldBlockScanScheduler.Match match : matches) {
        addStat(p, "excavation.spelunker.ores-revealed", 1);
        showOreMarker(p, result.world(), match, targetOre, color, glowingEntities);
      }
    });
    if (!scheduled) {
      activeScans.remove(playerId, result.scanId());
    }
  }

  private void showScanFailure(Player p) {
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .particle(Particle.SMOKE, 4, 0, 0, 0, 0.12D, 0.01D)
        .sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 0.65f);
  }

  private void showOreMarker(Player p, World world, WorldBlockScanScheduler.Match match, Material targetOre,
                             ChatColor color, GlowingEntities glowingEntities) {
    Location center = match.center(world);
    J.runAt(center, () -> {
      if (!world.isChunkLoaded(match.x() >> 4, match.z() >> 4)) {
        return;
      }
      Block block = world.getBlockAt(match.x(), match.y(), match.z());
      if (block.getType() != targetOre) {
        return;
      }

      fx(center, FxPriority.AMBIENT)
          .particle(Particle.WAX_ON, 3, 0, 0, 0, 0.2D, 0.02D);
      if (glowingEntities == null) {
        return;
      }

      Slime slime = world.spawn(new Location(world, match.x() + 0.5D, match.y(), match.z() + 0.5D), Slime.class, s -> {
        StackExclusion.exclude(s);
        s.setPersistent(false);
        s.setRotation(0, 0);
        s.setInvulnerable(true);
        s.setCollidable(false);
        s.setGravity(false);
        s.setSilent(true);
        s.setAI(false);
        s.setSize(2);
        s.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        s.setMetadata(MARKER_META, new FixedMetadataValue(Adapt.instance, true));
      });
      scheduleGlow(p, slime, color, glowingEntities);
    });
  }

  private void scheduleGlow(Player p, Slime slime, ChatColor color, GlowingEntities glowingEntities) {
    if (!J.runEntity(p, () -> setGlowing(glowingEntities, slime, p, color))) {
      J.runEntity(slime, slime::remove);
      return;
    }
    J.runEntity(p, () -> unsetGlowing(glowingEntities, slime, p), (5 * 20) - 1);
    J.runEntity(slime, () -> {
      fx(slime.getLocation().add(0, 0.5, 0), FxPriority.AMBIENT)
          .particle(Particle.SMOKE, 3, 0, 0, 0, 0.15D, 0.01D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.6f);
      slime.remove();
    }, 5 * 20);
  }

  private void setGlowing(GlowingEntities glowingEntities, Slime slime, Player p, ChatColor color) {
    try {
      synchronized (Adapt.glowingEntitiesLock()) {
        glowingEntities.setGlowing(slime, p, color);
      }
    } catch (ReflectiveOperationException error) {
      Adapt.verbose("Failed to enable glowing marker for Spelunker: " + error.getClass().getSimpleName()
          + (error.getMessage() == null ? "" : " - " + error.getMessage()));
      J.runEntity(slime, slime::remove);
    }
  }

  private void unsetGlowing(GlowingEntities glowingEntities, Slime slime, Player p) {
    try {
      synchronized (Adapt.glowingEntitiesLock()) {
        glowingEntities.unsetGlowing(slime, p);
      }
    } catch (ReflectiveOperationException error) {
      Adapt.verbose("Failed to clear glowing marker for Spelunker: " + error.getClass().getSimpleName()
          + (error.getMessage() == null ? "" : " - " + error.getMessage()));
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

  @EventHandler
  public void onEntityDamage(EntityDamageEvent e) {
    if (e.getEntity() instanceof Slime slime
        && e.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION
        && slime.hasMetadata(MARKER_META)) {
      e.setCancelled(true);
    }
  }


  private int getScanRadius(int level) {
    long configuredRadius = (long) getConfig().rangeMultiplier * level;
    return (int) Math.min(MAX_SCAN_RADIUS, Math.max(1L, configuredRadius));
  }

  @ConfigDescription("See ores through the ground using Glowberries in your main hand.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Excavation Spelunker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldown = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Multiplier for the Excavation Spelunker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rangeMultiplier = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum world block checks made by one Spelunker activation.", impact = "Higher values improve target detection but increase total budgeted scan work; values above the hard safety cap are clamped.")
    int maxBlockChecks = 8192;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius searched completely before the remaining Spelunker budget is spread across the full range.", impact = "Higher values prioritize nearby accuracy; lower values reserve more samples for distant target ore.")
    int denseScanRadius = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum ore markers created by one Spelunker scan.", impact = "Higher values reveal more ore at once but create more temporary entities and effects.")
    int maxHighlights = 16;

    public Config() {
      baseCost = 5;
      costFactor = 1;
      initialCost = 10;
    }
  }
}
