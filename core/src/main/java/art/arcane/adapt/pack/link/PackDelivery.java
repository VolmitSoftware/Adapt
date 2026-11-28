package art.arcane.adapt.pack.link;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.client.server.ClientUiService;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.util.common.inventorygui.GuiFrame;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The running pack pipeline: takes the artifact from the installed {@link PackSource}, sends players to the released
 * copy while it is that pack (see {@link PackRelease}), else hosts it, exports it, offers it through the installed
 * {@link PackLink} by the {@code pack} config, and tracks who loaded it. With
 * {@code pack.merged} it only exports, and counts any server pack a client loaded. A platform calls {@link #start} once
 * the core runtime runs and {@link #stop} before it stops.
 */
public final class PackDelivery {
  /** Players holding it get the pack as optional while {@code pack.required} is on. */
  public static final String OPTIONAL_PERMISSION = "adapt.pack.optional";
  static final String UNHOSTED = "pack.serve is false and pack.externalUrl is blank, so the Adapt pack is not offered to "
      + "players. Set one of them, or set pack.merged when players get the pack inside another pack.";
  private static volatile PackDelivery current;

  private final AdaptCore core;
  private final AdaptConfig.Pack config;
  private final PackSource source;
  private final PackLink link;
  private final PackViewers viewers;
  private final PackOffers offers;
  private final PackRelease release;
  private final Runnable unsubscribe;
  private PackHost host;
  private PackArtifact published;

  private PackDelivery(AdaptCore core, AdaptConfig.Pack config, PackSource source, PackLink link,
                       Supplier<List<ClassLoader>> bedrockLoaders, PackRelease release) {
    this.core = core;
    this.config = config;
    this.source = source;
    this.link = link;
    this.release = config.getExternalUrl().isEmpty() ? release : null;
    BedrockPlayers bedrock = new BedrockPlayers(bedrockLoaders);
    this.viewers = config.isMerged() ? PackViewers.merged(bedrock) : new PackViewers();
    this.offers = new PackOffers(link, viewers, new Players(core, link, bedrock), PackOffers.Switches.CONFIG,
        this::later, System::currentTimeMillis, config.offerPolicy(), config.getHandshakeWindowMillis());
    link.listen(this::status);
    core.getRegistrar().register(this);
    unsubscribe = source.subscribe(this::refresh);
  }

  /**
   * Starts delivery when {@code pack.enabled} is on. {@code bedrockLoaders} are the class loaders that may see the
   * Floodgate or Geyser API; {@code release} is the released pack, or null when there is none to send players to.
   */
  public static synchronized void start(Supplier<List<ClassLoader>> bedrockLoaders, PackRelease release) {
    stop();
    AdaptConfig.Pack config = AdaptConfig.get().getPack();
    if (!config.isEnabled()) {
      return;
    }
    AdaptCore core = AdaptCore.get();
    PackDelivery delivery = new PackDelivery(core, config, PackSource.installed(), PackLink.installed(),
        Objects.requireNonNull(bedrockLoaders, "bedrockLoaders"), release);
    current = delivery;
    GuiFrame.setMenusOffer(PackInvite::element);
    if (config.isMerged()) {
      AdaptLog.info("pack.merged is on: the Adapt pack is not offered; players get book menus once their client loads a server pack.");
    } else if (unhosted(config) && release == null) {
      AdaptLog.warn(UNHOSTED);
    }
    if (config.isRequired() && (config.isMerged() || unhosted(config) && release == null)) {
      AdaptLog.warn("pack.required is on, but Adapt offers no pack of its own here, so no player is required to load it.");
    }
    delivery.refresh();
    for (PlayerRef player : core.getPlatform().host().onlinePlayers()) {
      delivery.offers.present(player.uuid());
    }
  }

  public static synchronized void stop() {
    PackDelivery running = current;
    current = null;
    GuiFrame.setMenusOffer(null);
    if (running != null) {
      running.close();
    }
  }

  /** The running delivery, or null while the pack is off or Adapt is stopped. */
  public static PackDelivery current() {
    return current;
  }

  /** True when the player should get book menus; false while delivery is off. */
  public static boolean loaded(PlayerRef player) {
    PackDelivery running = current;
    return running != null && running.viewers.loaded(player);
  }

  /** The player opened an Adapt menu, which is when the {@code first-open} policy offers the pack. */
  public static void opened(PlayerRef player) {
    PackDelivery running = current;
    if (running != null && player != null) {
      running.offers.opened(player.uuid());
    }
  }

  public PackViewers viewers() {
    return viewers;
  }

  /** Sends the pack to a player who asked for it. */
  public PackOffers.Result request(PlayerRef player) {
    return offers.requested(player.uuid());
  }

  /** Where the player stands with the pack, for the chest menu invitation; {@link PackOffers.Standing#NONE} while delivery is off. */
  public static PackOffers.Standing standing(PlayerRef player) {
    PackDelivery running = current;
    return running == null || player == null ? PackOffers.Standing.NONE : running.offers.standing(player.uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent event) {
    offers.joined(event.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    offers.quit(event.player().uuid());
  }

  /**
   * True when nothing would host the pack: the built-in listener is off, no external address is set and the pack is
   * not merged into another one.
   */
  static boolean unhosted(AdaptConfig.Pack config) {
    return !config.isMerged() && !config.isServe() && config.getExternalUrl().isEmpty();
  }

  /** Publishes the source's artifact. An unhosted pack was warned about once at {@link #start}. */
  private synchronized void refresh() {
    Optional<PackArtifact> next = source.current();
    if (next.isEmpty()) {
      published = null;
      offers.published(null, null);
      if (host != null) {
        host.withdraw();
      }
      return;
    }
    PackArtifact artifact = next.get();
    if (artifact.equals(published)) {
      return;
    }
    published = artifact;
    if (config.isExport()) {
      export(artifact);
    }
    if (config.isMerged()) {
      return;
    }
    String url = url(artifact);
    if (url != null && !link.available()) {
      AdaptLog.info("The Adapt pack is export-only on this server; it is not offered to players.");
    } else if (release != null && release.serves(artifact)) {
      AdaptLog.info("Players download the Adapt pack from " + url + ".");
    }
    offers.published(artifact, url);
  }

  /** One client report. A failed download from the release moves delivery to this server's own host. */
  private synchronized void status(UUID playerId, UUID packId, PackState state) {
    offers.status(playerId, packId, state);
    PackArtifact artifact = published;
    if (release == null || artifact == null || !artifact.id().equals(packId) || !release.report(artifact, state)) {
      return;
    }
    String url = url(artifact);
    AdaptLog.warn("A player could not download the Adapt pack from " + release.url() + "; "
        + (url == null ? "pack.serve is false, so the pack is not offered." : "this server hosts it from now on."));
    offers.published(artifact, url);
  }

  /** The release while it serves {@code artifact}; else the host, opened on first use and again after it failed to open. */
  private String url(PackArtifact artifact) {
    if (release != null && release.serves(artifact)) {
      return release.url();
    }
    if (host == null) {
      host = openHost();
    }
    return host == null ? null : host.publish(artifact);
  }

  private PackHost openHost() {
    if (!config.getExternalUrl().isEmpty()) {
      return PackHost.external(config.getExternalUrl());
    }
    if (!config.isServe()) {
      return null;
    }
    try {
      PackHost opened = PackHost.serve(config.getBind(), config.getPort(), config.getPublicUrl());
      AdaptLog.info("Serving the Adapt pack on " + config.getBind() + ":" + opened.port() + ".");
      return opened;
    } catch (IOException failure) {
      AdaptLog.warn("Could not serve the Adapt pack on " + config.getBind() + ":" + config.getPort() + ": "
          + failure.getMessage() + "; the pack is not offered.");
      return null;
    }
  }

  private static void export(PackArtifact artifact) {
    Path directory = AdaptEnvironment.dataDirectory().resolve("pack").resolve("export");
    try {
      PackExport.export(artifact, directory);
    } catch (IOException failure) {
      AdaptLog.warn("Could not export the Adapt pack to " + directory + ": " + failure.getMessage());
    }
  }

  private synchronized void close() {
    unsubscribe.run();
    link.listen(null);
    core.getRegistrar().unregister(this);
    if (host != null) {
      host.stop();
      host = null;
    }
  }

  private void later(UUID playerId, long millis, Runnable task) {
    PlayerRef player = core.getPlatform().host().player(playerId);
    if (player != null) {
      Tasks.runEntity(player, task, (int) Math.max(1L, (millis + 49L) / 50L));
    }
  }

  private static boolean negotiatedClient(UUID playerId) {
    ClientUiService service = ClientUiService.current();
    return service != null && service.capability(playerId).isPresent();
  }

  private static AdaptPlayer online(AdaptCore core, UUID playerId) {
    AdaptServer server = core.getAdaptServer();
    return server == null ? null : server.getOnlineAdaptPlayer(playerId);
  }

  /** The players offers talk to: their stored decline, their Adapt client, their bypass. */
  private record Players(AdaptCore core, PackLink link, BedrockPlayers bedrockPlayers) implements PackOffers.Players {
    @Override
    public boolean ready(UUID playerId) {
      return online(core, playerId) != null;
    }

    @Override
    public boolean clientMod(UUID playerId) {
      return negotiatedClient(playerId);
    }

    @Override
    public boolean bedrock(UUID playerId) {
      return bedrockPlayers.test(playerId);
    }

    @Override
    public boolean optional(UUID playerId) {
      AdaptPlayer player = online(core, playerId);
      return player == null || player.getPlayer().hasPermission(OPTIONAL_PERMISSION);
    }

    @Override
    public String prompt(UUID playerId) {
      return PackPitch.prompt(playerId);
    }

    @Override
    public String declined(UUID playerId) {
      AdaptPlayer player = online(core, playerId);
      return player == null ? null : player.getData().getDeclinedPack();
    }

    @Override
    public void declined(UUID playerId, String sha1Hex) {
      AdaptPlayer player = online(core, playerId);
      if (player != null && !Objects.equals(player.getData().getDeclinedPack(), sha1Hex)) {
        player.getData().setDeclinedPack(sha1Hex);
        player.requestSave();
      }
    }

    @Override
    public void invite(UUID playerId) {
      send(playerId, PackPitch.invite(playerId));
    }

    @Override
    public void steps(UUID playerId) {
      send(playerId, PackPitch.steps(playerId));
    }

    @Override
    public void kick(UUID playerId) {
      link.disconnect(playerId, PackPitch.required(playerId));
    }

    private void send(UUID playerId, String markup) {
      PlayerRef player = core.getPlatform().host().player(playerId);
      if (player != null) {
        player.sendMessage(markup);
      }
    }
  }
}
