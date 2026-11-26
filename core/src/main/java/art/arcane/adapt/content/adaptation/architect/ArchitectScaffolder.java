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

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
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
import art.arcane.adapt.api.mutation.upgrade.UpgradeLoans;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.value.MaterialNames;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class ArchitectScaffolder extends SimpleAdaptation<ArchitectScaffolder.Config> {
  public static final PlayerPreference<Control> CONTROL = new PlayerPreference<>(Control.class,
      new PlayerPreference.Definition<>("control", ArchitectMessages.ARCHITECTSCAFFOLDER_PREFERENCE_CONTROL, Control.SNEAK, List.of(
          new PlayerPreference.Choice<>(Control.SNEAK, ArchitectMessages.ARCHITECTSCAFFOLDER_PREFERENCE_CONTROL_SNEAK, Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Control.ARMED, ArchitectMessages.ARCHITECTSCAFFOLDER_PREFERENCE_CONTROL_ARMED, Id.minecraft("scaffolding"), 1))));
  public static final PlayerPreference<Materials> MATERIALS = new PlayerPreference<>(Materials.class,
      new PlayerPreference.Definition<>("materials", ArchitectMessages.ARCHITECTSCAFFOLDER_PREFERENCE_MATERIALS, Materials.ALL, List.of(
          new PlayerPreference.Choice<>(Materials.ALL, ArchitectMessages.ARCHITECTSCAFFOLDER_PREFERENCE_MATERIALS_ALL, Id.minecraft("stone"), 1),
          new PlayerPreference.Choice<>(Materials.WOOD, ArchitectMessages.ARCHITECTSCAFFOLDER_PREFERENCE_MATERIALS_WOOD, Id.minecraft("oak_planks"), 1),
          new PlayerPreference.Choice<>(Materials.STONE, ArchitectMessages.ARCHITECTSCAFFOLDER_PREFERENCE_MATERIALS_STONE, Id.minecraft("stone_bricks"), 1),
          new PlayerPreference.Choice<>(Materials.GLASS, ArchitectMessages.ARCHITECTSCAFFOLDER_PREFERENCE_MATERIALS_GLASS, Id.minecraft("glass"), 1))));

  private static final Id AIR = Id.minecraft("air");
  private final Map<ArchitectDemolition.BlockKey, ScaffoldMark> scaffolds = new ConcurrentHashMap<>();
  private final Map<UUID, Set<ArchitectDemolition.BlockKey>> byPlayer = playerState();
  private final Cooldowns denyCd = cooldowns();
  private volatile FilterCache filterCache;

  public ArchitectScaffolder() {
    super("architect-scaffolder");
    registerConfiguration(ArchitectScaffolder.Config.class);
    setIcon(Id.minecraft("scaffolding"));
    setInterval(9220);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("scaffolding"))
        .key("challenge_architect_scaffolder_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("scaffolding"))
            .key("challenge_architect_scaffolder_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_architect_scaffolder_500", "architect.scaffolder.blocks-scaffolded", 500, 300);
    registerMilestone("challenge_architect_scaffolder_5k", "architect.scaffolder.blocks-scaffolded", 5000, 1000);
  }

  private boolean acceptsMaterial(PlayerRef player, Id material) {
    String name = material.path().toUpperCase(Locale.ROOT);
    return switch (preference(player, MATERIALS)) {
      case ALL -> true;
      case WOOD -> name.endsWith("_PLANKS") || name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE");
      case STONE -> name.contains("STONE") || name.contains("BRICK") || name.contains("DEEPSLATE");
      case GLASS -> name.contains("GLASS");
    };
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL, MATERIALS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.SCAFFOLDER_LORE1));
    statLore(v, C.GREEN, "", getDurationSeconds(getLevelPercent(level)), 2);
  }

  @Override
  public BlockActionContext resolveBlockPlaceContext(PlayerRef p, Loc location, Predicate<PlayerRef> requirement) {
    return UpgradeLoans.context(this, super.resolveBlockPlaceContext(p, location, requirement),
        (Adaptation<?> upgrade) -> upgrade.resolveBlockPlaceContext(p, location, requirement));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    PlayerRef p = e.player();
    if (!buildingArmed(p)) {
      return;
    }

    BlockRef block = e.block();
    Adaptation.BlockActionContext context = resolveBlockPlaceContext(p, block.location(), this::buildingArmed);
    if (context == null) {
      return;
    }

    Id type = block.type();
    if (type.isAir() || !AdaptCore.platform().host().items().isBlock(type)) {
      return;
    }
    ItemRef placedItem = ArchitectDemolition.snapshotPlacedItem(e.itemInHand(), block.state().placementItem());
    if (placedItem == null) {
      return;
    }

    if (!isScaffoldAllowed(type) || !acceptsMaterial(p, type)) {
      playScaffoldDenied(p);
      return;
    }

    UUID id = p.uuid();
    Set<ArchitectDemolition.BlockKey> mine = byPlayer.computeIfAbsent(id, unused -> ConcurrentHashMap.newKeySet());
    if (mine.size() >= getConfig().maxScaffoldsPerPlayer) {
      return;
    }

    if (!payHunger(p)) {
      playScaffoldDenied(p);
      return;
    }

    ArchitectDemolition.BlockKey key = ArchitectDemolition.BlockKey.of(block);
    mine.add(key);
    scaffolds.put(key, new ScaffoldMark(id, type, placedItem));
    fx(block.location().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
        .burst(Particles.CLOUD, 8, 0.25D)
        .burst(Particles.CRIT_MAGIC, 2, 0.15D)
        .sound(Sounds.BLOCK_DEEPSLATE_PLACE, 0.6f, 1.6f);

    addStat(p, "architect.scaffolder.blocks-scaffolded", 1);
    UpgradeLoans.landed(this, p);
    int delayTicks = getDurationSeconds(getLevelPercent(context.level())) * 20;
    if (delayTicks > 30) {
      Tasks.runAt(block.location(), () -> warnScaffold(block), delayTicks - 20);
    }
    Tasks.runAt(block.location(), () -> removeScaffold(block), delayTicks);
  }

  private boolean buildingArmed(PlayerRef player) {
    return player.isSneaking() || preference(player, CONTROL) == Control.ARMED;
  }

  private void warnScaffold(BlockRef block) {
    if (!scaffolds.containsKey(ArchitectDemolition.BlockKey.of(block))) {
      return;
    }
    fx(block.location().add(0.5, 0.5, 0.5), FxPriority.AMBIENT)
        .burst(Particles.ASH, 3, 0.2D)
        .sound(Sounds.BLOCK_SAND_STEP, 0.3f, 1.4f);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    if (scaffolds.isEmpty()) {
      return;
    }

    ArchitectDemolition.BlockKey key = ArchitectDemolition.BlockKey.of(e.block());
    ScaffoldMark mark = scaffolds.remove(key);
    if (mark == null) {
      return;
    }

    Set<ArchitectDemolition.BlockKey> mine = byPlayer.get(mark.owner());
    if (mine != null) {
      mine.remove(key);
    }
  }

  private void removeScaffold(BlockRef block) {
    ArchitectDemolition.BlockKey key = ArchitectDemolition.BlockKey.of(block);
    ScaffoldMark mark = scaffolds.remove(key);
    if (mark == null) {
      return;
    }

    Set<ArchitectDemolition.BlockKey> mine = byPlayer.get(mark.owner());
    if (mine != null) {
      mine.remove(key);
    }

    if (!block.type().equals(mark.material())) {
      return;
    }

    List<ItemRef> refunds = collectRestitution(block, mark.placed());
    block.setType(AIR, true);
    fx(block.location().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
        .burst(Particles.REVERSE_PORTAL, 6, 0.2D)
        .sound(Sounds.BLOCK_DEEPSLATE_BREAK, 0.6f, 1.4f);

    PlayerRef owner = AdaptCore.platform().host().player(mark.owner());
    if (owner != null && owner.isOnline()) {
      restitute(owner, block.location(), refunds, () -> {
        fx(owner.location().add(0, 1, 0), FxPriority.AMBIENT)
            .column(Particles.END_ROD, 4, 1.0D)
            .sound(Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.6f);
      });
    } else {
      for (ItemRef refund : refunds) {
        block.world().dropItemNaturally(block.location().position(), refund);
      }
    }
  }

  public static void restitute(PlayerRef owner, Loc source, List<ItemRef> refunds, Runnable delivered) {
    AtomicBoolean claimed = new AtomicBoolean();
    Runnable fallback = () -> {
      if (claimed.compareAndSet(false, true)) {
        Tasks.runAt(source, () -> {
          for (ItemRef refund : refunds) {
            source.world().dropItemNaturally(source.position(), refund);
          }
        });
      }
    };
    Runnable give = () -> {
      if (!owner.isOnline()) {
        fallback.run();
        return;
      }
      if (!claimed.compareAndSet(false, true)) {
        return;
      }
      for (ItemRef refund : refunds) {
        for (ItemRef overflow : owner.inventory().addItem(refund)) {
          owner.world().dropItemNaturally(owner.location().position(), overflow);
        }
      }
      delivered.run();
    };
    if (!Tasks.runEntity(owner, give, fallback)) {
      fallback.run();
    }
  }

  static List<ItemRef> collectRestitution(BlockRef block, ItemRef placed) {
    if (ArchitectDemolition.keepsContentsInItem(block.type())) {
      ItemRef refund = block.itemWithBlockState(placed);
      return refund == null ? new ArrayList<>(block.drops(null, null)) : List.of(refund);
    }
    InventoryRef inventory = block.blockInventory();
    List<ItemRef> refunds = ArchitectDemolition.restitution(placed.copy(),
        inventory == null ? null : inventory.contents());
    if (inventory != null) {
      ArchitectDemolition.clearInventory(inventory);
    }
    return refunds;
  }

  private int getDurationSeconds(double factor) {
    return (int) Math.max(1, M.lerp(getConfig().minDurationSeconds, getConfig().maxDurationSeconds, factor));
  }

  private boolean isScaffoldAllowed(Id type) {
    Config config = getConfig();
    FilterCache cache = filterCache;
    if (cache == null || cache.source() != config.blockFilterMaterials
        || !Objects.equals(cache.mode(), config.blockFilterMode)) {
      cache = new FilterCache(config.blockFilterMaterials, config.blockFilterMode,
          parseFilterMaterials(config.blockFilterMaterials));
      filterCache = cache;
    }

    return isScaffoldAllowed(cache.mode(), cache.materials(), type);
  }

  private boolean payHunger(PlayerRef p) {
    double cost = Math.max(0, getConfig().hungerExhaustionPerScaffold);
    if (cost <= 0) {
      return true;
    }

    if (p.foodLevel() <= 0 && p.saturation() <= 0) {
      return false;
    }

    p.setExhaustion((float) (p.exhaustion() + cost));
    return true;
  }

  private void playScaffoldDenied(PlayerRef p) {
    UUID id = p.uuid();
    if (!denyCd.isReady(id, 1500)) {
      return;
    }

    denyCd.mark(id);
    fx(p.location().add(0, 1, 0), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.15D)
        .sound(Sounds.BLOCK_DISPENSER_FAIL, 0.4f, 0.8f);
  }

  static Set<Id> parseFilterMaterials(Collection<String> names) {
    return parseFilterMaterials(names, ArchitectScaffolder::isKnownMaterial);
  }

  /** Material names as the Bukkit code read them ({@code matchMaterial} of the upper-cased name), or a modded id. */
  static Set<Id> parseFilterMaterials(Collection<String> names, Predicate<Id> known) {
    Set<Id> materials = new HashSet<>();
    if (names == null) {
      return materials;
    }

    for (String name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }

      Id material = MaterialNames.match(name.trim().toUpperCase(Locale.ROOT));
      if (material == null || !known.test(material)) {
        material = MaterialNames.modded(name.trim());
      }
      if (material == null) {
        continue;
      }
      if (known.test(material)) {
        materials.add(material);
      }
    }

    return materials;
  }

  private static boolean isKnownMaterial(Id material) {
    RegistryLookup registries = AdaptCore.platform().host().registries();
    return registries.exists(RegistryKind.ITEM, material) || registries.exists(RegistryKind.BLOCK, material);
  }

  static boolean isScaffoldAllowed(String mode, Set<Id> materials, Id type) {
    if (mode == null || type == null) {
      return true;
    }

    return switch (mode.trim().toUpperCase(Locale.ROOT)) {
      case "BLACKLIST" -> !materials.contains(type);
      case "WHITELIST" -> materials.contains(type);
      default -> true;
    };
  }

  private record ScaffoldMark(UUID owner, Id material, ItemRef placed) {
  }

  private record FilterCache(List<String> source, String mode, Set<Id> materials) {
  }

  @ConfigDescription("Sneak-place blocks as temporary scaffolds that dissolve and refund themselves, with an optional material filter and hunger cost.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaffold lifetime in seconds at level 0 progression.", impact = "Higher values keep low-level scaffolds in the world longer before dissolving.")
    int minDurationSeconds = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaffold lifetime in seconds at maximum level progression.", impact = "Higher values keep max-level scaffolds in the world longer before dissolving.")
    int maxDurationSeconds = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum number of active scaffolds tracked per player.", impact = "Higher values let players keep more temporary scaffolds at once.")
    int maxScaffoldsPerPlayer = 24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Block filter mode: OFF, BLACKLIST, or WHITELIST.", impact = "BLACKLIST denies the listed materials, WHITELIST allows only the listed materials, OFF applies no filter.")
    String blockFilterMode = "OFF";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Material names used by the block filter, for example TNT or SAND.", impact = "Entries are denied under BLACKLIST and are the only allowed scaffolds under WHITELIST.")
    List<String> blockFilterMaterials = new ArrayList<>();
    @art.arcane.adapt.util.config.ConfigDoc(value = "Exhaustion added per scaffolded block, where 4.0 drains half a hunger point. Zero disables the cost.", impact = "Higher values make scaffolding drain hunger faster; zero makes it free.")
    double hungerExhaustionPerScaffold = 0;

    public Config() {
      initialCost = 1;
      baseCost = 2;
      costFactor = 0.5;
    }
  }

  public enum Control { SNEAK, ARMED }

  public enum Materials { ALL, WOOD, STONE, GLASS }
}
