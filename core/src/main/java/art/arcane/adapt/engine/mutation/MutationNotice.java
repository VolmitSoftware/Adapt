package art.arcane.adapt.engine.mutation;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.notification.AdvancementNotification;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.MutationMessages;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.localization.LanguageAudience;

import java.util.Collection;

/** The one-time toast that tells a player their first mutation is available. */
public final class MutationNotice {
  private static final Id ICON = Id.minecraft("nether_star");

  private MutationNotice() {
  }

  /** Whether any two of the skills can be merged now; a player at the mutation limit is answered without walking them. */
  public static boolean available(PlayerData data, Collection<? extends Skill<?>> skills, AdaptConfig.Mutations config) {
    return config.allowsAnother(data.getMutations().size())
        && !MutationOffers.leads(data, skills, config).isEmpty();
  }

  /** Queues the toast and records it when a mutation is available and the player was never told; true when sent. */
  public static boolean sendIfAvailable(AdaptPlayer player, Collection<? extends Skill<?>> skills, AdaptConfig.Mutations config) {
    PlayerData data = player.getData();
    if (data.isMutationNoticeSent() || !available(data, skills, config)) {
      return false;
    }
    data.markMutationNoticeSent();
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.getPlayer() == null ? null : player.getPlayer().uuid())) {
      player.getNot().queue(AdvancementNotification.builder()
          .icon(ICON)
          .title(AdaptLanguage.text(MutationMessages.AVAILABLE))
          .description(AdaptLanguage.text(MutationMessages.AVAILABLE_QUIP))
          .kind(ToastKind.UNLOCK)
          .build());
    }
    return true;
  }
}
