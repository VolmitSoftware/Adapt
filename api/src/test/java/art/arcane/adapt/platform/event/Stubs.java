package art.arcane.adapt.platform.event;

import art.arcane.adapt.platform.entity.PlayerRef;

import java.lang.reflect.Proxy;
import java.util.UUID;

/** Minimal handle stubs for event tests. */
final class Stubs {
  private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private Stubs() {
  }

  static PlayerRef player() {
    return (PlayerRef) Proxy.newProxyInstance(PlayerRef.class.getClassLoader(), new Class<?>[] {PlayerRef.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "uuid" -> PLAYER_ID;
          case "name" -> "Tester";
          case "isOnline", "isValid" -> true;
          case "hashCode" -> PLAYER_ID.hashCode();
          case "equals" -> proxy == args[0];
          case "toString" -> "PlayerRef[Tester]";
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }
}
