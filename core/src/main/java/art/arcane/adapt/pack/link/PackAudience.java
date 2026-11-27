package art.arcane.adapt.pack.link;

import art.arcane.adapt.pack.manifest.PackManifests;
import art.arcane.adapt.platform.entity.PlayerRef;

/**
 * Who sees book menus: viewers whose client loaded the Adapt pack and who did not choose chest menus, while the server
 * has the pack's manifest to draw with. The running server answers through {@link #DELIVERED}.
 */
@FunctionalInterface
public interface PackAudience {
  PackAudience NONE = (PlayerRef viewer) -> false;
  PackAudience DELIVERED = (PlayerRef viewer) -> PackManifests.installed().current().isPresent() && PackDelivery.loaded(viewer);

  boolean book(PlayerRef viewer);
}
