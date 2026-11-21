package art.arcane.adapt.platform;

/**
 * Engine mechanics that exist only on some Minecraft versions and have no registry entry of their own. Content
 * (items, entities, attributes, effects, enchantments) is checked through {@link RegistryLookup#exists} instead.
 */
public enum EngineFeature {
  /** Item data components instead of the NBT tag (1.20.5+). */
  ITEM_DATA_COMPONENTS,
  /** Enchantments loaded from data packs (1.21+). */
  DATA_DRIVEN_ENCHANTMENTS,
  /** Display entities interpolate teleports ({@code teleport_duration}, 1.20.2+). */
  DISPLAY_TELEPORT_INTERPOLATION,
  /** Server tick rate manager: {@code /tick}, freezing and stepping (1.20.3+). */
  TICK_RATE_MANAGER,
  /** Bundles usable without an experimental data pack (1.21.2+). */
  BUNDLES,
  /** Max stack size and max durability set per item stack ({@code max_stack_size}, {@code max_damage}, 1.20.5+). */
  ITEM_STACK_LIMITS,
  /** Item model set per item stack ({@code item_model}, 1.21.2+). */
  ITEM_MODEL,
  /** Cooldown groups set per item stack ({@code use_cooldown}, 1.21.2+); before that a cooldown covers an item type. */
  ITEM_COOLDOWN_GROUPS,
  /**
   * The client reports its movement keys, jump and sprint included, in the player input packet (1.21.2+). Without it
   * horizontal input is inferred from velocity, and a mid-air jump press is seen through the Adapt client's jump key
   * reports ({@link art.arcane.adapt.util.common.input.ReportedJumps}) or else as a flight request
   * ({@link art.arcane.adapt.util.common.input.MidairJumps}).
   */
  PLAYER_INPUT
}
