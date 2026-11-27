package art.arcane.adapt.engine.protection;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.engine.bridge.HostKeys;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The protectors registered through {@code AdaptApi.protection()}. Core resolves which of them apply to an
 * adaptation from their names and the {@code protectionOverrides} config, then asks exactly that set; a check made
 * for no adaptation asks every protector enabled by default. A protector that throws denies and is logged once.
 */
public final class Protectors {
  private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

  private Protectors() {
  }

  /** Names of registered protectors enabled by default, in registration order. */
  public static List<String> defaultNames() {
    List<Protector> registered = Engine.protectors().protectors(HostKeys.key());
    List<String> names = new ArrayList<>(registered.size());
    for (Protector protector : registered) {
      if (protector.enabledByDefault()) {
        names.add(protector.name());
      }
    }
    return names;
  }

  /** Names of every registered protector, in registration order. */
  public static List<String> allNames() {
    List<Protector> registered = Engine.protectors().protectors(HostKeys.key());
    List<String> names = new ArrayList<>(registered.size());
    for (Protector protector : registered) {
      names.add(protector.name());
    }
    return names;
  }

  /**
   * True when every named protector allows the check. Without a location the victim's, then the player's, is
   * used.
   */
  public static boolean allows(Set<String> names, ProtectionCheck check, PlayerRef player, Loc location,
                               EntityRef victim, Id adaptation) {
    if (names == null || names.isEmpty() || player == null) {
      return true;
    }
    Loc at = at(player, location, victim);
    if (at == null) {
      return true;
    }
    for (Protector protector : Engine.protectors().protectors(HostKeys.key())) {
      if (names.contains(protector.name()) && !ask(protector, check, player, at, victim, adaptation)) {
        return false;
      }
    }
    return true;
  }

  /** True when every protector enabled by default allows a check made for no adaptation. */
  public static boolean defaultsAllow(ProtectionCheck check, PlayerRef player, Loc location, EntityRef victim) {
    if (player == null) {
      return true;
    }
    Loc at = at(player, location, victim);
    if (at == null) {
      return true;
    }
    for (Protector protector : Engine.protectors().protectors(HostKeys.key())) {
      if (protector.enabledByDefault() && !ask(protector, check, player, at, victim, null)) {
        return false;
      }
    }
    return true;
  }

  private static Loc at(PlayerRef player, Loc location, EntityRef victim) {
    return location != null ? location : victim != null ? victim.location() : player.location();
  }

  private static boolean ask(Protector protector, ProtectionCheck check, PlayerRef player, Loc at, EntityRef victim,
                             Id adaptation) {
    try {
      return protector.allows(check, player, at, victim, adaptation);
    } catch (RuntimeException | LinkageError error) {
      String name = protector.name();
      if (FAILED.add(name)) {
        AdaptApi.log().error("Protector '" + name + "' (" + protector.getClass().getName()
            + ") threw on a " + check + " check; its checks deny until it stops throwing", error);
      }
      return false;
    }
  }
}
