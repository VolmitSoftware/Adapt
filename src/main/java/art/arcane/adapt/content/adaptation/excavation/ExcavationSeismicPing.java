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
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.integration.hiddenore.HiddenOreLink;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.world.WorldBlockScanScheduler;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExcavationSeismicPing extends SimpleAdaptation<ExcavationSeismicPing.Config> {
  private static final int MAX_SCAN_RANGE = 32;
  private static final int MAX_BLOCK_CHECKS_PER_ACTIVATION = 2048;
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, UUID> activeScans = new ConcurrentHashMap<>();
  private final Map<UUID, DisplayHandle> activeLines = new ConcurrentHashMap<>();
  private final AtomicBoolean acceptingLines = new AtomicBoolean(true);
  private final Object lineLifecycleLock = new Object();

  public ExcavationSeismicPing() {
    super("excavation-seismic-ping");
    registerConfiguration(Config.class);
    setIcon(Material.GOAT_HORN);
    setInterval(2200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BELL)
        .key("challenge_excavation_seismic_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_seismic_200", "excavation.seismic-ping.pings-triggered", 200, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getScanRange(level), 1);
    statLore(v, Form.pc(getPingChance(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    if (!isExcavationTool(p.getInventory().getItemInMainHand())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();
    if (!cooldowns.isReady(p.getUniqueId(), getCooldownMillis(level))) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getPingChance(level)) {
      return;
    }

    if (activeScans.containsKey(p.getUniqueId())) {
      return;
    }

    cooldowns.mark(p.getUniqueId());
    Location blockLocation = e.getBlock().getLocation();
    int scanRange = getScanRange(level);
    startScan(p, blockLocation, scanRange, level);
  }

  private void startScan(Player p, Location origin, int scanRange, int level) {
    World world = origin.getWorld();
    if (world == null) {
      return;
    }

    ArrayList<WorldBlockScanScheduler.AdditionalMatch> hiddenMatches = new ArrayList<>(1);
    HiddenOreLink.VeinTarget hidden = HiddenOreLink.nearestVein(origin, scanRange);
    if (hidden != null && hidden.location().getWorld() == world) {
      Location at = hidden.location();
      double dx = at.getX() - origin.getX();
      double dy = at.getY() - origin.getY();
      double dz = at.getZ() - origin.getZ();
      double distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);
      if (at.getBlockY() >= world.getMinHeight()
          && at.getBlockY() < world.getMaxHeight()
          && distanceSquared <= (double) scanRange * scanRange) {
        hiddenMatches.add(new WorldBlockScanScheduler.AdditionalMatch(
            at.getBlockX(), at.getBlockY(), at.getBlockZ(), hidden.display(), distanceSquared
        ));
      }
    }

    int checks = Math.min(MAX_BLOCK_CHECKS_PER_ACTIVATION, Math.max(1, getConfig().maxBlockChecks));
    WorldBlockScanScheduler.ScanRequest request = WorldBlockScanScheduler.ScanRequest.builder(origin)
        .radius(scanRange)
        .denseRadius(getConfig().denseScanRadius)
        .maxSamples(checks)
        .maxResults(1)
        .seed(ThreadLocalRandom.current().nextInt())
        .additionalMatches(hiddenMatches)
        .matcher(this::isOre)
        .completion(result -> completeScan(p, origin, scanRange, level, result))
        .build();
    UUID scanId = WorldBlockScanScheduler.submit(this, p.getUniqueId(), request);
    activeScans.put(p.getUniqueId(), scanId);
  }

  private void completeScan(Player p, Location blockLocation, int scanRange, int level,
                            WorldBlockScanScheduler.ScanResult result) {
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
        fx(blockLocation.clone().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
            .sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, 0.5f);
        return;
      }

      WorldBlockScanScheduler.Match target = matches.get(0);
      Location targetCenter = target.center(result.world());
      Location playerOrigin = p.getEyeLocation();
      Vector direction = targetCenter.toVector().subtract(playerOrigin.toVector());
      if (direction.lengthSquared() <= 0.0000001) {
        return;
      }

      Color tint = oreTint(target.material());
      renderDirectionHint(p, playerOrigin, direction.normalize(), getHintSegments(level), tint);
      playPingSound(p, playerOrigin.distance(targetCenter), scanRange);
      addStat(p, "excavation.seismic-ping.pings-triggered", 1);
      xp(p, getConfig().xpPerPing + (getValue(target.material()) * getConfig().targetValueXpMultiplier));
    });
    if (!scheduled) {
      activeScans.remove(playerId, result.scanId());
    }
  }

  private Color oreTint(Material type) {
    if (type == null) {
      return Color.fromRGB(110, 230, 255);
    }

    String name = type.name();
    if (name.contains("REDSTONE")) {
      return Color.fromRGB(255, 60, 60);
    }
    if (name.contains("DIAMOND")) {
      return Color.fromRGB(90, 230, 235);
    }
    if (name.contains("GOLD")) {
      return Color.fromRGB(255, 215, 70);
    }
    if (name.contains("IRON") || name.contains("COPPER")) {
      return Color.fromRGB(210, 180, 150);
    }
    if (name.contains("EMERALD")) {
      return Color.fromRGB(70, 230, 130);
    }
    if (name.contains("LAPIS")) {
      return Color.fromRGB(60, 110, 240);
    }
    return Color.fromRGB(110, 230, 255);
  }

  private void renderDirectionHint(Player player, Location origin, Vector direction, int segments, Color tint) {
    if (!acceptingLines.get()) {
      return;
    }
    int total = Math.max(2, segments);
    float length = lineLength(total, getConfig().segmentSpacing);
    float thickness = (float) Math.max(0.04D, Math.min(0.5D, getConfig().lineThickness));
    Quaternionf rotation = new Quaternionf().rotationTo(
        0f, 0f, 1f,
        (float) direction.getX(), (float) direction.getY(), (float) direction.getZ()
    );
    Vector3f centeredOffset = new Vector3f(-thickness * 0.5F, -thickness * 0.5F, 0F);
    rotation.transform(centeredOffset);
    Transformation transformation = new Transformation(
        centeredOffset, rotation, new Vector3f(thickness, thickness, length), new Quaternionf()
    );
    Location lineOrigin = origin.clone().add(direction.clone().multiply(0.65D));

    BlockDisplay line;
    try {
      line = lineOrigin.getWorld().spawn(lineOrigin, BlockDisplay.class, display -> {
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setSilent(true);
        display.setVisibleByDefault(false);
        display.setViewRange((float) Math.max(0.5D, Math.min(2D, getConfig().lineViewRange)));
        display.setShadowRadius(0f);
        display.setShadowStrength(0f);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setDisplayWidth(length);
        display.setDisplayHeight(Math.max(1f, length));
        display.setBlock(Material.WHITE_STAINED_GLASS.createBlockData());
        display.setTransformation(transformation);
        display.setGlowColorOverride(tint);
        display.setGlowing(true);
      });
    } catch (Throwable error) {
      Adapt.verbose("Failed to spawn glowing direction line for Seismic Ping: "
          + error.getClass().getSimpleName()
          + (error.getMessage() == null ? "" : " - " + error.getMessage()));
      error.printStackTrace();
      return;
    }

    UUID lineId = line.getUniqueId();
    DisplayHandle handle = new DisplayHandle(line, lineOrigin.clone());
    synchronized (lineLifecycleLock) {
      if (!acceptingLines.get()) {
        removeDisplayOwned(line);
        return;
      }
      activeLines.put(lineId, handle);
    }
    player.showEntity(Adapt.instance, line);
    if (!J.runEntity(line, () -> removeLineOwned(lineId, line), getLineDurationTicks())) {
      removeLine(lineId);
    }
  }

  private void removeLine(UUID lineId) {
    DisplayHandle handle = activeLines.remove(lineId);
    if (handle == null) {
      return;
    }
    if (!J.runEntity(handle.display(), () -> removeDisplayOwned(handle.display()))) {
      J.runAt(handle.anchor(), () -> removeDisplayOwned(handle.display()));
    }
  }

  private void removeLineOwned(UUID lineId, BlockDisplay line) {
    activeLines.remove(lineId);
    removeDisplayOwned(line);
  }

  private void removeDisplayOwned(BlockDisplay line) {
    if (line.isValid()) {
      line.remove();
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityRemoveEvent e) {
    if (e.getEntity() instanceof BlockDisplay) {
      activeLines.remove(e.getEntity().getUniqueId());
    }
  }

  private void clearLines() {
    List<DisplayHandle> handles;
    synchronized (lineLifecycleLock) {
      handles = List.copyOf(activeLines.values());
      activeLines.clear();
    }
    for (DisplayHandle handle : handles) {
      if (!J.runEntity(handle.display(), () -> removeDisplayOwned(handle.display()))) {
        J.runAt(handle.anchor(), () -> removeDisplayOwned(handle.display()));
      }
    }
  }

  private int getLineDurationTicks() {
    return Math.max(10, Math.min(20 * 10, getConfig().lineDurationTicks));
  }

  static float lineLength(int segments, double spacing) {
    return (float) (Math.max(2, segments) * Math.max(0.1D, spacing));
  }

  private void playPingSound(Player p, double distance, int range) {
    double normalized = Math.min(1.0, distance / Math.max(1.0, range));
    float pitch = (float) Math.max(0.45, Math.min(1.95, 1.9 - (normalized * 1.1)));
    fx(p.getLocation(), FxPriority.GAMEPLAY)
        .chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, pitch, Sound.BLOCK_NOTE_BLOCK_BIT, 0.65f, (float) Math.min(2.0, pitch + 0.2));
  }

  private boolean isOre(Material type) {
    return type == Material.ANCIENT_DEBRIS || type.name().endsWith("_ORE");
  }

  private boolean isExcavationTool(ItemStack item) {
    if (!isItem(item)) {
      return false;
    }

    String name = item.getType().name();
    return name.endsWith("_SHOVEL") || name.endsWith("_PICKAXE");
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.getPlayer().getUniqueId();
    activeScans.remove(playerId);
    WorldBlockScanScheduler.cancel(this, playerId);
  }

  @Override
  public void unregister() {
    acceptingLines.set(false);
    super.unregister();
    activeScans.clear();
    WorldBlockScanScheduler.cancelOwner(this);
    clearLines();
  }

  private int getScanRange(int level) {
    return Math.min(MAX_SCAN_RANGE, Math.max(6, (int) Math.round(getConfig().scanRangeBase + (getLevelPercent(level) * getConfig().scanRangeFactor))));
  }

  private double getPingChance(int level) {
    return Math.min(getConfig().maxPingChance, getConfig().pingChanceBase + (getLevelPercent(level) * getConfig().pingChanceFactor));
  }

  private long getCooldownMillis(int level) {
    return Math.max(350L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  private int getHintSegments(int level) {
    return Math.max(4, (int) Math.round(getConfig().hintSegmentsBase + (getLevelPercent(level) * getConfig().hintSegmentsFactor)));
  }


  @ConfigDescription("Mining can emit seismic pings that hint toward nearby ore direction.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Scan Range Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double scanRangeBase = 11;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Scan Range Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double scanRangeFactor = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum world block checks made by one seismic ping.", impact = "Higher values improve ore detection but increase total budgeted scan work; values above the hard safety cap are clamped.")
    int maxBlockChecks = 1024;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius searched completely before the remaining seismic budget is spread across the full range.", impact = "Higher values prioritize nearby accuracy; lower values reserve more samples for distant ore.")
    int denseScanRadius = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ping Chance Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pingChanceBase = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ping Chance Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pingChanceFactor = 0.37;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Ping Chance for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxPingChance = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 2600;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 1850;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hint Segments Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hintSegmentsBase = 7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hint Segments Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hintSegmentsFactor = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Segment Spacing for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double segmentSpacing = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Thickness of the glowing Seismic Ping direction line.", impact = "Higher values make the line easier to see and values are clamped between 0.04 and 0.5 blocks.")
    double lineThickness = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks that the glowing Seismic Ping direction line remains visible.", impact = "Higher values keep the direction visible longer and retain the temporary display entity longer.")
    int lineDurationTicks = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Client view-range multiplier for the glowing Seismic Ping direction line.", impact = "Higher values allow the line to render farther away and values are clamped between 0.5 and 2.0.")
    double lineViewRange = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Ping for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerPing = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Target Value Xp Multiplier for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double targetValueXpMultiplier = 0.5;

    public Config() {
      costFactor = 0.78;
      initialCost = 4;
    }
  }

  private record DisplayHandle(BlockDisplay display, Location anchor) {
  }
}
