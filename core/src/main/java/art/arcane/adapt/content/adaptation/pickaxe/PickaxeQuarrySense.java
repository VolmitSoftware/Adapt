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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.fx.ViewerDisplayPort;
import art.arcane.adapt.api.item.ItemDurability;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.content.integration.hiddenore.HiddenOrePort;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.common.world.BlockScanScheduler;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PickaxeQuarrySense extends SimpleAdaptation<PickaxeQuarrySense.Config> {
  private static final int MAX_SCAN_RADIUS = 32;
  private static final int MAX_BLOCK_CHECKS_PER_ACTIVATION = 4096;
  private static final int MAX_HIGHLIGHTS_PER_ACTIVATION = 16;
  private static final Set<String> ELIGIBLE_PICKAXES = Set.of("iron_pickaxe", "diamond_pickaxe", "netherite_pickaxe");
  private static final Set<String> QUARRY_ORES = Set.of(
      "copper_ore", "deepslate_copper_ore", "coal_ore", "gold_ore", "iron_ore",
      "diamond_ore", "lapis_ore", "emerald_ore", "nether_quartz_ore",
      "nether_gold_ore", "redstone_ore", "deepslate_coal_ore",
      "deepslate_iron_ore", "deepslate_gold_ore", "deepslate_lapis_ore",
      "deepslate_diamond_ore", "deepslate_emerald_ore",
      "deepslate_redstone_ore");
  private final Map<UUID, Long> markerGenerations = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> activeScans = new ConcurrentHashMap<>();

  public PickaxeQuarrySense() {
    super("pickaxe-quarry-sense");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("map"));
    setInterval(1200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spyglass"))
        .key("challenge_pickaxe_quarry_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_quarry_200", "pickaxe.quarry-sense.scans", 200, 300);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, PickaxePreferences.TRIGGER, PickaxePreferences.MATERIALS, PickaxePreferences.GLOW);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getScanRadius(level)), 1);
    statLore(v, Form.pc(getDurabilityCostPercent(level), 2), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_BLOCK && action != InteractAction.RIGHT_CLICK_AIR) {
      return;
    }

    if (e.hand() != null && e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    if (!preference(p, PickaxePreferences.TRIGGER).accepts(p.isSneaking())) {
      return;
    }
    if (!p.isSneaking()) {
      if (e.clickedBlock() != null && items().isInteractable(e.clickedBlock().type())) {
        return;
      }
      for (Adaptation<?> sibling : getSkill().getAdaptations()) {
        if (sibling instanceof PickaxeChisel && sibling.getActiveLevel(p) > 0) {
          return;
        }
      }
    }
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!isEligiblePickaxe(hand) || p.hasCooldown(hand.type())) {
      return;
    }

    if (activeScans.containsKey(p.uuid())) {
      e.setCancelled(true);
      return;
    }

    fx(p.eyeLocation(), FxPriority.GAMEPLAY)
        .particle(Particles.ENCHANTMENT_TABLE, 14, 0, 0, 0, 0.22, 0.15)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.35f);

    int durabilityCost = getDurabilityCost(hand, level);
    if (!canApplyPickaxeCost(hand, durabilityCost)) {
      showPickaxeCostFailure(p);
      return;
    }

    int scanRadius = getScanRadius(level);
    startScan(p, p.location(), hand.type(), level, scanRadius);
    e.setCancelled(true);
  }

  private void startScan(PlayerRef p, Loc origin, Id pickaxeType, int level, int radius) {
    WorldRef world = origin.world();
    if (world == null) {
      return;
    }

    PickaxePreferences.Materials filter = preference(p, PickaxePreferences.MATERIALS);
    ArrayList<BlockScanScheduler.AdditionalMatch> hiddenMatches = new ArrayList<>();
    for (HiddenOrePort.VeinTarget vein : hiddenOre().veins(origin, radius)) {
      Loc at = vein.location();
      if (!filter.accepts(vein.display()) || at.world() == null || !at.world().uid().equals(world.uid())
          || at.blockY() < world.minHeight()
          || at.blockY() >= world.maxHeight()) {
        continue;
      }
      double dx = at.x() - origin.x();
      double dy = at.y() - origin.y();
      double dz = at.z() - origin.z();
      double distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);
      if (distanceSquared <= (double) radius * radius) {
        hiddenMatches.add(new BlockScanScheduler.AdditionalMatch(
            at.blockX(), at.blockY(), at.blockZ(), vein.display(), distanceSquared
        ));
      }
    }

    int checks = Math.min(MAX_BLOCK_CHECKS_PER_ACTIVATION, Math.max(1, getConfig().maxBlockChecks));
    BlockScanScheduler.ScanRequest request = BlockScanScheduler.ScanRequest.builder(origin)
        .radius(radius)
        .denseRadius(getConfig().denseScanRadius)
        .maxSamples(checks)
        .maxResults(getMaxHighlights(level))
        .seed(ThreadLocalRandom.current().nextInt())
        .additionalMatches(hiddenMatches)
        .matcher((Id material) -> isQuarryOre(material) && filter.accepts(material))
        .completion((BlockScanScheduler.ScanResult result) -> completeScan(p, pickaxeType, radius, result))
        .build();
    UUID scanId = BlockScanScheduler.submit(this, p.uuid(), request);
    activeScans.put(p.uuid(), scanId);
  }

  private void completeScan(PlayerRef p, Id pickaxeType, int scanRadius, BlockScanScheduler.ScanResult result) {
    UUID playerId = p.uuid();
    if (!result.scanId().equals(activeScans.get(playerId))) {
      return;
    }

    boolean scheduled = Tasks.runEntity(p, () -> {
      if (!activeScans.remove(playerId, result.scanId()) || !p.isOnline()) {
        return;
      }

      int activeLevel = getActiveLevel(p);
      if (!canCompleteScan(activeLevel, result.world(), p.world())) {
        return;
      }

      List<BlockScanScheduler.Match> ores = result.matches();
      int cooldownTicks = getCooldownTicks(activeLevel);
      if (ores.isEmpty()) {
        showEmptyScan(p);
        p.setCooldown(pickaxeType, cooldownTicks);
        AbilityGlyphs.cooldown(p, this, cooldownTicks * 50L);
        return;
      }

      ItemRef currentHand = p.inventory().mainHand();
      int durabilityCost = getDurabilityCost(currentHand, activeLevel);
      if (!currentHand.type().equals(pickaxeType)
          || !isEligiblePickaxe(currentHand)
          || !canApplyPickaxeCost(currentHand, durabilityCost)
          || !applyPickaxeCost(p, currentHand, durabilityCost)) {
        showPickaxeCostFailure(p);
        p.setCooldown(pickaxeType, cooldownTicks);
        AbilityGlyphs.cooldown(p, this, cooldownTicks * 50L);
        return;
      }

      int highlightTicks = getHighlightTicks(activeLevel);
      for (BlockScanScheduler.Match ore : ores) {
        showOreMarker(p, result.world(), ore, highlightTicks);
      }
      p.setCooldown(pickaxeType, cooldownTicks);
      AbilityGlyphs.cooldown(p, this, cooldownTicks * 50L);
      showSuccessfulScan(p, scanRadius, ores.size());
    });
    if (!scheduled) {
      activeScans.remove(playerId, result.scanId());
    }
  }

  static boolean canCompleteScan(int activeLevel, WorldRef scannedWorld, WorldRef currentWorld) {
    return activeLevel > 0 && scannedWorld != null && currentWorld != null
        && scannedWorld.uid().equals(currentWorld.uid());
  }

  private void showEmptyScan(PlayerRef p) {
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 12, 0, 0, 0, 0.22, 0.02)
        .ring(Particles.SMOKE, 0.6D, 10, 0)
        .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.75f);
  }

  private void showSuccessfulScan(PlayerRef p, int scanRadius, int oreCount) {
    timeline(p.location().add(0, 1, 0))
        .duration(8)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(Math.min(48, scanRadius + 8))
        .frame((fxE, tick, progress) -> {
          fxE.dome(Particles.GLOW, scanRadius * 0.5D * progress, Math.min(48, 12 + scanRadius));
          if (tick == 0 || tick == 3 || tick == 6) {
            fxE.sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, (float) (1.0D + (progress * 0.8D)));
          }
        })
        .start();
    fx(p.location(), FxPriority.GAMEPLAY).sound(Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.6f);
    xp(p, oreCount * getConfig().xpPerFoundOre);
    addStat(p, "pickaxe.quarry-sense.scans", 1);
  }

  private void showOreMarker(PlayerRef p, WorldRef world, BlockScanScheduler.Match ore, int durationTicks) {
    Loc location = new Loc(world, ore.x(), ore.y(), ore.z());
    Rgb color = preference(p, PickaxePreferences.GLOW).color(oreColor(ore.material()));
    UUID playerId = p.uuid();
    long generation = markerGenerations.getOrDefault(playerId, 0L);
    Tasks.runAt(location, () -> {
      if (generation == markerGenerations.getOrDefault(playerId, 0L)) {
        showOreMarkerAtRegion(p, world, ore, location, durationTicks, color);
      }
    });
  }

  private void showOreMarkerAtRegion(PlayerRef p, WorldRef world, BlockScanScheduler.Match ore, Loc location,
                                     int durationTicks, Rgb color) {
    if (!world.isChunkLoaded(ore.x() >> 4, ore.z() >> 4)) {
      return;
    }
    if (!ore.supplied() && !world.blockAt(ore.x(), ore.y(), ore.z()).type().equals(ore.material())) {
      return;
    }
    BlockStateRef block = items().blockState(ore.material());
    if (block == null) {
      return;
    }

    displays().showBlock(
        getName(),
        "ore-" + ore.x() + ":" + ore.y() + ":" + ore.z(),
        p,
        location,
        block,
        color,
        durationTicks
    );
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    UUID playerId = player.getPlayer().uuid();
    markerGenerations.merge(playerId, 1L, Long::sum);
    activeScans.remove(playerId);
    BlockScanScheduler.cancel(this, playerId);
    displays().clearViewer(getName(), playerId);
  }

  @Handle(ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.player().uuid();
    markerGenerations.merge(playerId, 1L, Long::sum);
    activeScans.remove(playerId);
    BlockScanScheduler.cancel(this, playerId);
    displays().clearViewer(getName(), playerId);
  }

  @Override
  public void unregister() {
    activeScans.clear();
    BlockScanScheduler.cancelOwner(this);
    displays().clearChannel(getName());
    super.unregister();
  }

  static Rgb oreColor(Id material) {
    String name = material.path();
    if (name.contains("redstone")) {
      return new Rgb(255, 60, 60);
    }
    if (name.contains("diamond")) {
      return new Rgb(90, 230, 235);
    }
    if (name.contains("emerald")) {
      return new Rgb(70, 230, 100);
    }
    if (name.contains("gold")) {
      return new Rgb(255, 205, 55);
    }
    if (name.contains("lapis")) {
      return new Rgb(55, 105, 230);
    }
    if (name.contains("copper")) {
      return new Rgb(220, 120, 75);
    }
    if (name.contains("iron")) {
      return new Rgb(225, 205, 185);
    }
    return new Rgb(170, 220, 235);
  }

  private boolean isEligiblePickaxe(ItemRef hand) {
    return isItem(hand) && hand.type().isMinecraft() && ELIGIBLE_PICKAXES.contains(hand.type().path());
  }

  private boolean isQuarryOre(Id type) {
    return type.isMinecraft() && QUARRY_ORES.contains(type.path());
  }

  private boolean canApplyPickaxeCost(ItemRef hand, int durabilityCost) {
    if (hand == null || hand.isEmpty()) {
      return false;
    }
    int typeMax = items().maxDurability(hand.type());
    if (!reducesMaxDurability()) {
      return (long) hand.damage() + durabilityCost < typeMax;
    }

    int currentMax = Math.max(1, hand.maxDamage());
    int currentDamage = Math.max(0, hand.damage());
    return (long) currentMax - durabilityCost > currentDamage + 1L;
  }

  private void showPickaxeCostFailure(PlayerRef p) {
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 8, 0, 0, 0, 0.2, 0.03)
        .dustBurst(Rgb.of(0x555555), 6, 0.2, 1.0f)
        .sound(Sounds.BLOCK_ANVIL_PLACE, 0.5f, 0.55f);
  }

  private boolean applyPickaxeCost(PlayerRef p, ItemRef hand, int durabilityCost) {
    if (reducesMaxDurability()) {
      return tryReduceMaxDurability(p, hand, durabilityCost);
    }

    return tryDamagePickaxe(p, hand, durabilityCost);
  }

  /** Without per-stack max durability (before 1.20.5) the cost is taken as damage instead. */
  private boolean reducesMaxDurability() {
    return getConfig().costsReduceMaxDurability && ItemDurability.perStack();
  }

  private boolean tryDamagePickaxe(PlayerRef p, ItemRef hand, int durabilityCost) {
    if (hand.isEmpty()) {
      return false;
    }

    int maxDurability = items().maxDurability(hand.type());
    int currentDamage = hand.damage();
    if (currentDamage + durabilityCost >= maxDurability) {
      return false;
    }

    hand.setDamage(currentDamage + durabilityCost);
    p.inventory().setMainHand(hand);
    return true;
  }

  private boolean tryReduceMaxDurability(PlayerRef p, ItemRef hand, int durabilityCost) {
    if (hand.isEmpty()) {
      return false;
    }

    int currentMax = Math.max(1, hand.maxDamage());
    int currentDamage = Math.max(0, hand.damage());
    int newMax = currentMax - durabilityCost;
    if (newMax <= currentDamage + 1) {
      return false;
    }

    hand.setMaxDamage(Math.max(1, newMax));
    p.inventory().setMainHand(hand);
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

  private int getDurabilityCost(ItemRef hand, int level) {
    int maxDurability = Math.max(1, items().maxDurability(hand.type()));
    return Math.max(1, (int) Math.round(maxDurability * getDurabilityCostPercent(level)));
  }

  private double getDurabilityCostPercent(int level) {
    return Math.max(getConfig().minDurabilityCostPercent,
        getConfig().durabilityCostPercentBase - (getLevelPercent(level) * getConfig().durabilityCostPercentFactor));
  }

  private static ItemOps items() {
    return AdaptCore.platform().host().items();
  }

  private static HiddenOrePort hiddenOre() {
    return AdaptPorts.get(HiddenOrePort.class, HiddenOrePort.NONE);
  }

  private static ViewerDisplayPort displays() {
    return AdaptPorts.get(ViewerDisplayPort.class, ViewerDisplayPort.NONE);
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
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
