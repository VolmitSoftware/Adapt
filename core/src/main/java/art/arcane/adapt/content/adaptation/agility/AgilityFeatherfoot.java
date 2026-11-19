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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
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
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.value.MaterialNames;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AgilityMessages;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityInsideBlockEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.platform.event.player.PlayerInputEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.event.server.ResourcesReloadedEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.math.PlayerInputPort;
import art.arcane.adapt.util.common.world.BlockTagSet;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class AgilityFeatherfoot extends SimpleAdaptation<AgilityFeatherfoot.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> FARMLAND = CommonPreferences.toggle("farmland", AgilityMessages.AGILITYFEATHERFOOT_PREFERENCE_FARMLAND, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> PLATES = CommonPreferences.toggle("plates", AgilityMessages.AGILITYFEATHERFOOT_PREFERENCE_PLATES, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> BERRIES = CommonPreferences.toggle("berries", AgilityMessages.AGILITYFEATHERFOOT_PREFERENCE_BERRIES, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SNOW = CommonPreferences.toggle("snow", AgilityMessages.AGILITYFEATHERFOOT_PREFERENCE_SNOW, CommonPreferences.Toggle.ON);

  private static final long SPRINT_INTENT_GRACE_MILLIS = 350L;

  private final Cooldowns fxThrottle = cooldowns();
  private final Cooldowns surfaceContactThrottle = cooldowns();
  private final Cooldowns sprintIntent = cooldowns();
  private final BlockTagSet pressurePlates = new BlockTagSet(Id.minecraft("pressure_plates"));
  private volatile RuleCache ruleCache;

  public AgilityFeatherfoot() {
    super("agility-featherfoot");
    registerConfiguration(Config.class);
    needs(PlatformNeed.partialFeature(EngineFeature.PLAYER_INPUT, "a held sprint key counts only while the player sprints"));
    setIcon(Id.minecraft("rabbit_foot"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("rabbit_foot"))
        .key("challenge_agility_featherfoot_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("leather_boots"))
            .key("challenge_agility_featherfoot_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_featherfoot_500", "agility.featherfoot.surfaces-ignored", 500, 300);
    registerMilestone("challenge_agility_featherfoot_5k", "agility.featherfoot.surfaces-ignored", 5000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, FARMLAND, PLATES, BERRIES, SNOW);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getSurfacesIgnored(level), 1);
    v.addLore(C.GREEN + " " + AdaptLanguage.text(AgilityMessages.FEATHERFOOT_LORE2));
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.normalizeForPersistence();
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (e.action() != InteractAction.PHYSICAL || e.clickedBlock() == null) {
      return;
    }

    BlockRef block = e.clickedBlock();
    Id type = block.type();
    SurfaceRules rules = getSurfaceRules();
    int minimumLevel = minimumLevelForSurface(type, pressurePlates.contains(type), rules);
    if (minimumLevel < 0) {
      return;
    }

    PlayerRef p = e.player();
    if (!protectsPreferredSurface(p, type, rules)) {
      return;
    }
    if (!ignoresSurface(minimumLevel, hasSprintIntent(p), getActiveLevel(p), rules)) {
      return;
    }

    e.setUseBlock(UseResult.DENY);
    recordSurfaceProtection(p, block);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityInsideBlockEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    BlockRef block = e.block();
    Id type = block.type();
    SurfaceRules rules = getSurfaceRules();
    int minimumLevel = minimumLevelForSurface(type, pressurePlates.contains(type), rules);
    if (minimumLevel < 0 || !protectsPreferredSurface(p, type, rules)) {
      return;
    }

    if (!ignoresSurface(minimumLevel, hasSprintIntent(p), getActiveLevel(p), rules)) {
      return;
    }

    e.setCancelled(true);
    if (rules.powderSnow().protects(type, false)) {
      p.setFreezeTicks(0);
    }
    recordSurfaceProtection(p, block);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInputEvent e) {
    PlayerRef p = e.player();
    if (shouldTrackSprintIntent(e.input().sprint(), getActiveLevel(p))) {
      sprintIntent.mark(p.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSprintEvent e) {
    PlayerRef p = e.player();
    if (shouldTrackSprintIntent(e.isSprinting(), getActiveLevel(p))) {
      sprintIntent.mark(p.uuid());
    }
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.player().uuid();
    sprintIntent.clear(playerId);
    surfaceContactThrottle.clear(playerId);
    fxThrottle.clear(playerId);
  }

  @Handle
  public void on(ResourcesReloadedEvent e) {
    pressurePlates.invalidate();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    rememberSprintIntent(p);
    if (p.freezeTicks() <= 0) {
      return;
    }

    Id type = p.world().blockAt(p.location()).type();
    SurfaceRules rules = getSurfaceRules();
    if (!preferenceEnabled(p, SNOW) || !rules.powderSnow().protects(type, false)) {
      return;
    }

    if (ignoresSurface(rules.powderSnow().minLevel(), hasSprintIntent(p), level, rules)) {
      p.setFreezeTicks(0);
    }
  }

  private boolean protectsPreferredSurface(PlayerRef player, Id type, SurfaceRules rules) {
    if (rules.farmland().protects(type, false)) {
      return preferenceEnabled(player, FARMLAND);
    }
    if (rules.pressurePlates().protects(type, pressurePlates.contains(type))) {
      return preferenceEnabled(player, PLATES);
    }
    if (rules.berryBushes().protects(type, false)) {
      return preferenceEnabled(player, BERRIES);
    }
    return rules.powderSnow().protects(type, false) && preferenceEnabled(player, SNOW);
  }

  private void rememberSprintIntent(PlayerRef p) {
    if (p.isSprinting() || sprintInput(p)) {
      sprintIntent.mark(p.uuid());
    }
  }

  private boolean hasSprintIntent(PlayerRef p) {
    boolean currentSprint = p.isSprinting() || sprintInput(p);
    if (currentSprint) {
      sprintIntent.mark(p.uuid());
    }
    return sprintIntentActive(currentSprint, sprintIntent.remaining(p.uuid(), SPRINT_INTENT_GRACE_MILLIS));
  }

  private static boolean sprintInput(PlayerRef p) {
    PlayerInput input = AdaptPorts.get(PlayerInputPort.class, PlayerInputPort.NONE).currentInput(p);
    return input != null && input.sprint();
  }

  static boolean sprintIntentActive(boolean currentSprint, long graceRemainingMillis) {
    return currentSprint || graceRemainingMillis > 0L;
  }

  static boolean shouldTrackSprintIntent(boolean sprinting, int level) {
    return sprinting && level > 0;
  }

  static boolean ignoresSurface(int minimumLevel, boolean sprinting, int level, SurfaceRules rules) {
    if (minimumLevel < 0 || level <= 0 || level < minimumLevel) {
      return false;
    }

    return sprinting || !rules.requireSprint();
  }

  static int minimumLevelForSurface(Id type, boolean vanillaPressurePlate, SurfaceRules rules) {
    List<SurfaceRule> ordered = rules.ordered();
    for (int index = 0; index < ordered.size(); index++) {
      SurfaceRule rule = ordered.get(index);
      if (rule.protects(type, vanillaPressurePlate)) {
        return rule.minLevel();
      }
    }

    return -1;
  }

  SurfaceRules getSurfaceRules() {
    Config config = getConfig();
    RuleCache local = ruleCache;
    if (local != null && local.source() == config) {
      return local.rules();
    }

    SurfaceRules built = SurfaceRules.build(config);
    ruleCache = new RuleCache(config, built);
    return built;
  }

  private void recordSurfaceProtection(PlayerRef p, BlockRef block) {
    UUID playerId = p.uuid();
    if (!surfaceContactThrottle.isReady(playerId, 600L)) {
      return;
    }
    surfaceContactThrottle.mark(playerId);
    recordProtection(p, block);
  }

  private void recordProtection(PlayerRef p, BlockRef block) {
    addStat(p, "agility.featherfoot.surfaces-ignored", 1);
    if (!fxThrottle.isReady(p.uuid(), 600L)) {
      return;
    }

    fxThrottle.mark(p.uuid());
    fx(block.location().add(0.5D, 1.0D, 0.5D), FxPriority.AMBIENT)
        .particle(Particles.CLOUD, 3, 0, 0.05D, 0, 0.15D, 0.02D)
        .sound(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.3F, 1.6F);
  }

  private int getSurfacesIgnored(int level) {
    return surfacesIgnored(level, getSurfaceRules());
  }

  static int surfacesIgnored(int level, SurfaceRules rules) {
    List<SurfaceRule> ordered = rules.ordered();
    int surfaces = 0;
    for (int index = 0; index < ordered.size(); index++) {
      SurfaceRule rule = ordered.get(index);
      if (rule.enabled() && level >= rule.minLevel()) {
        surfaces++;
      }
    }

    return surfaces;
  }

  private record RuleCache(Config source, SurfaceRules rules) {
  }

  record SurfaceRule(boolean enabled, int minLevel, Set<Id> materials, boolean vanillaPressurePlates) {
    boolean protects(Id type, boolean vanillaPressurePlate) {
      if (!enabled) {
        return false;
      }

      if (vanillaPressurePlates && vanillaPressurePlate) {
        return true;
      }

      return type != null && materials.contains(type);
    }
  }

  static final class SurfaceRules {
    private final boolean requireSprint;
    private final SurfaceRule farmland;
    private final SurfaceRule pressurePlates;
    private final SurfaceRule berryBushes;
    private final SurfaceRule powderSnow;
    private final List<SurfaceRule> ordered;

    private SurfaceRules(boolean requireSprint, SurfaceRule farmland, SurfaceRule pressurePlates,
                         SurfaceRule berryBushes, SurfaceRule powderSnow) {
      this.requireSprint = requireSprint;
      this.farmland = farmland;
      this.pressurePlates = pressurePlates;
      this.berryBushes = berryBushes;
      this.powderSnow = powderSnow;
      this.ordered = List.of(farmland, pressurePlates, berryBushes, powderSnow);
    }

    boolean requireSprint() {
      return requireSprint;
    }

    SurfaceRule farmland() {
      return farmland;
    }

    SurfaceRule pressurePlates() {
      return pressurePlates;
    }

    SurfaceRule berryBushes() {
      return berryBushes;
    }

    SurfaceRule powderSnow() {
      return powderSnow;
    }

    List<SurfaceRule> ordered() {
      return ordered;
    }

    static SurfaceRules build(Config config) {
      RegistryLookup registries = AdaptCore.platform().host().registries();
      return build(config, (Id id) -> registries.exists(RegistryKind.BLOCK, id) || registries.exists(RegistryKind.ITEM, id));
    }

    static SurfaceRules build(Config config, Predicate<Id> known) {
      return new SurfaceRules(
          config.requireSprint,
          new SurfaceRule(config.farmlandEnabled, config.farmlandMinLevel, parseMaterials(config.farmlandMaterials, known), false),
          new SurfaceRule(config.pressurePlateEnabled, config.pressurePlateMinLevel,
              parseMaterials(config.pressurePlateMaterials, known), config.pressurePlateUseVanillaTag),
          new SurfaceRule(config.berryBushEnabled, config.berryBushMinLevel, parseMaterials(config.berryBushMaterials, known), false),
          new SurfaceRule(config.powderSnowEnabled, config.powderSnowMinLevel, parseMaterials(config.powderSnowMaterials, known), false)
      );
    }

    private static Set<Id> parseMaterials(List<String> source, Predicate<Id> known) {
      if (source == null || source.isEmpty()) {
        return Collections.emptySet();
      }

      Set<Id> materials = new HashSet<>();
      for (String raw : source) {
        if (raw == null || raw.isBlank()) {
          continue;
        }

        Id material = MaterialNames.match(raw.trim());
        if (material == null || !known.test(material)) {
          AdaptLog.warn("agility-featherfoot: unknown material '" + raw.trim() + "'");
          continue;
        }

        materials.add(material);
      }

      return materials.isEmpty() ? Collections.emptySet() : Set.copyOf(materials);
    }
  }

  @ConfigDescription("Sprinting refuses to trample or trigger fragile ground. Every protection unlocks at its own level and can be switched off or retargeted at other blocks.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Requires the player to be sprinting for any protection to apply.", impact = "False protects the listed blocks while walking too.")
    boolean requireSprint = true;
    @ConfigDoc(value = "Enables the farmland protection entirely.", impact = "False stops farmland from ever being protected regardless of level.")
    boolean farmlandEnabled = true;
    @ConfigDoc(value = "Minimum level at which farmland protection applies.", impact = "Lower values unlock farmland protection sooner.")
    int farmlandMinLevel = 1;
    @ConfigDoc(value = "Blocks protected from being trampled by stepping on them.", impact = "Add block ids here to stop stepping from breaking or changing them.")
    List<String> farmlandMaterials = new ArrayList<>(List.of("FARMLAND"));
    @ConfigDoc(value = "Enables the pressure-plate protection entirely.", impact = "False stops plates from ever being ignored regardless of level.")
    boolean pressurePlateEnabled = true;
    @ConfigDoc(value = "Minimum level at which pressure plates are not triggered.", impact = "Lower values unlock plate immunity sooner.")
    int pressurePlateMinLevel = 2;
    @ConfigDoc(value = "Covers every block in the vanilla pressure plate tag.", impact = "False restricts the protection to the listed blocks only.")
    boolean pressurePlateUseVanillaTag = true;
    @ConfigDoc(value = "Extra blocks treated as pressure plates.", impact = "Add block ids such as TRIPWIRE to also step over them without triggering.")
    List<String> pressurePlateMaterials = new ArrayList<>();
    @ConfigDoc(value = "Enables the sweet-berry protection entirely.", impact = "False stops berry bushes from ever being ignored regardless of level.")
    boolean berryBushEnabled = true;
    @ConfigDoc(value = "Minimum level at which sweet-berry slowdown and damage are ignored.", impact = "Lower values unlock berry-bush immunity sooner.")
    int berryBushMinLevel = 3;
    @ConfigDoc(value = "Blocks whose contact damage and slowdown are ignored.", impact = "Add block ids here to walk through them without being snagged.")
    List<String> berryBushMaterials = new ArrayList<>(List.of("SWEET_BERRY_BUSH"));
    @ConfigDoc(value = "Enables the powder-snow protection entirely.", impact = "False stops powder snow from ever being ignored regardless of level.")
    boolean powderSnowEnabled = true;
    @ConfigDoc(value = "Minimum level at which powder-snow freezing is shrugged off.", impact = "Lower values unlock powder-snow crossing sooner.")
    int powderSnowMinLevel = 4;
    @ConfigDoc(value = "Blocks whose freezing effect is cleared on contact.", impact = "Add block ids here to cross them without accumulating freeze ticks.")
    List<String> powderSnowMaterials = new ArrayList<>(List.of("POWDER_SNOW"));

    public Config() {
      baseCost = 1;
      costFactor = 0.2;
      maxLevel = 4;
      initialCost = 1;
    }

    void normalizeForPersistence() {
      farmlandMinLevel = Math.max(1, farmlandMinLevel);
      pressurePlateMinLevel = Math.max(1, pressurePlateMinLevel);
      berryBushMinLevel = Math.max(1, berryBushMinLevel);
      powderSnowMinLevel = Math.max(1, powderSnowMinLevel);
      farmlandMaterials = farmlandMaterials == null ? new ArrayList<>() : farmlandMaterials;
      pressurePlateMaterials = pressurePlateMaterials == null ? new ArrayList<>() : pressurePlateMaterials;
      berryBushMaterials = berryBushMaterials == null ? new ArrayList<>() : berryBushMaterials;
      powderSnowMaterials = powderSnowMaterials == null ? new ArrayList<>() : powderSnowMaterials;
    }
  }
}
