package art.arcane.adapt.platform;

import org.jetbrains.annotations.ApiStatus;

/**
 * Optional platform features. Only third-party integrations and Folia regions are optional; every
 * Adapt gameplay feature (golemancy, advancements, displays, virtual players) is part of the core SPI.
 * Bukkit reports a capability when the matching plugin is enabled. Modded platforms report only
 * {@link #PROTECTION_PLUGINS}, when a claim mod is loaded (FTB Chunks, Open Parties and Claims, Flan, Cadmus or
 * Get Off My Lawn).
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum Capability {
  /** Region-threaded scheduling (Folia). Without it every region check is true. */
  FOLIA_REGIONS,
  /** PlaceholderAPI is enabled. */
  PLACEHOLDERS,
  /** Vault is enabled. */
  ECONOMY,
  /** Claim and protection plugins (GriefPrevention, Factions, Residence and the rest). */
  PROTECTION_PLUGINS,
  WORLDGUARD,
  HIDDEN_ORE,
  ADVANCED_CHESTS,
  MAGIC_COSMETICS
}
