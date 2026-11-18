package art.arcane.adapt.platform;

/**
 * Optional platform features. Only third-party integrations and Folia regions are optional; every
 * Adapt gameplay feature (golemancy, advancements, displays, virtual players) is part of the core SPI.
 */
public enum Capability {
  /** Region-threaded scheduling (Folia). Without it every region check is true. */
  FOLIA_REGIONS,
  PLACEHOLDERS,
  ECONOMY,
  /** Claim and protection plugins (GriefPrevention, Factions, Residence and the rest). */
  PROTECTION_PLUGINS,
  WORLDGUARD,
  HIDDEN_ORE,
  ADVANCED_CHESTS,
  MAGIC_COSMETICS
}
