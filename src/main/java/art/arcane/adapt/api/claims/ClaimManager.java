package art.arcane.adapt.api.claims;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClaimManager implements AutoCloseable {
  private final ClaimRepository repository;
  private final Map<ClaimBlock, ClaimProgress> progress = new HashMap<>();
  private final Map<UUID, ClaimInvitation> invitations = new HashMap<>();
  private final Map<UUID, Deque<Long>> placementTimes = new HashMap<>();
  private volatile ClaimSnapshot snapshot;
  private volatile ClaimsConfig config;
  private volatile ClaimRanks rankLimits = new ClaimRanks(true, 4, 4, 4, true);
  private volatile Set<UUID> connectedPlayers = Set.of();
  private boolean closed;

  public ClaimManager(ClaimsConfig config, ClaimRepository repository) throws IOException {
    this.repository = Objects.requireNonNull(repository);
    reconfigure(config);
    ClaimState loaded = repository.load();
    Map<UUID, ClaimCity> cities = new HashMap<>();
    Map<UUID, ClaimPlot> plots = new HashMap<>();
    long now = System.currentTimeMillis();
    try {
      for (ClaimCity city : loaded.cities()) {
        ClaimCity recovered = city.offlineSince() == 0 ? withOfflineSince(city, now) : city;
        if (cities.putIfAbsent(city.id(), recovered) != null) {
          throw new IllegalArgumentException("Duplicate city identifier");
        }
      }
      for (ClaimPlot plot : loaded.plots()) {
        if (plots.putIfAbsent(plot.owner(), plot) != null) {
          throw new IllegalArgumentException("Duplicate personal plot owner");
        }
      }
      snapshot = new ClaimSnapshot(cities, plots);
      for (ClaimProgress entry : loaded.progress()) {
        if (snapshot.cityAt(entry.block().chunk()).isEmpty()
            || progress.putIfAbsent(entry.block(), entry) != null) {
          throw new IllegalArgumentException("Invalid persisted raid position");
        }
      }
    } catch (RuntimeException exception) {
      throw new IOException("Invalid Claims ownership data", exception);
    }
    if (!cities.isEmpty()) {
      save();
    }
  }

  public synchronized void reconfigure(ClaimsConfig replacement) {
    config = Objects.requireNonNull(replacement).validatedCopy();
  }

  public ClaimSnapshot snapshot() {
    return snapshot;
  }

  public ClaimsConfig config() {
    return config.validatedCopy();
  }

  public void setRankLimits(ClaimRanks limits) {
    rankLimits = Objects.requireNonNull(limits);
  }

  public ClaimRanks effectiveRanks(ClaimRanks purchased) {
    ClaimRanks limits = rankLimits;
    return new ClaimRanks(purchased.founding() && limits.founding(),
        Math.min(purchased.fortification(), limits.fortification()),
        Math.min(purchased.citizenship(), limits.citizenship()),
        Math.min(purchased.expansion(), limits.expansion()),
        purchased.homestead() && limits.homestead());
  }

  public synchronized ClaimCity found(UUID founder, String name, ClaimBlock anchor, ClaimRanks ranks, ClaimWorldAccess world) {
    ensureOpen();
    Objects.requireNonNull(founder);
    Objects.requireNonNull(anchor);
    ClaimRanks effective = effectiveRanks(ranks);
    if (!effective.founding()) {
      throw failure(ClaimException.Reason.FOUNDING_REQUIRED);
    }
    if (effective.fortification() == 0) {
      throw failure(ClaimException.Reason.FORTIFICATION_REQUIRED);
    }
    if (snapshot.cityOf(founder).isPresent()) {
      throw failure(ClaimException.Reason.ALREADY_MEMBER);
    }
    String cityName = name == null ? "" : name.strip();
    if (cityName.isEmpty() || cityName.length() > 48) {
      throw failure(ClaimException.Reason.INVALID_NAME);
    }
    Set<ClaimChunk> footprint = ClaimSnapshot.footprint(anchor.chunk());
    for (ClaimChunk chunk : footprint) {
      validateNewChunk(chunk, world);
    }
    ClaimCity city = new ClaimCity(UUID.randomUUID(), cityName, founder, anchor, Set.of(), footprint, ranks,
        connectedPlayers.contains(founder) ? 0 : System.currentTimeMillis());
    replaceCity(city);
    return city;
  }

  public synchronized ClaimCity expand(UUID founder, ClaimChunk chunk, ClaimWorldAccess world) {
    ensureOpen();
    ClaimCity city = requireFounder(founder);
    if (city.chunks().size() >= config.chunkCapacity(effectiveRanks(city.ranks()))) {
      throw failure(ClaimException.Reason.CAPACITY_REACHED);
    }
    validateNewChunk(chunk, world);
    boolean adjacent = false;
    for (ClaimChunk owned : city.chunks()) {
      if (owned.sharesEdge(chunk)) {
        adjacent = true;
        break;
      }
    }
    if (!adjacent) {
      throw failure(ClaimException.Reason.NOT_ADJACENT);
    }
    Set<ClaimChunk> chunks = new HashSet<>(city.chunks());
    chunks.add(chunk);
    ClaimCity expanded = new ClaimCity(city.id(), city.name(), city.founder(), city.anchor(), city.members(),
        chunks, city.ranks(), city.offlineSince());
    replaceCity(expanded);
    return expanded;
  }

  public synchronized ClaimPlot createPlot(UUID owner, ClaimBlock anchor, ClaimRanks ranks) {
    ensureOpen();
    if (!effectiveRanks(ranks).homestead()) {
      throw failure(ClaimException.Reason.HOMESTEAD_REQUIRED);
    }
    ClaimCity city = requireMember(owner);
    if (snapshot.plots().containsKey(owner)) {
      throw failure(ClaimException.Reason.ALREADY_HAS_PLOT);
    }
    Set<ClaimChunk> chunks = ClaimSnapshot.footprint(anchor.chunk());
    if (!city.chunks().containsAll(chunks)) {
      throw failure(ClaimException.Reason.PLOT_OUTSIDE_CITY);
    }
    if (chunks.contains(city.anchor().chunk())) {
      throw failure(ClaimException.Reason.MAIN_ANCHOR_OVERLAP);
    }
    for (ClaimChunk chunk : chunks) {
      if (snapshot.plotAt(chunk).isPresent()) {
        throw failure(ClaimException.Reason.PLOT_OVERLAP);
      }
    }
    ClaimPlot plot = new ClaimPlot(owner, city.id(), anchor, chunks);
    Map<UUID, ClaimPlot> plots = new HashMap<>(snapshot.plots());
    plots.put(owner, plot);
    publish(snapshot.cities(), plots);
    return plot;
  }

  public synchronized void invite(UUID founder, UUID target, long now) {
    ensureOpen();
    ClaimCity city = requireFounder(founder);
    validateJoin(city, target);
    invitations.put(target, new ClaimInvitation(founder, target, city.id(), Math.addExact(now, config.invitationDurationMillis)));
  }

  public synchronized Optional<ClaimInvitation> invitation(UUID target) {
    return Optional.ofNullable(invitations.get(target));
  }

  public synchronized void accept(UUID target, UUID founder, long now) {
    ensureOpen();
    ClaimInvitation invitation = invitations.get(target);
    if (invitation == null || !invitation.founder().equals(founder)) {
      throw failure(ClaimException.Reason.INVITATION_MISSING);
    }
    if (now >= invitation.expiresAt()) {
      invitations.remove(target);
      throw failure(ClaimException.Reason.INVITATION_EXPIRED);
    }
    ClaimCity city = snapshot.cities().get(invitation.cityId());
    if (city == null) {
      invitations.remove(target);
      throw failure(ClaimException.Reason.CITY_NOT_FOUND);
    }
    validateJoin(city, target);
    Set<UUID> members = new HashSet<>(city.members());
    members.add(target);
    ClaimCity joined = new ClaimCity(city.id(), city.name(), city.founder(), city.anchor(), members,
        city.chunks(), city.ranks(), connectedPlayers.contains(target) ? 0 : city.offlineSince());
    invitations.remove(target);
    replaceCity(joined);
  }

  public synchronized void removeMember(UUID founder, UUID member) {
    ensureOpen();
    ClaimCity city = requireFounder(founder);
    if (city.founder().equals(member)) {
      throw failure(ClaimException.Reason.FOUNDER_CANNOT_LEAVE);
    }
    if (!city.members().contains(member)) {
      throw failure(ClaimException.Reason.NOT_MEMBER);
    }
    removeMember(city, member);
  }

  public synchronized void leave(UUID player) {
    ensureOpen();
    ClaimCity city = requireMember(player);
    if (city.founder().equals(player)) {
      throw failure(ClaimException.Reason.FOUNDER_CANNOT_LEAVE);
    }
    removeMember(city, player);
  }

  public synchronized void removeCity(UUID cityId) {
    ensureOpen();
    ClaimCity city = snapshot.cities().get(cityId);
    if (city == null) {
      throw failure(ClaimException.Reason.CITY_NOT_FOUND);
    }
    Map<UUID, ClaimCity> cities = new HashMap<>(snapshot.cities());
    Map<UUID, ClaimPlot> plots = new HashMap<>(snapshot.plots());
    cities.remove(cityId);
    plots.values().removeIf(plot -> plot.cityId().equals(cityId));
    progress.keySet().removeIf(block -> city.chunks().contains(block.chunk()));
    invitations.values().removeIf(invitation -> invitation.cityId().equals(cityId));
    publish(cities, plots);
  }

  public synchronized void removePlot(UUID owner) {
    ensureOpen();
    if (!snapshot.plots().containsKey(owner)) {
      throw failure(ClaimException.Reason.NOT_MEMBER);
    }
    Map<UUID, ClaimPlot> plots = new HashMap<>(snapshot.plots());
    plots.remove(owner);
    publish(snapshot.cities(), plots);
  }

  public synchronized boolean destroyAnchorIfUnchanged(ClaimCity expected) {
    ensureOpen();
    ClaimCity current = snapshot.cities().get(expected.id());
    if (current == null || !current.anchor().equals(expected.anchor())) {
      return false;
    }
    removeCity(current.id());
    return true;
  }

  public synchronized boolean destroyAnchorIfUnchanged(ClaimPlot expected) {
    ensureOpen();
    if (snapshot.plots().get(expected.owner()) != expected) {
      return false;
    }
    removePlot(expected.owner());
    return true;
  }

  public synchronized void updateRanks(UUID founder, ClaimRanks ranks) {
    ensureOpen();
    ClaimCity city = snapshot.cityOf(founder).orElse(null);
    if (city == null || !city.founder().equals(founder) || city.ranks().equals(ranks)) {
      return;
    }
    replaceCity(new ClaimCity(city.id(), city.name(), city.founder(), city.anchor(), city.members(), city.chunks(), ranks, city.offlineSince()));
  }

  public synchronized void setOnline(UUID player, boolean online, long now) {
    ensureOpen();
    Set<UUID> connected = new HashSet<>(connectedPlayers);
    if (online ? !connected.add(player) : !connected.remove(player)) {
      return;
    }
    connectedPlayers = Set.copyOf(connected);
    if (!online) {
      placementTimes.remove(player);
    }
    ClaimCity city = snapshot.cityOf(player).orElse(null);
    if (city == null) {
      return;
    }
    long offlineSince = hasConnectedMember(city) ? 0 : now;
    if (city.offlineSince() != offlineSince) {
      replaceCity(withOfflineSince(city, offlineSince));
    }
  }

  public boolean isOnline(UUID cityId, long now) {
    ClaimCity city = snapshot.cities().get(cityId);
    return city != null && isOnline(city, now);
  }

  public boolean canEdit(UUID player, ClaimBlock block) {
    ClaimSnapshot current = snapshot;
    ClaimCity city = current.cityAt(block.chunk()).orElse(null);
    if (city == null) {
      return true;
    }
    ClaimPlot plot = current.plotAt(block.chunk()).orElse(null);
    if (plot != null) {
      return plot.owner().equals(player);
    }
    if (city.anchor().equals(block)) {
      return city.founder().equals(player);
    }
    return city.includes(player);
  }

  public int resistance(UUID player, ClaimBlock block, ClaimAction action, long now) {
    ClaimSnapshot current = snapshot;
    ClaimCity city = current.cityAt(block.chunk()).orElse(null);
    if (city == null) {
      return 1;
    }
    ClaimPlot plot = current.plotAt(block.chunk()).orElse(null);
    boolean online = plot != null && city.includes(player) || isOnline(city, now);
    return config.resistance(effectiveRanks(city.ranks()), online, action);
  }

  public synchronized ClaimAttemptResult attempt(ClaimAttempt attempt) {
    return attemptAll(List.of(attempt)).getFirst();
  }

  public synchronized List<ClaimAttemptResult> attemptAll(List<ClaimAttempt> attempts) {
    ensureOpen();
    if (attempts.isEmpty()) {
      return List.of();
    }
    ClaimAttempt first = attempts.getFirst();
    Set<ClaimBlock> targets = new HashSet<>(attempts.size());
    boolean externallyAllowed = true;
    boolean resisted = false;
    for (ClaimAttempt attempt : attempts) {
      if (!first.player().equals(attempt.player()) || first.action() != attempt.action()
          || first.timestamp() != attempt.timestamp() || !targets.add(attempt.block())) {
        throw new IllegalArgumentException("A raid batch requires unique positions and one player, action and timestamp");
      }
      externallyAllowed &= attempt.externallyAllowed();
      resisted |= !canEdit(attempt.player(), attempt.block());
    }
    if (!externallyAllowed || resisted && first.action() == ClaimAction.PLACE && !allowPlacement(first.player(), first.timestamp())) {
      return deniedResults(attempts);
    }
    List<ClaimAttemptResult> results = new ArrayList<>(attempts.size());
    List<ClaimProgress> updates = new ArrayList<>(attempts.size());
    boolean allAllowed = true;
    boolean changed = false;
    for (ClaimAttempt attempt : attempts) {
      if (canEdit(attempt.player(), attempt.block())) {
        results.add(new ClaimAttemptResult(true, false, 1, 1));
        continue;
      }
      int required = resistance(attempt.player(), attempt.block(), attempt.action(), attempt.timestamp());
      ClaimProgress previous = progress.get(attempt.block());
      if (previous != null && !previous.blockState().equals(attempt.blockState())) {
        progress.remove(attempt.block());
        changed = true;
        previous = null;
      }
      if (previous == null) {
        previous = new ClaimProgress(attempt.block(), attempt.blockState(), 0, 0);
      }
      ClaimProgress advanced = previous.advance(attempt.action(), required);
      updates.add(advanced);
      double completed = advanced.progress(attempt.action());
      allAllowed &= completed >= 1;
      results.add(new ClaimAttemptResult(completed >= 1, previous.progress(attempt.action()) < 1, completed, required));
    }
    if (allAllowed) {
      for (int index = 0; index < results.size(); index++) {
        ClaimAttemptResult result = results.get(index);
        results.set(index, new ClaimAttemptResult(true, false, result.progress(), result.required()));
      }
    } else {
      for (ClaimProgress update : updates) {
        progress.put(update.block(), update);
      }
      changed = true;
    }
    if (changed) {
      save();
    }
    return List.copyOf(results);
  }

  public synchronized Optional<ClaimProgress> progress(ClaimBlock block) {
    return Optional.ofNullable(progress.get(block));
  }

  public synchronized void blockChanged(ClaimBlock block) {
    ensureOpen();
    if (progress.remove(block) != null) {
      save();
    }
  }

  public synchronized void destroyAnchor(ClaimBlock block) {
    ensureOpen();
    ClaimCity city = snapshot.cityAt(block.chunk()).orElse(null);
    if (city == null) {
      return;
    }
    if (city.anchor().equals(block)) {
      removeCity(city.id());
      return;
    }
    ClaimPlot plot = snapshot.plotAt(block.chunk()).orElse(null);
    if (plot != null && plot.anchor().equals(block)) {
      removePlot(plot.owner());
    }
  }

  public void flush() throws IOException {
    repository.flush();
  }

  @Override
  public void close() throws IOException {
    synchronized (this) {
      if (closed) {
        return;
      }
      long now = System.currentTimeMillis();
      Map<UUID, ClaimCity> cities = new HashMap<>(snapshot.cities());
      for (ClaimCity city : snapshot.cities().values()) {
        if (hasConnectedMember(city)) {
          cities.put(city.id(), withOfflineSince(city, now));
        }
      }
      publish(cities, snapshot.plots());
      connectedPlayers = Set.of();
      closed = true;
    }
    repository.close();
  }

  private boolean isOnline(ClaimCity city, long now) {
    return hasConnectedMember(city) || now - city.offlineSince() < config.offlineGraceMillis;
  }

  private boolean hasConnectedMember(ClaimCity city) {
    Set<UUID> connected = connectedPlayers;
    if (connected.contains(city.founder())) {
      return true;
    }
    for (UUID member : city.members()) {
      if (connected.contains(member)) {
        return true;
      }
    }
    return false;
  }

  private void validateNewChunk(ClaimChunk chunk, ClaimWorldAccess world) {
    if (!config.allows(world)) {
      throw failure(ClaimException.Reason.WORLD_DENIED);
    }
    if (config.excludes(chunk)) {
      throw failure(ClaimException.Reason.EXCLUDED_AREA);
    }
    if (snapshot.cityAt(chunk).isPresent()) {
      throw failure(ClaimException.Reason.LAND_OCCUPIED);
    }
  }

  private void validateJoin(ClaimCity city, UUID target) {
    if (snapshot.cityOf(target).isPresent()) {
      throw failure(ClaimException.Reason.ALREADY_MEMBER);
    }
    if (city.members().size() >= config.memberCapacity(effectiveRanks(city.ranks()))) {
      throw failure(ClaimException.Reason.MEMBER_CAPACITY_REACHED);
    }
  }

  private ClaimCity requireMember(UUID player) {
    return snapshot.cityOf(player).orElseThrow(() -> failure(ClaimException.Reason.NOT_MEMBER));
  }

  private ClaimCity requireFounder(UUID player) {
    ClaimCity city = requireMember(player);
    if (!city.founder().equals(player)) {
      throw failure(ClaimException.Reason.NOT_FOUNDER);
    }
    return city;
  }

  private void removeMember(ClaimCity city, UUID member) {
    Set<UUID> members = new HashSet<>(city.members());
    members.remove(member);
    ClaimCity removed = new ClaimCity(city.id(), city.name(), city.founder(), city.anchor(), members,
        city.chunks(), city.ranks(), city.offlineSince());
    if (city.offlineSince() == 0 && !hasConnectedMember(removed)) {
      removed = withOfflineSince(removed, System.currentTimeMillis());
    }
    replaceCity(removed);
  }

  private boolean allowPlacement(UUID player, long now) {
    Deque<Long> times = placementTimes.computeIfAbsent(player, ignored -> new ArrayDeque<>());
    while (!times.isEmpty() && now - times.getFirst() >= 1000) {
      times.removeFirst();
    }
    if (times.size() >= config.placementAttemptsPerSecond || !times.isEmpty() && now < times.getLast()) {
      return false;
    }
    times.addLast(now);
    return true;
  }

  private List<ClaimAttemptResult> deniedResults(List<ClaimAttempt> attempts) {
    List<ClaimAttemptResult> results = new ArrayList<>(attempts.size());
    for (ClaimAttempt attempt : attempts) {
      ClaimProgress existing = progress.get(attempt.block());
      double completed = existing == null || !existing.blockState().equals(attempt.blockState()) ? 0 : existing.progress(attempt.action());
      results.add(new ClaimAttemptResult(false, false, completed,
          resistance(attempt.player(), attempt.block(), attempt.action(), attempt.timestamp())));
    }
    return List.copyOf(results);
  }

  private void replaceCity(ClaimCity city) {
    Map<UUID, ClaimCity> cities = new HashMap<>(snapshot.cities());
    cities.put(city.id(), city);
    publish(cities, snapshot.plots());
  }

  private void publish(Map<UUID, ClaimCity> cities, Map<UUID, ClaimPlot> plots) {
    snapshot = new ClaimSnapshot(cities, plots);
    save();
  }

  private void save() {
    repository.requestSave(this::state);
  }

  private synchronized ClaimState state() {
    return new ClaimState(new ArrayList<>(snapshot.cities().values()), new ArrayList<>(snapshot.plots().values()), new ArrayList<>(progress.values()));
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("Claims manager is closed");
    }
  }

  private static ClaimCity withOfflineSince(ClaimCity city, long since) {
    return new ClaimCity(city.id(), city.name(), city.founder(), city.anchor(), city.members(), city.chunks(), city.ranks(), since);
  }

  private static ClaimException failure(ClaimException.Reason reason) {
    return new ClaimException(reason);
  }
}
