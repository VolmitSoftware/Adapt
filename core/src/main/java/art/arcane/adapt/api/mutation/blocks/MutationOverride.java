package art.arcane.adapt.api.mutation.blocks;

import art.arcane.adapt.api.mutation.signature.Signature;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A hand-written replacement for one generated mutated adaptation. A text row replaces the description; a null block
 * list keeps the generated blocks; a {@code stateWindowSeconds} above 0 lets its State blocks count when they held
 * within that window. A Barter description starts after the gesture, which is prefixed from {@link BarterGesture#of}.
 *
 * <p>A signature row ({@code handler} set) replaces the adaptation entirely: it has its own {@code name},
 * {@code description} and stat labels ({@code lore}), no blocks, and its effect is the named {@link Signature}
 * subclass, which the lead skill builds and {@link art.arcane.adapt.api.mutation.engine.MutationEngine} dispatches.
 * Players see every row through {@link MutatedText}, translated as its own messages.
 */
public record MutationOverride(String leadId, String partnerId, Recipe recipe, String description,
                               @Nullable List<BlockRef> blocks, int stateWindowSeconds, @Nullable String name,
                               List<String> lore, @Nullable Class<? extends Signature<?>> handler) {
  public MutationOverride {
    blocks = blocks == null ? null : List.copyOf(blocks);
    lore = lore == null ? List.of() : List.copyOf(lore);
  }

  public MutationOverride(String leadId, String partnerId, Recipe recipe, String description,
                          @Nullable List<BlockRef> blocks, int stateWindowSeconds) {
    this(leadId, partnerId, recipe, description, blocks, stateWindowSeconds, null, List.of(), null);
  }

  public boolean isSignature() {
    return handler != null;
  }

  public MutatedAdaptation applyTo(MutatedAdaptation generated) {
    if (isSignature()) {
      return new MutatedAdaptation(generated.leadId(), generated.partnerId(), generated.recipe(), name,
          MutatedText.override(MutatedText.ENGLISH, this), List.of(), generated.form(), 0);
    }
    return new MutatedAdaptation(generated.leadId(), generated.partnerId(), generated.recipe(),
        generated.name(), MutatedText.override(MutatedText.ENGLISH, this),
        blocks == null ? generated.blocks() : blocks,
        generated.form(), stateWindowSeconds > 0 ? stateWindowSeconds : generated.stateWindowSeconds());
  }
}
