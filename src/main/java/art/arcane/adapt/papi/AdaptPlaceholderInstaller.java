package art.arcane.adapt.papi;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration;

import java.util.logging.Logger;

public final class AdaptPlaceholderInstaller {
  private AdaptPlaceholderInstaller() {
  }

  public static void install(PlaceholderRegistration registration, AdaptPlaceholders placeholders, Logger logger) {
    registration.register(() -> new AdaptPapiExpansion(placeholders.players(), placeholders.catalog(), logger));
  }
}
