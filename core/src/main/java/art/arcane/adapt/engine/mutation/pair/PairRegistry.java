package art.arcane.adapt.engine.mutation.pair;

import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.PairText;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.text.Text;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Per-pair content any installed addon declared: signatures and texts per slot, names per pair. Each entry carries
 * the namespace of the addon that claimed it. A claim is held whether or not its parents are registered; it applies
 * once the pair's mutated skill exists, which needs both.
 */
public final class PairRegistry {
  private final Map<String, PairClaims> byOwner = new LinkedHashMap<>();
  private volatile Index index = Index.EMPTY;
  private volatile long revision;

  /** One declaration and the addon that made it. */
  public record Claim<T>(String owner, T value) {
  }

  public Optional<Claim<Function<MutationSlot, ? extends Signature<?>>>> signature(MutationSlot slot) {
    return Optional.ofNullable(index.signatures().get(slot));
  }

  /** The slot's text; empty when the slot has a signature, which brings its own. */
  public Optional<Claim<PairText>> text(MutationSlot slot) {
    Index current = index;
    return current.signatures().containsKey(slot) ? Optional.empty() : Optional.ofNullable(current.texts().get(slot));
  }

  public Optional<Claim<Text>> name(SkillPair pair) {
    return Optional.ofNullable(index.names().get(pair));
  }

  /** Every slot some addon registered a signature for. */
  public List<MutationSlot> signatureSlots() {
    return List.copyOf(index.signatures().keySet());
  }

  /** Grows on every install or uninstall that changes a claim. */
  public long revision() {
    return revision;
  }

  /**
   * Throws when {@code claims} takes a slot or pair name another installed addon holds, naming both addons. The
   * addon's own earlier claims do not count, so a refresh may claim them again.
   */
  public synchronized void requireFree(PairClaims claims) {
    for (PairClaims held : byOwner.values()) {
      if (held.owner().equals(claims.owner())) {
        continue;
      }
      for (MutationSlot slot : claims.signatures().keySet()) {
        if (held.signatures().containsKey(slot)) {
          throw refused("signature for " + slot, held.owner(), claims.owner());
        }
      }
      for (MutationSlot slot : claims.texts().keySet()) {
        if (held.texts().containsKey(slot)) {
          throw refused("pair text for " + slot, held.owner(), claims.owner());
        }
      }
      for (SkillPair pair : claims.names().keySet()) {
        if (held.names().containsKey(pair)) {
          throw refused("name of " + pair, held.owner(), claims.owner());
        }
      }
    }
  }

  /** Puts in the addon's claims in place of any it held; {@link #requireFree} must have passed. */
  public synchronized void install(PairClaims claims) {
    requireFree(claims);
    PairClaims previous = claims.isEmpty() ? byOwner.remove(claims.owner()) : byOwner.put(claims.owner(), claims);
    if (previous != null || !claims.isEmpty()) {
      changed();
    }
  }

  /** Takes back every claim of {@code owner}; true when it held any. */
  public synchronized boolean withdraw(String owner) {
    if (byOwner.remove(owner) == null) {
      return false;
    }
    changed();
    return true;
  }

  private void changed() {
    Map<MutationSlot, Claim<Function<MutationSlot, ? extends Signature<?>>>> signatures = new HashMap<>();
    Map<MutationSlot, Claim<PairText>> texts = new HashMap<>();
    Map<SkillPair, Claim<Text>> names = new HashMap<>();
    for (PairClaims claims : byOwner.values()) {
      claims.signatures().forEach((slot, factory) -> signatures.put(slot, new Claim<>(claims.owner(), factory)));
      claims.texts().forEach((slot, text) -> texts.put(slot, new Claim<>(claims.owner(), text)));
      claims.names().forEach((pair, name) -> names.put(pair, new Claim<>(claims.owner(), name)));
    }
    index = new Index(Map.copyOf(signatures), Map.copyOf(texts), Map.copyOf(names));
    revision++;
  }

  private static IllegalStateException refused(String what, String holder, String claimant) {
    return new IllegalStateException("The " + what + " is registered by addon " + holder + "; addon " + claimant
        + " cannot register it too");
  }

  private record Index(Map<MutationSlot, Claim<Function<MutationSlot, ? extends Signature<?>>>> signatures,
                       Map<MutationSlot, Claim<PairText>> texts, Map<SkillPair, Claim<Text>> names) {
    private static final Index EMPTY = new Index(Map.of(), Map.of(), Map.of());
  }
}
