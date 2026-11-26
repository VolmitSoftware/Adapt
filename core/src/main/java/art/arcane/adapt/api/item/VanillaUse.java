package art.arcane.adapt.api.item;

/** A vanilla interaction that would place, spend or transform an item. Adapt items get none unless they declare it. */
public enum VanillaUse {
  /** Set down as a block or a plant. */
  PLACE,
  /** The material's own right-click action: throwing, drinking, casting, using it on a block. */
  USE,
  /** Finishing eating or drinking it. */
  CONSUME,
  /** Fired, thrown or placed by a dispenser. */
  DISPENSE,
  /** Cooked or burnt as fuel by a furnace, smoker or blast furnace. */
  FURNACE,
  /** Taken by an anvil, smithing table, grindstone, stonecutter, loom, cartography table, enchanting table or brewing stand. */
  WORKSTATION,
  /** Put into a block from the hand: composter, flower pot, lectern, bookshelf, pot, campfire, cauldron, jukebox. */
  INSERT,
  /** Handed to an item frame, an armor stand or an allay. */
  ENTITY,
  /** Paid to a villager or wandering trader. */
  TRADE
}
