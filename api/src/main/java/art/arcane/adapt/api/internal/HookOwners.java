package art.arcane.adapt.api.internal;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** Checks the namespace a protector or ability hook is registered with. */
@ApiStatus.Internal
final class HookOwners {
  private HookOwners() {
  }

  static String require(String namespace) {
    Objects.requireNonNull(namespace, "namespace");
    return Id.of(namespace, "hook").namespace();
  }
}
