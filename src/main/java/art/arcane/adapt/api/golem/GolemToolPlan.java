package art.arcane.adapt.api.golem;

import java.util.List;

public record GolemToolPlan(List<GolemToolNeed> needs, boolean free) {
  public static final GolemToolPlan NONE = new GolemToolPlan(List.of(), true);

  public GolemToolPlan {
    needs = List.copyOf(needs);
  }
}
