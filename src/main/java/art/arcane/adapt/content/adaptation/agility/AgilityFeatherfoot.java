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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AgilityMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.inventorygui.Element;
import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AgilityFeatherfoot extends SimpleAdaptation<AgilityFeatherfoot.Config> {
  private static final long SPRINT_INTENT_GRACE_MILLIS = 350L;

  private final Cooldowns fxThrottle = cooldowns();
  private final Cooldowns surfaceContactThrottle = cooldowns();
  private final Cooldowns sprintIntent = cooldowns();
  private volatile RuleCache ruleCache;

  public AgilityFeatherfoot() {
    super("agility-featherfoot");
    registerConfiguration(Config.class);
    setIcon(Material.RABBIT_FOOT);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.RABBIT_FOOT)
        .key("challenge_agility_featherfoot_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.LEATHER_BOOTS)
            .key("challenge_agility_featherfoot_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_featherfoot_500", "agility.featherfoot.surfaces-ignored", 500, 300);
    registerMilestone("challenge_agility_featherfoot_5k", "agility.featherfoot.surfaces-ignored", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
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

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.getAction() != Action.PHYSICAL || e.getClickedBlock() == null) {
      return;
    }

    Block block = e.getClickedBlock();
    Material type = block.getType();
    SurfaceRules rules = getSurfaceRules();
    int minimumLevel = minimumLevelForSurface(type, Tag.PRESSURE_PLATES.isTagged(type), rules);
    if (minimumLevel < 0) {
      return;
    }

    Player p = e.getPlayer();
    if (!ignoresSurface(minimumLevel, hasSprintIntent(p), getActiveLevel(p), rules)) {
      return;
    }

    e.setUseInteractedBlock(Event.Result.DENY);
    recordSurfaceProtection(p, block);
  }

  @Override
  protected List<Listener> createCompanionListeners() {
    if (!PaperCompat.hasClass("io.papermc.paper.event.entity.EntityInsideBlockEvent")) {
      return List.of();
    }

    return List.of(new InsideBlockListener());
  }

  private final class InsideBlockListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityInsideBlockEvent e) {
      if (!(e.getEntity() instanceof Player p)) {
        return;
      }

      Block block = e.getBlock();
      Material type = block.getType();
      SurfaceRules rules = getSurfaceRules();
      int minimumLevel = minimumLevelForSurface(type, Tag.PRESSURE_PLATES.isTagged(type), rules);
      if (minimumLevel < 0) {
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
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerInputEvent e) {
    Player p = e.getPlayer();
    if (shouldTrackSprintIntent(e.getInput().isSprint(), getActiveLevel(p))) {
      sprintIntent.mark(p.getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSprintEvent e) {
    Player p = e.getPlayer();
    if (shouldTrackSprintIntent(e.isSprinting(), getActiveLevel(p))) {
      sprintIntent.mark(p.getUniqueId());
    }
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.getPlayer().getUniqueId();
    sprintIntent.clear(playerId);
    surfaceContactThrottle.clear(playerId);
    fxThrottle.clear(playerId);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    rememberSprintIntent(p);
    if (p.getFreezeTicks() <= 0) {
      return;
    }

    Material type = p.getLocation().getBlock().getType();
    SurfaceRules rules = getSurfaceRules();
    if (!rules.powderSnow().protects(type, false)) {
      return;
    }

    if (ignoresSurface(rules.powderSnow().minLevel(), hasSprintIntent(p), level, rules)) {
      p.setFreezeTicks(0);
    }
  }

  private void rememberSprintIntent(Player p) {
    if (p.isSprinting() || p.getCurrentInput().isSprint()) {
      sprintIntent.mark(p.getUniqueId());
    }
  }

  private boolean hasSprintIntent(Player p) {
    boolean currentSprint = p.isSprinting() || p.getCurrentInput().isSprint();
    if (currentSprint) {
      sprintIntent.mark(p.getUniqueId());
    }
    return sprintIntentActive(currentSprint, sprintIntent.remaining(p.getUniqueId(), SPRINT_INTENT_GRACE_MILLIS));
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

  static int minimumLevelForSurface(Material type, boolean vanillaPressurePlate, SurfaceRules rules) {
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

  private void recordSurfaceProtection(Player p, Block block) {
    UUID playerId = p.getUniqueId();
    if (!surfaceContactThrottle.isReady(playerId, 600L)) {
      return;
    }
    surfaceContactThrottle.mark(playerId);
    recordProtection(p, block);
  }

  private void recordProtection(Player p, Block block) {
    addStat(p, "agility.featherfoot.surfaces-ignored", 1);
    if (!fxThrottle.isReady(p.getUniqueId(), 600L)) {
      return;
    }

    fxThrottle.mark(p.getUniqueId());
    fx(block.getLocation().add(0.5D, 1.0D, 0.5D), FxPriority.AMBIENT)
        .particle(Particle.CLOUD, 3, 0, 0.05D, 0, 0.15D, 0.02D)
        .sound(Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.3F, 1.6F);
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

  record SurfaceRule(boolean enabled, int minLevel, Set<Material> materials, boolean vanillaPressurePlates) {
    boolean protects(Material type, boolean vanillaPressurePlate) {
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
      return new SurfaceRules(
          config.requireSprint,
          new SurfaceRule(config.farmlandEnabled, config.farmlandMinLevel, parseMaterials(config.farmlandMaterials), false),
          new SurfaceRule(config.pressurePlateEnabled, config.pressurePlateMinLevel,
              parseMaterials(config.pressurePlateMaterials), config.pressurePlateUseVanillaTag),
          new SurfaceRule(config.berryBushEnabled, config.berryBushMinLevel, parseMaterials(config.berryBushMaterials), false),
          new SurfaceRule(config.powderSnowEnabled, config.powderSnowMinLevel, parseMaterials(config.powderSnowMaterials), false)
      );
    }

    private static Set<Material> parseMaterials(List<String> source) {
      if (source == null || source.isEmpty()) {
        return Collections.emptySet();
      }

      EnumSet<Material> materials = EnumSet.noneOf(Material.class);
      for (String raw : source) {
        if (raw == null || raw.isBlank()) {
          continue;
        }

        Material material = Material.matchMaterial(raw.trim());
        if (material == null) {
          Adapt.warn("agility-featherfoot: unknown material '" + raw.trim() + "'");
          continue;
        }

        materials.add(material);
      }

      return materials.isEmpty() ? Collections.emptySet() : materials;
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
