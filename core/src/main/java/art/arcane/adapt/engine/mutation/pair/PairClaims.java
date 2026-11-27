package art.arcane.adapt.engine.mutation.pair;

import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.PairText;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** The signatures, texts and names one addon declared, each slot or pair at most once. */
public record PairClaims(String owner, Map<MutationSlot, Function<MutationSlot, ? extends Signature<?>>> signatures,
                         Map<MutationSlot, PairText> texts, Map<SkillPair, Text> names) {
  public PairClaims {
    Objects.requireNonNull(owner, "owner");
    signatures = Map.copyOf(signatures);
    texts = Map.copyOf(texts);
    names = Map.copyOf(names);
  }

  public static PairClaims none(String owner) {
    return new PairClaims(owner, Map.of(), Map.of(), Map.of());
  }

  public boolean isEmpty() {
    return signatures.isEmpty() && texts.isEmpty() && names.isEmpty();
  }

  /** Collects one addon's claims and refuses a second claim on the same slot or pair. */
  public static final class Builder {
    private final String owner;
    private final Map<MutationSlot, Function<MutationSlot, ? extends Signature<?>>> signatures = new LinkedHashMap<>();
    private final Map<MutationSlot, PairText> texts = new LinkedHashMap<>();
    private final Map<SkillPair, Text> names = new LinkedHashMap<>();

    public Builder(String owner) {
      this.owner = Objects.requireNonNull(owner, "owner");
    }

    public void signature(MutationSlot slot, Function<MutationSlot, ? extends Signature<?>> factory) {
      Objects.requireNonNull(factory, "factory");
      if (signatures.putIfAbsent(slot, factory) != null) {
        throw new IllegalArgumentException("Addon " + owner + " registers a signature for " + slot + " twice");
      }
    }

    public void text(MutationSlot slot, PairText text) {
      Objects.requireNonNull(text, "text");
      if (texts.putIfAbsent(slot, text) != null) {
        throw new IllegalArgumentException("Addon " + owner + " registers pair text for " + slot + " twice");
      }
    }

    public void name(SkillPair pair, Text name) {
      Objects.requireNonNull(name, "name");
      if (names.putIfAbsent(pair, name) != null) {
        throw new IllegalArgumentException("Addon " + owner + " registers a name for " + pair + " twice");
      }
    }

    public PairClaims build() {
      return new PairClaims(owner, signatures, texts, names);
    }
  }
}
