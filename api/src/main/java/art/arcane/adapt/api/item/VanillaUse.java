package art.arcane.adapt.api.item;

import org.jetbrains.annotations.ApiStatus;

/**
 * The vanilla things a custom item's base item can do. A custom item refuses every use its definition does not
 * allow, so a bound ender pearl is never thrown as a plain one and a backpack is never placed as a chest.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum VanillaUse {
  /** Placed as a block. */
  PLACE,
  /** Used in hand: thrown, eaten, drawn, opened. */
  USE,
  /** Eaten or drunk. */
  CONSUME,
  /** Fired or placed by a dispenser. */
  DISPENSE,
  /** Smelted, or burned as fuel. */
  FURNACE,
  /** An ingredient at a crafting table, anvil, smithing table, grindstone, loom, stonecutter or brewing stand. */
  WORKSTATION,
  /** Moved by a hopper or dropper into a container. */
  INSERT,
  /** Used on an entity. */
  ENTITY,
  /** Sold to a villager. */
  TRADE
}
