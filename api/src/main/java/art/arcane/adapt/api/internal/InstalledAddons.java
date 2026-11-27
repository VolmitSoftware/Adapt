package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.AddonInfo;
import art.arcane.adapt.api.addon.AddonRegistry;
import art.arcane.adapt.api.addon.AddonState;
import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The installed addons. Lives in the API jar so installs made before a core exists are kept for every core. */
@ApiStatus.Internal
public final class InstalledAddons implements AddonRegistry {
  private final Map<String, InstalledAddon> byNamespace = new LinkedHashMap<>();

  InstalledAddons() {
  }

  @Override
  public AddonHandle install(AdaptAddon addon) {
    return install(addon, false);
  }

  /** Installs Adapt's own content under the reserved {@code adapt} namespace. */
  public AddonHandle installBuiltin(HostKey key, AdaptAddon addon) {
    Engine.requireHost(key);
    return install(addon, true);
  }

  @Override
  public synchronized Optional<AddonInfo> find(String namespace) {
    InstalledAddon addon = byNamespace.get(namespace);
    return addon == null ? Optional.empty() : Optional.of(addon.info());
  }

  @Override
  public List<AddonInfo> installed() {
    List<AddonInfo> infos = new ArrayList<>();
    for (InstalledAddon addon : ordered()) {
      infos.add(addon.info());
    }
    return List.copyOf(infos);
  }

  /** The handle of the addon with this namespace. */
  public synchronized Optional<AddonHandle> handle(HostKey key, String namespace) {
    Engine.requireHost(key);
    return Optional.ofNullable(byNamespace.get(namespace));
  }

  /** Installed addons in apply order: {@code adapt} first, then install order. */
  public List<InstalledAddon> snapshot(HostKey key) {
    Engine.requireHost(key);
    return ordered();
  }

  private synchronized List<InstalledAddon> ordered() {
    List<InstalledAddon> ordered = new ArrayList<>(byNamespace.size());
    InstalledAddon builtin = byNamespace.get(Id.ADAPT);
    if (builtin != null) {
      ordered.add(builtin);
    }
    for (InstalledAddon addon : byNamespace.values()) {
      if (addon != builtin) {
        ordered.add(addon);
      }
    }
    return ordered;
  }

  void refresh(InstalledAddon addon) {
    EngineBridge bridge = Engine.current();
    if (bridge != null && addon.state() != AddonState.UNINSTALLED) {
      bridge.addonRefreshed(Engine.key(), addon);
    }
  }

  void uninstall(InstalledAddon addon) {
    synchronized (this) {
      if (!byNamespace.remove(addon.namespace(), addon)) {
        return;
      }
    }
    EngineBridge bridge = Engine.current();
    if (bridge != null) {
      bridge.addonUninstalled(Engine.key(), addon);
    }
    dropHooks(addon.addon());
    Engine.services().withdrawAll(addon.namespace());
    addon.removed();
  }

  /**
   * Removes the protectors and ability hooks registered with the addon's namespace, and those whose classes the
   * addon's own class loader defined. Runs on uninstall with or without a core, since the hook lists outlive cores.
   * A refresh keeps them: they are registered outside {@code register}, which is all a refresh runs again.
   */
  private static void dropHooks(AdaptAddon addon) {
    String namespace = addon.namespace();
    ClassLoader loader = addon.getClass().getClassLoader();
    boolean ownLoader = loader != null && loader != InstalledAddons.class.getClassLoader();
    ProtectorList protectors = Engine.protectors();
    for (Protector protector : protectors.all()) {
      if (namespace.equals(protectors.ownerOf(protector)) || ownLoader && definedBy(protector, loader)) {
        protectors.unregister(protector);
      }
    }
    AbilityHookList abilities = Engine.abilities();
    for (AbilityUsePolicy policy : abilities.allPolicies()) {
      if (namespace.equals(abilities.ownerOf(policy)) || ownLoader && definedBy(policy, loader)) {
        abilities.unregisterPolicy(policy);
      }
    }
    for (AbilityCostProvider provider : abilities.allCostProviders()) {
      if (namespace.equals(abilities.ownerOf(provider)) || ownLoader && definedBy(provider, loader)) {
        abilities.unregisterCostProvider(provider);
      }
    }
  }

  private static boolean definedBy(Object hook, ClassLoader loader) {
    return hook.getClass().getClassLoader() == loader;
  }

  private AddonHandle install(AdaptAddon addon, boolean builtin) {
    Objects.requireNonNull(addon, "addon");
    String namespace = Objects.requireNonNull(addon.namespace(), "addon namespace");
    if (!ContentIds.validNamespace(namespace)) {
      throw new IllegalArgumentException("Invalid addon namespace '" + namespace + "' from " + describe(addon));
    }
    if (ContentIds.isReserved(namespace) && !(builtin && Id.ADAPT.equals(namespace))) {
      throw new IllegalArgumentException("Namespace '" + namespace + "' is reserved; " + describe(addon)
          + " must use its own");
    }
    InstalledAddon installed = new InstalledAddon(this, addon, namespace);
    synchronized (this) {
      InstalledAddon holder = byNamespace.get(namespace);
      if (holder != null) {
        throw new IllegalArgumentException("Namespace '" + namespace + "' is already installed by "
            + describe(holder.addon()) + "; " + describe(addon) + " cannot take it");
      }
      byNamespace.put(namespace, installed);
    }
    EngineBridge bridge = Engine.current();
    if (bridge != null) {
      bridge.addonInstalled(Engine.key(), installed);
    }
    return installed;
  }

  private static String describe(AdaptAddon addon) {
    return addon.getClass().getName();
  }
}
