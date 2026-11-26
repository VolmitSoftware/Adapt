package art.arcane.adapt.api.mutation.blocks;

import java.util.List;

/**
 * A stronger adaptation that replaces the originals it fuses once its skill is mutated. {@code sources} are the
 * display names of the fused adaptations in the same skill. {@code price} is {@link MutationPrice#UPGRADED}, or
 * {@link MutationPrice#TOOL} for an upgrade that hands the player a craftable tool or deployable.
 */
public record UpgradedAdaptation(String name, List<String> sources, String description, MutationPrice price) {
  public UpgradedAdaptation {
    sources = List.copyOf(sources);
  }

  public boolean tool() {
    return price.equals(MutationPrice.TOOL);
  }
}
