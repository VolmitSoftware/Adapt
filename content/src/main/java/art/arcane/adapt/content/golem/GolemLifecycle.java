package art.arcane.adapt.content.golem;

interface GolemLifecycle {
  void awake(GolemHandle handle);

  void asleep(GolemHandle handle);

  void detached(GolemHandle handle);
}
