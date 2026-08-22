package art.arcane.adapt.api.protection;

import org.bukkit.Location;
import org.bukkit.entity.Player;

final class FakeRegionPolicySource implements RegionPolicySource {
  private RegionPolicy policy = RegionPolicy.DEFAULT;
  private RuntimeException failure;
  private int calls;

  @Override
  public String getName() {
    return "Fake";
  }

  @Override
  public RegionPolicy resolve(Player player, Location location) {
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
