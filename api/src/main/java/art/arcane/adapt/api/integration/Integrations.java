package art.arcane.adapt.api.integration;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What Adapt learns from the platform and from other plugins it integrates with, for content that should behave
 * like the built-in skills around them. Each answer falls back to a neutral one where the platform or the plugin is
 * absent: nothing is marked, shielded or hidden, every player is real and every teleport goes through. Needs a
 * running core.
 */
@ApiStatus.Experimental
public final class Integrations {
  private Integrations() {
  }

  /** False for fake players: mod FakePlayers, NPC plugin player classes, spoofed handles. */
  public static boolean isServerPlayer(PlayerRef player) {
    return Engine.bridge().integrations().isServerPlayer(Objects.requireNonNull(player, "player"));
  }

  /** True for entities an adaptation must leave alone: NPCs, marker armour stands, minions. */
  public static boolean isShielded(EntityRef entity) {
    return Engine.bridge().integrations().isShielded(Objects.requireNonNull(entity, "entity"));
  }

  /** Whether another plugin marked the entity, such as {@code NPC}, {@code shopitem} or {@code hologram} on Bukkit. */
  public static boolean marked(EntityRef entity, String marker) {
    return Engine.bridge().integrations().marked(Objects.requireNonNull(entity, "entity"),
        Objects.requireNonNull(marker, "marker"));
  }

  /** The movement keys the player's client last reported; empty where the platform cannot read them. */
  public static Optional<PlayerInput> input(PlayerRef player) {
    return Optional.ofNullable(Engine.bridge().integrations().input(Objects.requireNonNull(player, "player")));
  }

  /**
   * Announces a teleport an adaptation is about to make, so other plugins can cancel or redirect it. Teleport the
   * player to the returned location; empty means a listener cancelled it.
   */
  public static Optional<Loc> announceTeleport(PlayerRef player, Adaptation<?> source, Loc from, Loc to) {
    return Optional.ofNullable(Engine.bridge().integrations().announceTeleport(Objects.requireNonNull(player, "player"),
        Objects.requireNonNull(source, "source"), Objects.requireNonNull(from, "from"), Objects.requireNonNull(to, "to")));
  }

  /** HiddenOre veins within {@code radius} blocks of {@code origin}; empty without HiddenOre. */
  public static List<HiddenVein> hiddenVeins(Loc origin, int radius) {
    return Engine.bridge().integrations().hiddenVeins(Objects.requireNonNull(origin, "origin"), radius);
  }

  /** The other blocks of the HiddenOre vein {@code block} belongs to; empty without HiddenOre. */
  public static List<BlockRef> hiddenVeinSiblings(BlockRef block) {
    return Engine.bridge().integrations().hiddenVeinSiblings(Objects.requireNonNull(block, "block"));
  }

  /** Whether Iris fells trees on this server. */
  public static boolean treeFellingAvailable() {
    return Engine.bridge().integrations().treeFellingAvailable();
  }

  /** Whether Iris counts the block as part of a tree it can fell. */
  public static boolean isTreeBlock(BlockRef block) {
    return Engine.bridge().integrations().isTreeBlock(Objects.requireNonNull(block, "block"));
  }

  /** True for a break Iris makes itself while felling; handlers that react to breaks should skip it. */
  public static boolean isFellingBreak(BlockBreakEvent event) {
    return Engine.bridge().integrations().isFellingBreak(Objects.requireNonNull(event, "event"));
  }

  /**
   * Asks Iris to fell the tree the broken block belongs to. {@code durabilityPreservationChance} is the percent
   * chance, 0 to 100, that a log costs the axe no durability. False when Iris is absent or declined.
   */
  public static boolean fellTree(BlockBreakEvent event, int durabilityPreservationChance, TreeFelling felling) {
    return Engine.bridge().integrations().fellTree(Objects.requireNonNull(event, "event"),
        Math.max(0, Math.min(100, durabilityPreservationChance)), Objects.requireNonNull(felling, "felling"));
  }

  /** Whether another plugin, such as AdvancedChests, keeps its own container at the block. */
  public static PluginContainer pluginContainer(Loc block) {
    return Engine.bridge().integrations().pluginContainer(Objects.requireNonNull(block, "block"));
  }

  /**
   * Opens the plugin container at the block for the player, on its first page. Returns the top inventory of the view
   * that opened; empty when none opened.
   */
  public static Optional<InventoryRef> openPluginContainer(PlayerRef player, Loc block) {
    return Optional.ofNullable(Engine.bridge().integrations().openPluginContainer(Objects.requireNonNull(player, "player"),
        Objects.requireNonNull(block, "block")));
  }

  /**
   * Flags the entity so mob stacker plugins leave it alone, for summons and decoys that must stay single. The flag is
   * the shared {@code volmit:no-stack} entity data, which outlives a restart.
   */
  public static void excludeFromStacking(EntityRef entity) {
    Engine.bridge().integrations().excludeFromStacking(Objects.requireNonNull(entity, "entity"));
  }

  /** Lets stacking plugins merge the entity again after {@link #excludeFromStacking(EntityRef)}. */
  public static void includeInStacking(EntityRef entity) {
    Engine.bridge().integrations().includeInStacking(Objects.requireNonNull(entity, "entity"));
  }

  /** Whether {@code stack} is a stacking plugin's bundle item (React super stacks on Bukkit) holding other items. */
  public static boolean isSuperStack(ItemRef stack) {
    return Engine.bridge().integrations().isSuperStack(Objects.requireNonNull(stack, "stack"));
  }

  /** The items a super stack holds; empty for any other item. */
  public static List<ItemRef> superStackItems(ItemRef stack) {
    return Engine.bridge().integrations().superStackItems(Objects.requireNonNull(stack, "stack"));
  }

  /** A copy of the super stack {@code stack} that holds {@code items} instead, or empty when it cannot be rebuilt. */
  public static Optional<ItemRef> withSuperStackItems(ItemRef stack, List<ItemRef> items) {
    return Optional.ofNullable(Engine.bridge().integrations().withSuperStackItems(Objects.requireNonNull(stack, "stack"),
        List.copyOf(items)));
  }
}
