package art.arcane.adapt.api.golem;

import java.util.List;

public interface GolemStatSource {
  String id();

  List<GolemStatDelta> deltas(GolemHandle handle);
}
