package art.arcane.adapt.api.mutation;

import java.util.Objects;

public record MutationLineage(MutationDomain first, MutationDomain second) {
  public MutationLineage {
    Objects.requireNonNull(first);
    Objects.requireNonNull(second);
    if (first == second) {
      throw new IllegalArgumentException("A Mutation lineage requires two distinct Domains");
    }
    if (first.ordinal() > second.ordinal()) {
      MutationDomain swap = first;
      first = second;
      second = swap;
    }
  }

  public static MutationLineage of(MutationDomain first, MutationDomain second) {
    return new MutationLineage(first, second);
  }
}
