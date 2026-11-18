package art.arcane.adapt.platform.event;

@FunctionalInterface
public interface EventListener<E> {
  void handle(E event) throws Exception;
}
