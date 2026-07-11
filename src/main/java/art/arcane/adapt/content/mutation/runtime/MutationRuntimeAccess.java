package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.mutation.MutationConfig;
import art.arcane.adapt.api.mutation.MutationManager;
import art.arcane.adapt.api.mutation.MutationSnapshot;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.api.mutation.PlayerMutationData;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.service.MutationSVC;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class MutationRuntimeAccess {
  private static final Color PERFECT_ACCENT = Color.fromRGB(255, 226, 145);

  private final MutationProtectionAccess protection = new MutationProtectionAccess();

  MutationManager manager() {
    MutationSVC service = MutationSVC.get();
    return service == null ? null : service.getManager();
  }

  MutationConfig config() {
    MutationManager manager = manager();
    return manager == null ? MutationConfig.get() : manager.getConfig();
  }

  boolean enabled() {
    return config().isEnabled();
  }

  MutationSnapshot snapshot(Player player) {
    MutationManager manager = manager();
    return manager == null ? MutationSnapshot.empty() : manager.snapshot(player);
  }

  boolean expressed(Player player, MutationType type) {
    return eligible(player) && type != null && snapshot(player).expressed().contains(type);
  }

  boolean perfect(Player player) {
    return eligible(player) && snapshot(player).perfect();
  }

  List<MutationType> ordered(Player player) {
    if (!eligible(player)) {
      return List.of();
    }
    MutationSnapshot snapshot = snapshot(player);
    ArrayList<MutationType> ordered = new ArrayList<>(2);
    addExpressed(ordered, snapshot, MutationType.find(snapshot.slotOneId()));
    addExpressed(ordered, snapshot, MutationType.find(snapshot.slotTwoId()));
    return List.copyOf(ordered);
  }

  PlayerMutationData durable(Player player) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    return adaptPlayer == null ? null : adaptPlayer.getData().getMutationData();
  }

  void save(Player player) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    if (adaptPlayer != null) {
      adaptPlayer.saveNow();
    }
  }

  boolean consented(Player recipient) {
    if (!eligible(recipient)) {
      return false;
    }
    MutationConfig config = config();
    return cooperativeModeAllows(
        config.isEnabled() && config.isCooperativeEffectsEnabled(),
        config.getCooperativeConsentMode(),
        snapshot(recipient).cooperativeOptIn(),
        false
    );
  }

  CooperativeConsent cooperativeConsent(Player root) {
    MutationConfig config = config();
    MutationConfig.ConsentMode mode = config.getCooperativeConsentMode();
    PartyIdentity party = config.isEnabled() && mode == MutationConfig.ConsentMode.PARTY
        ? partyIdentity(root)
        : null;
    return new CooperativeConsent(config.isEnabled() && config.isCooperativeEffectsEnabled(), mode, party);
  }

  boolean consented(CooperativeConsent consent, Player recipient) {
    if (consent == null || !eligible(recipient)) {
      return false;
    }
    boolean optedIn = snapshot(recipient).cooperativeOptIn();
    boolean sameParty = consent.party() != null && consent.party().matches(recipient);
    return cooperativeModeAllows(consent.enabled(), consent.mode(), optedIn, sameParty);
  }

  boolean pvpEnabled(MutationType type) {
    MutationConfig config = config();
    return config.isEnabled()
        && config.isPvpEnabled()
        && type != null
        && config.profile(type).isPvpEnabled();
  }

  void tell(Player player, MutationType type, Particle particle, int count) {
    if (!enabled() || player == null || type == null || particle == null || count <= 0) {
      return;
    }
    FxEmitter emitter = Fx.now(type, player, FxPriority.TRANSITION)
        .burst(particle, Math.min(16, count), 0.45D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.28F, tellPitch(type));
    if (perfect(player)) {
      emitter.dustBurst(PERFECT_ACCENT, Math.min(4, Math.max(2, count / 3)), 0.32D, 0.75F);
    }
  }

  MutationProtectionAccess protection() {
    return protection;
  }

  Player onlinePlayer(UUID playerId) {
    if (playerId == null || Adapt.instance == null || Adapt.instance.getServer() == null) {
      return null;
    }
    return Adapt.instance.getServer().getPlayer(playerId);
  }

  static boolean cooperativeModeAllows(
      boolean enabled,
      MutationConfig.ConsentMode mode,
      boolean optedIn,
      boolean sameParty
  ) {
    if (!enabled || mode == null || !optedIn) {
      return false;
    }
    return switch (mode) {
      case EXPLICIT -> true;
      case PARTY -> sameParty;
      case FRIEND, DISABLED -> false;
    };
  }

  private AdaptPlayer adaptPlayer(Player player) {
    if (player == null || Adapt.instance == null || Adapt.instance.getAdaptServer() == null) {
      return null;
    }
    return Adapt.instance.getAdaptServer().getPlayer(player);
  }

  private boolean eligible(Player player) {
    return player != null
        && enabled()
        && player.isOnline()
        && MutationWorldRuntime.eligibleGameMode(player.getGameMode());
  }

  private PartyIdentity partyIdentity(Player player) {
    if (player == null || !player.isOnline()) {
      return null;
    }
    Scoreboard scoreboard = player.getScoreboard();
    Team team = scoreboard.getEntryTeam(player.getName());
    return team == null ? null : new PartyIdentity(scoreboard, team.getName());
  }

  private float tellPitch(MutationType type) {
    return 0.85F + ((type.ordinal() % 6) * 0.12F);
  }

  private void addExpressed(List<MutationType> ordered, MutationSnapshot snapshot, MutationType type) {
    if (type != null && snapshot.expressed().contains(type) && !ordered.contains(type)) {
      ordered.add(type);
    }
  }

  record CooperativeConsent(
      boolean enabled,
      MutationConfig.ConsentMode mode,
      PartyIdentity party
  ) {
  }

  private record PartyIdentity(Scoreboard scoreboard, String teamName) {
    boolean matches(Player recipient) {
      Scoreboard recipientScoreboard = recipient.getScoreboard();
      if (recipientScoreboard != scoreboard) {
        return false;
      }
      Team recipientTeam = recipientScoreboard.getEntryTeam(recipient.getName());
      return recipientTeam != null && teamName.equals(recipientTeam.getName());
    }
  }
}
