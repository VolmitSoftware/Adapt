package art.arcane.adapt.claims;

import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimChunk;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimException;
import art.arcane.adapt.api.claims.ClaimInvitation;
import art.arcane.adapt.api.claims.ClaimManager;
import art.arcane.adapt.api.claims.ClaimPlot;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimSnapshot;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ClaimsMessages;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public final class ClaimInteractionListener implements Listener {
  private static final long CONFIRMATION_MILLIS = 30_000L;
  private static final int BORDER_INTERVAL_TICKS = 10;
  private static final int BORDER_DURATION_TICKS = 100;
  private static final Particle.DustOptions CITY_BORDER = new Particle.DustOptions(Color.fromRGB(255, 190, 55), 1.2F);
  private static final Particle.DustOptions PLOT_BORDER = new Particle.DustOptions(Color.fromRGB(65, 180, 255), 1.2F);

  private final ClaimsService service;
  private final Map<UUID, UUID> selectedCities = new ConcurrentHashMap<>();
  private final Map<UUID, Confirmation> confirmations = new ConcurrentHashMap<>();
  private final Map<UUID, BoundaryView> boundaryViews = new ConcurrentHashMap<>();
  private volatile boolean closed;

  public ClaimInteractionListener(ClaimsService service) {
    this.service = service;
  }

  public void close() {
    closed = true;
    selectedCities.clear();
    confirmations.clear();
    boundaryViews.clear();
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (!service.enabled() || event.getHand() != EquipmentSlot.HAND
        || event.getAction() != Action.RIGHT_CLICK_BLOCK || !isShovel(event.getItem())) {
      return;
    }
    Player player = event.getPlayer();
    Block block = event.getClickedBlock();
    if (block == null || !player.hasPermission("adapt.claims")) {
      return;
    }
    if (block.getType() != Material.LODESTONE && !selectedCities.containsKey(player.getUniqueId())) {
      return;
    }
    event.setUseInteractedBlock(Event.Result.DENY);
    event.setUseItemInHand(Event.Result.DENY);
    perform(player, () -> interact(player, block));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onInteractEntity(PlayerInteractEntityEvent event) {
    if (!service.enabled() || event.getHand() != EquipmentSlot.HAND
        || !(event.getRightClicked() instanceof Player target)
        || !isShovel(event.getPlayer().getInventory().getItemInMainHand())) {
      return;
    }
    Player player = event.getPlayer();
    if (!player.hasPermission("adapt.claims")) {
      return;
    }
    event.setCancelled(true);
    if (!selectedCities.containsKey(player.getUniqueId())) {
      error(player, ClaimsMessages.SELECT_ANCHOR);
      return;
    }
    invite(player, target);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    UUID player = event.getPlayer().getUniqueId();
    selectedCities.remove(player);
    confirmations.remove(player);
    boundaryViews.remove(player);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onItemHeld(PlayerItemHeldEvent event) {
    if (!isShovel(event.getPlayer().getInventory().getItem(event.getNewSlot()))) {
      selectedCities.remove(event.getPlayer().getUniqueId());
    }
  }

  public void info(Player player) {
    ClaimSnapshot snapshot = service.manager().snapshot();
    ClaimCity city = snapshot.cityAt(ClaimsService.chunk(player.getLocation()))
        .or(() -> snapshot.cityOf(player.getUniqueId())).orElse(null);
    if (city == null) {
      error(player, ClaimsMessages.NO_CLAIM);
      return;
    }
    report(player, city);
    snapshot.plotAt(ClaimsService.chunk(player.getLocation())).ifPresent(plot -> reportPlot(player, plot));
  }

  public void inspect(CommandSender sender, UUID id) {
    ClaimSnapshot snapshot = service.manager().snapshot();
    ClaimCity city = snapshot.cities().get(id);
    ClaimPlot plot = snapshot.plots().get(id);
    if (city == null && plot != null) {
      city = snapshot.cities().get(plot.cityId());
    }
    if (city == null) {
      error(sender, ClaimsMessages.NO_CLAIM);
      return;
    }
    report(sender, city);
    if (plot != null) {
      reportPlot(sender, plot);
    }
  }

  public void boundaries(Player player) {
    UUID playerId = player.getUniqueId();
    boundaryViews.remove(playerId);
    if (closed || !service.enabled() || !player.isOnline()) {
      return;
    }
    UUID world = player.getWorld().getUID();
    ClaimSnapshot snapshot = service.manager().snapshot();
    ClaimCity city = snapshot.cityAt(ClaimsService.chunk(player.getLocation()))
        .or(() -> snapshot.cityOf(player.getUniqueId())).orElse(null);
    if (city == null || !city.anchor().worldId().equals(world)) {
      error(player, ClaimsMessages.NO_CLAIM);
      return;
    }
    BoundaryView view = new BoundaryView(city.id(), world);
    boundaryViews.put(playerId, view);
    refreshBoundaries(player, view, 0);
    success(player, ClaimsMessages.BOUNDARIES);
  }

  public void invite(Player founder, Player target) {
    perform(founder, () -> {
      if (!mayUse(founder, "claims-citizenship")) {
        return;
      }
      service.refreshRanks(founder);
      ClaimCity city = service.manager().snapshot().cityOf(founder.getUniqueId())
          .orElseThrow(() -> new ClaimException(ClaimException.Reason.CITY_NOT_FOUND));
      service.manager().invite(founder.getUniqueId(), target.getUniqueId(), System.currentTimeMillis());
      success(founder, ClaimsMessages.INVITED, untrusted("player", target.getName()), untrusted("city", city.name()));
      success(target, ClaimsMessages.INVITATION, untrusted("player", founder.getName()), untrusted("city", city.name()));
    });
  }

  public void accept(Player player) {
    perform(player, () -> {
      ClaimInvitation invitation = service.manager().invitation(player.getUniqueId())
          .orElseThrow(() -> new ClaimException(ClaimException.Reason.INVITATION_MISSING));
      ClaimCity city = service.manager().snapshot().cities().get(invitation.cityId());
      if (city == null) {
        throw new ClaimException(ClaimException.Reason.CITY_NOT_FOUND);
      }
      service.manager().accept(player.getUniqueId(), invitation.founder(), System.currentTimeMillis());
      success(player, ClaimsMessages.ACCEPTED, untrusted("city", city.name()));
    });
  }

  public void members(Player player) {
    ClaimCity city = service.manager().snapshot().cityOf(player.getUniqueId()).orElse(null);
    if (city == null) {
      error(player, ClaimsMessages.NO_CLAIM);
      return;
    }
    List<String> names = new ArrayList<>(city.members().size() + 1);
    names.add(playerName(city.founder()));
    for (UUID member : city.members()) {
      names.add(playerName(member));
    }
    success(player, ClaimsMessages.MEMBERS, untrusted("players", String.join(", ", names)));
  }

  public void kick(Player founder, OfflinePlayer target) {
    perform(founder, () -> {
      if (target == null) {
        throw new ClaimException(ClaimException.Reason.NOT_MEMBER);
      }
      service.manager().removeMember(founder.getUniqueId(), target.getUniqueId());
      success(founder, ClaimsMessages.MEMBER_REMOVED, untrusted("player", playerName(target.getUniqueId())));
    });
  }

  public void leave(Player player) {
    perform(player, () -> {
      service.manager().leave(player.getUniqueId());
      selectedCities.remove(player.getUniqueId());
      success(player, ClaimsMessages.LEFT);
    });
  }

  public void dismantle(Player player, boolean confirm) {
    if (!confirm) {
      requestDismantle(player);
      return;
    }
    Confirmation pending = confirmations.remove(player.getUniqueId());
    if (pending == null || pending.expiresAt() <= System.currentTimeMillis()) {
      error(player, ClaimsMessages.CONFIRMATION_REQUIRED);
      return;
    }
    perform(player, () -> releaseOwnedAnchor(player, pending));
  }

  public void remove(CommandSender sender, String kind, UUID id, boolean confirm) {
    if (!"city".equals(kind) && !"plot".equals(kind)) {
      error(sender, ClaimsMessages.INVALID_KIND);
      return;
    }
    ClaimSnapshot snapshot = service.manager().snapshot();
    boolean exists = "city".equals(kind) ? snapshot.cities().containsKey(id) : snapshot.plots().containsKey(id);
    if (!exists) {
      error(sender, ClaimsMessages.NO_CLAIM);
      return;
    }
    if (!confirm) {
      success(sender, ClaimsMessages.REMOVE_PROMPT, trusted("kind", kind), trusted("id", id));
      return;
    }
    perform(sender, () -> {
      if ("city".equals(kind)) {
        service.manager().removeCity(id);
      } else {
        service.manager().removePlot(id);
      }
      success(sender, ClaimsMessages.DISMANTLED, trusted("target", id));
    });
  }

  private void interact(Player player, Block block) {
    ClaimRanks ranks = service.refreshRanks(player);
    ClaimBlock position = ClaimsService.block(block);
    ClaimSnapshot snapshot = service.manager().snapshot();
    ClaimCity here = snapshot.cityAt(position.chunk()).orElse(null);
    if (block.getType() == Material.LODESTONE) {
      if (here != null && here.anchor().equals(position)) {
        if (here.founder().equals(player.getUniqueId())) {
          selectedCities.put(player.getUniqueId(), here.id());
          success(player, ClaimsMessages.CITY_SELECTED, untrusted("city", here.name()));
        } else {
          report(player, here);
        }
        return;
      }
      ClaimPlot plot = snapshot.plotAt(position.chunk()).orElse(null);
      if (plot != null && plot.anchor().equals(position)) {
        if (plot.owner().equals(player.getUniqueId())) {
          success(player, ClaimsMessages.PLOT_SELECTED);
        } else {
          reportPlot(player, plot);
        }
        return;
      }
      ClaimCity membership = snapshot.cityOf(player.getUniqueId()).orElse(null);
      if (membership != null && here != null) {
        if (!mayUse(player, "claims-homestead")) {
          return;
        }
        service.manager().createPlot(player.getUniqueId(), position, ranks);
        success(player, ClaimsMessages.PLOT_CREATED);
        return;
      }
      if (membership == null) {
        if (!mayUse(player, "claims-founding")) {
          return;
        }
        ClaimCity city = service.manager().found(player.getUniqueId(), player.getName(), position, ranks, ClaimsService.world(block.getWorld()));
        selectedCities.put(player.getUniqueId(), city.id());
        success(player, ClaimsMessages.CITY_FOUNDED, untrusted("city", city.name()), trusted("count", city.chunks().size()));
        return;
      }
    }
    UUID selected = selectedCities.get(player.getUniqueId());
    ClaimCity city = selected == null ? null : snapshot.cities().get(selected);
    if (city == null || !city.founder().equals(player.getUniqueId())) {
      error(player, ClaimsMessages.SELECT_ANCHOR);
      return;
    }
    if (!mayUse(player, "claims-expansion")) {
      return;
    }
    ClaimCity expanded = service.manager().expand(player.getUniqueId(), position.chunk(), ClaimsService.world(block.getWorld()));
    success(player, ClaimsMessages.CITY_EXPANDED, untrusted("city", expanded.name()),
        trusted("x", position.chunk().x()), trusted("z", position.chunk().z()),
        trusted("count", expanded.chunks().size()),
        trusted("capacity", service.config().chunkCapacity(service.manager().effectiveRanks(expanded.ranks()))));
  }

  private void requestDismantle(Player player) {
    confirmations.remove(player.getUniqueId());
    if (!J.isOwnedByCurrentRegion(player.getLocation(), 7, 7)) {
      error(player, ClaimsMessages.NOT_ANCHOR_OWNER);
      return;
    }
    Block target = player.getTargetBlockExact(6);
    if (target == null || target.getType() != Material.LODESTONE) {
      error(player, ClaimsMessages.NOT_ANCHOR_OWNER);
      return;
    }
    ClaimBlock block = ClaimsService.block(target);
    ClaimSnapshot snapshot = service.manager().snapshot();
    ClaimPlot plot = snapshot.plots().get(player.getUniqueId());
    if (plot != null && plot.anchor().equals(block)) {
      confirmations.put(player.getUniqueId(), new Confirmation(plot.cityId(), plot, block, System.currentTimeMillis() + CONFIRMATION_MILLIS));
      success(player, ClaimsMessages.DISMANTLE_PROMPT, trusted("target", plot.owner()));
      return;
    }
    ClaimCity city = snapshot.cityAt(block.chunk()).orElse(null);
    if (city == null || !city.founder().equals(player.getUniqueId()) || !city.anchor().equals(block)) {
      error(player, ClaimsMessages.NOT_ANCHOR_OWNER);
      return;
    }
    confirmations.put(player.getUniqueId(), new Confirmation(city.id(), null, block, System.currentTimeMillis() + CONFIRMATION_MILLIS));
    success(player, ClaimsMessages.DISMANTLE_PROMPT, untrusted("target", city.name()));
  }

  private void releaseOwnedAnchor(Player player, Confirmation pending) {
    ClaimManager manager = service.manager();
    synchronized (manager) {
      ClaimSnapshot snapshot = manager.snapshot();
      if (pending.plot() != null) {
        ClaimPlot plot = snapshot.plots().get(pending.plot().owner());
        if (plot != pending.plot() || !plot.owner().equals(player.getUniqueId())) {
          error(player, ClaimsMessages.NOT_ANCHOR_OWNER);
          return;
        }
        manager.removePlot(plot.owner());
        success(player, ClaimsMessages.DISMANTLED, trusted("target", plot.owner()));
        return;
      }
      ClaimCity city = snapshot.cities().get(pending.city());
      if (city == null || !city.founder().equals(player.getUniqueId()) || !city.anchor().equals(pending.anchor())) {
        error(player, ClaimsMessages.NOT_ANCHOR_OWNER);
        return;
      }
      manager.removeCity(city.id());
      selectedCities.remove(player.getUniqueId());
      success(player, ClaimsMessages.DISMANTLED, untrusted("target", city.name()));
    }
  }

  private void report(CommandSender sender, ClaimCity city) {
    ClaimRanks ranks = service.manager().effectiveRanks(city.ranks());
    success(sender, ClaimsMessages.INFO, untrusted("city", city.name()), trusted("id", city.id()),
        untrusted("founder", playerName(city.founder())), trusted("members", city.members().size()),
        trusted("memberCapacity", service.config().memberCapacity(ranks)), trusted("chunks", city.chunks().size()),
        trusted("chunkCapacity", service.config().chunkCapacity(ranks)), trusted("rank", Math.max(1, ranks.fortification())));
    World world = Bukkit.getWorld(city.anchor().worldId());
    success(sender, ClaimsMessages.ANCHOR_LOCATION,
        untrusted("world", world == null ? city.anchor().worldId().toString() : world.getName()),
        trusted("x", city.anchor().x()), trusted("y", city.anchor().y()), trusted("z", city.anchor().z()));
  }

  private static void reportPlot(CommandSender sender, ClaimPlot plot) {
    success(sender, ClaimsMessages.PLOT_INFO, untrusted("owner", playerName(plot.owner())),
        trusted("x", plot.anchor().x()), trusted("y", plot.anchor().y()), trusted("z", plot.anchor().z()));
  }

  private void refreshBoundaries(Player player, BoundaryView view, int elapsedTicks) {
    UUID playerId = player.getUniqueId();
    if (boundaryViews.get(playerId) != view) {
      return;
    }
    if (elapsedTicks >= BORDER_DURATION_TICKS || closed || !service.enabled() || !player.isOnline()
        || !player.getWorld().getUID().equals(view.world)) {
      boundaryViews.remove(playerId, view);
      return;
    }
    ClaimSnapshot snapshot = service.manager().snapshot();
    ClaimCity city = snapshot.cities().get(view.city);
    if (city == null) {
      boundaryViews.remove(playerId, view);
      return;
    }
    drawBorders(player, city.chunks(), CITY_BORDER);
    for (ClaimPlot plot : snapshot.plots().values()) {
      if (plot.cityId().equals(city.id())) {
        drawBorders(player, plot.chunks(), PLOT_BORDER);
      }
    }
    if (boundaryViews.get(playerId) == view && !J.runEntity(player,
        () -> refreshBoundaries(player, view, elapsedTicks + BORDER_INTERVAL_TICKS), BORDER_INTERVAL_TICKS,
        () -> boundaryViews.remove(playerId, view))) {
      boundaryViews.remove(playerId, view);
    }
  }

  private static void drawBorders(Player player, Set<ClaimChunk> chunks, Particle.DustOptions dust) {
    Location viewer = player.getLocation();
    UUID world = viewer.getWorld().getUID();
    double y = viewer.getY() + 1.5D;
    for (ClaimChunk chunk : chunks) {
      double x = (double) chunk.x() * 16;
      double z = (double) chunk.z() * 16;
      if (!chunk.worldId().equals(world) || Math.abs(x + 8 - viewer.getX()) > 64 || Math.abs(z + 8 - viewer.getZ()) > 64) {
        continue;
      }
      if (!chunks.contains(new ClaimChunk(world, chunk.x() - 1, chunk.z()))) {
        borderLine(player, x, z, x, z + 16, y, dust);
      }
      if (!chunks.contains(new ClaimChunk(world, chunk.x() + 1, chunk.z()))) {
        borderLine(player, x + 16, z, x + 16, z + 16, y, dust);
      }
      if (!chunks.contains(new ClaimChunk(world, chunk.x(), chunk.z() - 1))) {
        borderLine(player, x, z, x + 16, z, y, dust);
      }
      if (!chunks.contains(new ClaimChunk(world, chunk.x(), chunk.z() + 1))) {
        borderLine(player, x, z + 16, x + 16, z + 16, y, dust);
      }
    }
  }

  private static void borderLine(Player player, double x1, double z1, double x2, double z2, double y, Particle.DustOptions dust) {
    for (int point = 0; point <= 16; point += 2) {
      double fraction = point / 16D;
      player.spawnParticle(Particle.DUST, x1 + (x2 - x1) * fraction, y, z1 + (z2 - z1) * fraction, 1, 0, 0, 0, 0, dust);
    }
  }

  private static boolean isShovel(ItemStack item) {
    return item != null && item.getType().name().endsWith("_SHOVEL");
  }

  private static String playerName(UUID id) {
    Player player = Bukkit.getPlayer(id);
    return player == null ? id.toString() : player.getName();
  }

  private boolean mayUse(Player player, String adaptation) {
    Optional<String> denied = service.deniedUsePermission(player, adaptation);
    if (denied.isEmpty()) {
      return true;
    }
    error(player, CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", denied.get()));
    return false;
  }

  private static void perform(CommandSender sender, Runnable action) {
    try {
      action.run();
    } catch (ClaimException failure) {
      error(sender, ClaimsMessages.error(failure.reason()));
    }
  }

  private static void success(CommandSender sender, TextKey message, MessageArgument... arguments) {
    send(sender, message, false, arguments);
  }

  private static void error(CommandSender sender, TextKey message, MessageArgument... arguments) {
    send(sender, message, true, arguments);
  }

  private static void send(CommandSender sender, TextKey message, boolean error, MessageArgument... arguments) {
    UUID audience = sender instanceof Player player ? player.getUniqueId() : null;
    Runnable action = () -> LanguageAudience.run(audience, () -> {
      String text = AdaptLanguage.text(message, arguments);
      if (error) {
        FConst.error(text).send(sender);
      } else {
        FConst.success(text).send(sender);
      }
    });
    if (sender instanceof Player player && !J.isOwnedByCurrentRegion(player)) {
      J.runEntity(player, action);
    } else {
      action.run();
    }
  }

  private record Confirmation(UUID city, ClaimPlot plot, ClaimBlock anchor, long expiresAt) {
  }

  private static final class BoundaryView {
    private final UUID city;
    private final UUID world;

    private BoundaryView(UUID city, UUID world) {
      this.city = city;
      this.world = world;
    }
  }
}
