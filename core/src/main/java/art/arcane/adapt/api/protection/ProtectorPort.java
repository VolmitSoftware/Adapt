package art.arcane.adapt.api.protection;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;
import java.util.Set;

/**
 * The platform's protector registry (claim, region and lock plugins). Core resolves which protectors apply to
 * an adaptation from their names and the {@code protectionOverrides} config, then asks the port to evaluate
 * exactly that set. Without an installed port no protector exists and every check passes.
 */
public interface ProtectorPort {
  ProtectorPort NONE = new ProtectorPort() {
    @Override
    public List<String> defaultProtectors() {
      return List.of();
    }

    @Override
    public List<String> allProtectors() {
      return List.of();
    }

    @Override
    public boolean allows(Set<String> protectors, ProtectionCheck check, PlayerRef player, Loc location,
                          EntityRef victim, Adaptation<?> adaptation) {
      return true;
    }
  };

  /** Names of registered protectors enabled by default, in registration order. */
  List<String> defaultProtectors();

  /** Names of every registered protector, in registration order. */
  List<String> allProtectors();

  /**
   * True when every named protector allows the check. {@code victim} is set only for an entity PVE check, in
   * which case {@code location} is the victim's location.
   */
  boolean allows(Set<String> protectors, ProtectionCheck check, PlayerRef player, Loc location, EntityRef victim,
                 Adaptation<?> adaptation);
}
