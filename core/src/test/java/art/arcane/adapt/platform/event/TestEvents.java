package art.arcane.adapt.platform.event;

final class TestEvents {
  private TestEvents() {
  }

  static class Base extends CancellableEvent {
    int value;
  }

  static class Child extends Base {
  }

  static final class Plain extends AdaptEvent {
    int hits;
  }
}
