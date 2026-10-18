package art.arcane.adapt.api.golem;

import java.util.Optional;

public interface GolemCoreBehavior extends GolemActor {
  Optional<GolemTask> seek(GolemContext golem);
}
