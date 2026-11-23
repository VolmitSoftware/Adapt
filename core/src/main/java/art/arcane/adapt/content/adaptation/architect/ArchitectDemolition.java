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
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ArchitectDemolition extends SimpleAdaptation<ArchitectDemolition.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", ArchitectMessages.ARCHITECTDEMOLITION_PREFERENCE_SNEAK, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<Materials> MATERIALS = new PlayerPreference<>(Materials.class,
      new PlayerPreference.Definition<>("materials", ArchitectMessages.ARCHITECTDEMOLITION_PREFERENCE_MATERIALS, Materials.ALL, List.of(
          new PlayerPreference.Choice<>(Materials.ALL, ArchitectMessages.ARCHITECTDEMOLITION_PREFERENCE_MATERIALS_ALL, Id.minecraft("stone"), 1),
          new PlayerPreference.Choice<>(Materials.WOOD, ArchitectMessages.ARCHITECTDEMOLITION_PREFERENCE_MATERIALS_WOOD, Id.minecraft("oak_planks"), 1),
          new PlayerPreference.Choice<>(Materials.STONE, ArchitectMessages.ARCHITECTDEMOLITION_PREFERENCE_MATERIALS_STONE, Id.minecraft("stone_bricks"), 1),
          new PlayerPreference.Choice<>(Materials.GLASS, ArchitectMessages.ARCHITECTDEMOLITION_PREFERENCE_MATERIALS_GLASS, Id.minecraft("glass"), 1))));

  private final Map<BlockKey, DemolitionMark> placed;
  private final Map<UUID, ConcurrentLinkedDeque<BlockKey>> order;

  public ArchitectDemolition() {
    super("architect-demolition");
    registerConfiguration(ArchitectDemolition.Config.class);
    setIcon(Id.minecraft("tnt"));
    setInterval(10880);
    placed = new ConcurrentHashMap<>();
    order = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("tnt"))
        .key("challenge_architect_demolition_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("tnt"))
            .key("challenge_architect_demolition_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_architect_demolition_500", "architect.demolition.blocks-demolished", 500, 300);
    registerMilestone("challenge_architect_demolition_5k", "architect.demolition.blocks-demolished", 5000, 1000);
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
    return List.of(CommonPreferences.ENABLED, SNEAK, MATERIALS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.DEMOLITION_LORE1));
    statLore(v, C.GREEN, "", getWindowMillis(getLevelPercent(level)) / 1000, 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    PlayerRef p = e.player();
    if (getActiveLevel(p) <= 0) {
      return;
    }

    ItemRef placedItem = snapshotPlacedItem(e.itemInHand(), e.block().state().placementItem());
    if (placedItem == null) {
      return;
    }

    UUID id = p.uuid();
    BlockKey block = BlockKey.of(e.block());
    ConcurrentLinkedDeque<BlockKey> deque = order.computeIfAbsent(id, unused -> new ConcurrentLinkedDeque<>());
    deque.addLast(block);
    placed.put(block, new DemolitionMark(id, M.ms(), placedItem));
    fx(e.block().location().add(0.5, 0.5, 0.5), FxPriority.AMBIENT)
        .burst(Particles.CRIT_MAGIC, 2, 0.1D);
    int cap = trackingCap(getConfig().maxTrackedPerPlayer);
    while (deque.size() > cap) {
      BlockKey oldest = deque.pollFirst();
      if (oldest != null) {
        placed.remove(oldest);
      }
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    if (placed.isEmpty()) {
      return;
    }

    BlockKey key = BlockKey.of(e.block());
    DemolitionMark mark = placed.get(key);
    if (mark == null) {
      return;
    }

    PlayerRef p = e.player();
    if (!mark.owner().equals(p.uuid()) || !acceptsMaterial(p, e.block().type())
        || (preferenceEnabled(p, SNEAK) && !p.isSneaking())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.block().location());
    if (context == null) {
      return;
    }

    if (M.ms() - mark.at() > getWindowMillis(getLevelPercent(context.level()))) {
      placed.remove(key, mark);
      return;
    }

    e.setInstaBreak(true);
    fx(e.block().location().add(0.5, 0.5, 0.5), FxPriority.COMBAT)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_HIT, 0.5f, 0.7f);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (placed.isEmpty()) {
      return;
    }

    DemolitionMark mark = placed.remove(BlockKey.of(e.block()));
    if (mark == null) {
      return;
    }

    PlayerRef p = e.player();
    if (!mark.owner().equals(p.uuid()) || !acceptsMaterial(p, e.block().type())
        || (preferenceEnabled(p, SNEAK) && !p.isSneaking())) {
      return;
    }

    int level = getActiveBlockBreakLevel(p, e.block().location());
    if (level <= 0) {
      return;
    }

    if (M.ms() - mark.at() > getWindowMillis(getLevelPercent(level))) {
      return;
    }

    List<ItemRef> restitution = collectRestitution(e.block(), mark, p);
    e.setDropItems(false);
    e.setExperience(0);
    restitute(p, e.block().location().add(0.5, 0.5, 0.5), restitution);

    fx(e.block().location().add(0.5, 0.5, 0.5), FxPriority.COMBAT)
        .ring(Particles.SCRAPE, 0.8D, 16, 0.2D)
        .dustBurst(new Rgb(255, 120, 40), 6, 0.4D, 1.0F)
        .chord(Sounds.BLOCK_STONE_BREAK, 0.6f, 1.0f, Sounds.BLOCK_ANVIL_LAND, 0.15f, 1.6f);

    addStat(p, "architect.demolition.blocks-demolished", 1);
    xp(p, getConfig().xpPerDemolish);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    ConcurrentLinkedDeque<BlockKey> deque = order.remove(e.player().uuid());
    if (deque == null) {
      return;
    }

    for (BlockKey block : deque) {
      placed.remove(block);
    }
  }

  /**
   * Collects everything an erased placement owes the player: the item that was
   * placed plus whatever the block was holding at the time of the break.
   */
  private List<ItemRef> collectRestitution(BlockRef block, DemolitionMark mark, PlayerRef p) {
    if (keepsContentsInItem(block.type())) {
      List<ItemRef> drops = new ArrayList<>(block.drops(p.inventory().mainHand(), p));
      if (!drops.isEmpty()) {
        return drops;
      }

      return restitution(placedOrFallback(mark, block), null);
    }

    InventoryRef inventory = block.blockInventory();
    List<ItemRef> items = restitution(placedOrFallback(mark, block),
        inventory == null ? null : inventory.contents());
    if (inventory != null) {
      clearInventory(inventory);
    }
    return items;
  }

  static void clearInventory(InventoryRef inventory) {
    for (int slot = 0; slot < inventory.size(); slot++) {
      inventory.clear(slot);
    }
  }

  private void restitute(PlayerRef p, Loc location, List<ItemRef> items) {
    WorldRef world = location.world();
    for (ItemRef item : items) {
      if (item == null || item.type().isAir() || item.amount() <= 0) {
        continue;
      }

      for (ItemRef overflow : p.inventory().addItem(item)) {
        if (world != null && overflow != null && !overflow.type().isAir()) {
          world.dropItemNaturally(location.position(), overflow);
        }
      }
    }
  }

  private ItemRef placedOrFallback(DemolitionMark mark, BlockRef block) {
    ItemRef snapshot = mark.placed();
    if (snapshot != null && !snapshot.type().isAir()) {
      return snapshot.copy();
    }

    Id type = block.type();
    ItemOps items = AdaptCore.platform().host().items();
    return !type.isAir() && items.isItem(type) ? items.create(type, 1) : null;
  }

  private long getWindowMillis(double factor) {
    return (long) Math.max(1000, M.lerp(getConfig().minWindowSeconds, getConfig().maxWindowSeconds, factor) * 1000D);
  }

  static int trackingCap(int configuredCap) {
    return Math.max(0, configuredCap);
  }

  static ItemRef snapshotPlacedItem(ItemRef hand, Id placementMaterial) {
    if (hand == null || hand.type().isAir() || hand.amount() <= 0
        || !hand.type().equals(placementMaterial)) {
      return null;
    }

    return hand.copyWithAmount(1);
  }

  /**
   * Shulker boxes carry their contents inside the dropped item, so their
   * inventory must never be harvested separately.
   */
  static boolean keepsContentsInItem(Id type) {
    return type != null && type.path().endsWith("shulker_box");
  }

  static List<ItemRef> restitution(ItemRef placedItem, List<ItemRef> contents) {
    List<ItemRef> items = new ArrayList<>();
    if (placedItem != null && !placedItem.type().isAir()) {
      items.add(placedItem);
    }

    if (contents == null) {
      return items;
    }

    for (ItemRef content : contents) {
      if (content == null || content.type().isAir() || content.amount() <= 0) {
        continue;
      }

      items.add(content.copy());
    }

    return items;
  }

  private record DemolitionMark(UUID owner, long at, ItemRef placed) {
  }

  record BlockKey(UUID worldId, BlockPos pos) {
    static BlockKey of(BlockRef block) {
      return new BlockKey(block.world().uid(), block.pos());
    }
  }

  @ConfigDescription("Blocks you recently placed break near-instantly and hand the placed item plus any stored contents straight back to you.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "How many seconds a placement counts as recent at level 0 progression.", impact = "Higher values let low-level players instabreak older placements.")
    double minWindowSeconds = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How many seconds a placement counts as recent at maximum level progression.", impact = "Higher values let max-level players instabreak older placements.")
    double maxWindowSeconds = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum number of recent placements tracked per player.", impact = "Higher values remember more placements at slightly more memory cost.")
    int maxTrackedPerPlayer = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Adaptation xp granted per demolished block.", impact = "Higher values speed up adaptation progression from demolition.")
    double xpPerDemolish = 1;

    public Config() {
      baseCost = 2;
      initialCost = 1;
      costFactor = 0.5;
    }
  }

  public enum Materials { ALL, WOOD, STONE, GLASS }
}
