package art.arcane.adapt.api.claims;

import java.util.List;

public record ClaimState(List<ClaimCity> cities, List<ClaimPlot> plots, List<ClaimProgress> progress) {
  public ClaimState {
    cities = List.copyOf(cities);
    plots = List.copyOf(plots);
    progress = List.copyOf(progress);
  }

  public static ClaimState empty() {
    return new ClaimState(List.of(), List.of(), List.of());
  }
}
