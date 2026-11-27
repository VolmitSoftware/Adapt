package art.arcane.adapt.engine.protection;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;

final class FakeRegionPolicySource implements RegionPolicySource {
  private RegionPolicy policy = RegionPolicy.DEFAULT;
  private RuntimeException failure;
  private int calls;

  @Override
  public String getName() {
    return "Fake";
  }

  @Override
  public RegionPolicy resolve(PlayerRef player, Loc location) {
    calls++;
    if (failure != null) {
      throw failure;
    }
    return policy;
  }

  void setPolicy(RegionPolicy policy) {
    this.policy = policy;
  }

  void setFailure(RuntimeException failure) {
    this.failure = failure;
  }

  int getCalls() {
    return calls;
  }
}
