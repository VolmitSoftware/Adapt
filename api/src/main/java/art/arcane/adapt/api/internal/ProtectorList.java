package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.protection.ProtectionHooks;
import art.arcane.adapt.api.protection.Protector;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** The registered protectors. */
@ApiStatus.Internal
public final class ProtectorList implements ProtectionHooks {
  private volatile List<Protector> protectors = List.of();
  private final Map<Protector, String> owners = new ConcurrentHashMap<>();

  ProtectorList() {
  }

  @Override
  public synchronized void register(Protector protector) {
    String name = Objects.requireNonNull(Objects.requireNonNull(protector, "protector").name(), "protector name");
    for (Protector existing : protectors) {
      if (existing.name().equals(name)) {
        throw new IllegalArgumentException("A protector named '" + name + "' is already registered by "
            + existing.getClass().getName());
      }
    }
    List<Protector> extended = new ArrayList<>(protectors);
    extended.add(protector);
    protectors = List.copyOf(extended);
  }

  @Override
  public synchronized void register(String namespace, Protector protector) {
    String owner = HookOwners.require(namespace);
    register(protector);
    owners.put(protector, owner);
  }

  @Override
  public synchronized void unregister(Protector protector) {
    List<Protector> reduced = new ArrayList<>(protectors);
    if (reduced.remove(protector)) {
      protectors = List.copyOf(reduced);
    }
    if (protector != null) {
      owners.remove(protector);
    }
  }

  /** The namespace a protector was registered with, or null. */
  public String owner(HostKey key, Protector protector) {
    Engine.requireHost(key);
    return ownerOf(protector);
  }

  @Override
  public List<String> names() {
    List<String> names = new ArrayList<>();
    for (Protector protector : protectors) {
      names.add(protector.name());
    }
    return List.copyOf(names);
  }

  /** The registered protectors themselves, for the engine. */
  public List<Protector> protectors(HostKey key) {
    Engine.requireHost(key);
    return protectors;
  }

  List<Protector> all() {
    return protectors;
  }

  String ownerOf(Protector protector) {
    return protector == null ? null : owners.get(protector);
  }
}
