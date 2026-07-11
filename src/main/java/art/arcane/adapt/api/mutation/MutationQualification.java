package art.arcane.adapt.api.mutation;

import java.util.List;

public record MutationQualification(
    boolean qualified,
    boolean firstDomainQualified,
    boolean secondDomainQualified,
    List<String> qualifyingAdaptations,
    String reason
) {
  public MutationQualification {
    qualifyingAdaptations = qualifyingAdaptations == null ? List.of() : List.copyOf(qualifyingAdaptations);
    reason = reason == null ? "" : reason;
  }

  public static MutationQualification rejected(String reason) {
    return new MutationQualification(false, false, false, List.of(), reason);
  }
}
