package art.arcane.adapt.api.integration;

import org.jetbrains.annotations.ApiStatus;

/**
 * Whether another plugin, such as AdvancedChests, keeps its own container at a block.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.Experimental
public enum PluginContainer {
  /** No plugin container at the block; a vanilla container there opens the usual way. */
  NONE,
  /** A plugin container sits at the block; open it with {@link Integrations#openPluginContainer}. */
  PRESENT,
  /** The plugin failed to answer; treat the block as unusable for now. */
  FAILED
}
