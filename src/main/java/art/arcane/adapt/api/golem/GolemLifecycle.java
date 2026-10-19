package art.arcane.adapt.api.golem;

interface GolemLifecycle {
  void awake(GolemHandle handle);

  void asleep(GolemHandle handle);

  void detached(GolemHandle handle);
}
