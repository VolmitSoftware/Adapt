package art.arcane.adapt.api.internal;

import java.util.Objects;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.content.ContentVeto;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.AddonInfo;
import art.arcane.adapt.api.addon.AddonState;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** The handle behind an installed addon. The engine reports the outcome of each apply through it. */
@ApiStatus.Internal
public final class InstalledAddon implements AddonHandle {
  private final InstalledAddons registry;
  private final AdaptAddon addon;
  private final String namespace;
  private volatile AddonState state = AddonState.WAITING;
  private volatile String rejection;

  InstalledAddon(InstalledAddons registry, AdaptAddon addon, String namespace) {
    this.registry = registry;
    this.addon = addon;
    this.namespace = namespace;
  }

  @Override
  public String namespace() {
    return namespace;
  }

  @Override
  public AdaptAddon addon() {
    return addon;
  }

  @Override
  public AddonState state() {
    return state;
  }

  @Override
  public Optional<String> rejection() {
    return Optional.ofNullable(rejection);
  }

  @Override
  public Path dataDirectory() {
    Path directory = Engine.bridge().platform().host().dataDirectory().resolve("addons").resolve(namespace);
    try {
      return Files.createDirectories(directory);
    } catch (IOException error) {
      throw new UncheckedIOException("Cannot create the data folder of addon " + namespace, error);
    }
  }

  @Override
  public ContentVeto veto(Id content) {
    return Engine.bridge().veto(Engine.key(), this, Objects.requireNonNull(content, "content"));
  }

  @Override
  public void refresh() {
    registry.refresh(this);
  }

  @Override
  public void uninstall() {
    registry.uninstall(this);
  }

  /** The engine registered the addon's content. */
  public void applied(HostKey key) {
    Engine.requireHost(key);
    if (state != AddonState.UNINSTALLED) {
      rejection = null;
      state = AddonState.ACTIVE;
    }
  }

  /** The engine refused the addon and registered nothing of it. */
  public void rejected(HostKey key, String reason) {
    Engine.requireHost(key);
    if (state != AddonState.UNINSTALLED) {
      rejection = reason;
      state = AddonState.REJECTED;
    }
  }

  /** The core stopped; the addon waits for the next one. */
  public void waiting(HostKey key) {
    Engine.requireHost(key);
    if (state != AddonState.UNINSTALLED) {
      rejection = null;
      state = AddonState.WAITING;
    }
  }

  /** What anyone may know about the addon now, detached from this handle. */
  AddonInfo info() {
    return new Info(namespace, state, Optional.ofNullable(rejection));
  }

  void removed() {
    rejection = null;
    state = AddonState.UNINSTALLED;
  }

  private record Info(String namespace, AddonState state, Optional<String> rejection) implements AddonInfo {
  }
}
