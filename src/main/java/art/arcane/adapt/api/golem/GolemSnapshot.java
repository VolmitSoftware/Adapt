package art.arcane.adapt.api.golem;

import art.arcane.adapt.util.common.io.Json;
import com.google.gson.JsonParseException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record GolemSnapshot(UUID id, UUID owner, GolemBody body, List<GolemCoreType> cores, List<GolemRune> runes, int channels,
                            GolemMode mode, GolemState.Anchor home, GolemState.Anchor homeChest, String customName, Stats stats,
                            boolean masterwork, boolean snowTrail, double essence, List<String> carried, double health,
                            double maxHealthMark, int copperStage, boolean waxed, long takenAt) {
  public static final int MAX_COPPER_STAGE = 3;

  public GolemSnapshot {
    Objects.requireNonNull(id);
    Objects.requireNonNull(owner);
    Objects.requireNonNull(body);
    Objects.requireNonNull(mode);
    cores = List.copyOf(cores);
    if (cores.isEmpty() || cores.size() > GolemState.MAX_CORES) {
      throw new IllegalArgumentException("A golem snapshot holds 1 to " + GolemState.MAX_CORES + " cores, found " + cores.size());
    }
    runes = List.copyOf(runes.size() > GolemRuneLayout.MAX_SLOTS ? runes.subList(0, GolemRuneLayout.MAX_SLOTS) : runes);
    channels = ChannelMask.normalize(channels);
    stats = stats == null ? Stats.NONE : stats;
    essence = nonNegative(essence);
    carried = List.copyOf(carried);
    health = nonNegative(health);
    maxHealthMark = nonNegative(maxHealthMark);
    copperStage = Math.max(0, Math.min(MAX_COPPER_STAGE, copperStage));
  }

  public static GolemSnapshot capture(GolemState state, Traits traits, long takenAt) {
    List<GolemCoreType> cores = state.cores().isEmpty() ? List.of(state.primaryCore()) : state.cores();
    return new GolemSnapshot(state.id(), state.owner(), state.body(), cores, state.runes(), state.channels(), state.mode(), state.home(),
        state.homeChest(), traits.customName(), Stats.of(state.stats()), state.masterwork(), state.snowTrail(), state.essence(),
        state.carried(), traits.health(), state.maxHealthMark(), traits.copperStage(), traits.waxed(), takenAt);
  }

  public static Optional<GolemSnapshot> fromJson(String json) {
    if (json == null || json.isBlank()) {
      return Optional.empty();
    }
    try {
      Stored stored = Json.fromJson(json, Stored.class);
      return stored == null ? Optional.empty() : stored.resolve();
    } catch (JsonParseException | IllegalArgumentException | IllegalStateException | ClassCastException malformed) {
      return Optional.empty();
    }
  }

  public static String serializeName(Component name) {
    return name == null ? null : GsonComponentSerializer.gson().serialize(name);
  }

  public String toJson() {
    return Json.toJson(new Stored(this), false);
  }

  public GolemCoreType primaryCore() {
    return cores.getFirst();
  }

  public Optional<Component> name() {
    if (customName == null || customName.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(GsonComponentSerializer.gson().deserialize(customName));
    } catch (JsonParseException | IllegalArgumentException | IllegalStateException malformed) {
      return Optional.empty();
    }
  }

  public GolemSnapshot withoutCargo() {
    return new GolemSnapshot(id, owner, body, cores, runes, channels, mode, home, homeChest, customName, stats, masterwork, snowTrail, 0D,
        List.of(), health, maxHealthMark, copperStage, waxed, takenAt);
  }

  private static double nonNegative(double value) {
    return Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }

  public record Traits(String customName, double health, int copperStage, boolean waxed) {
  }

  public record Stats(long kills, double damage, long tasks, long itemsMoved) {
    public static final Stats NONE = new Stats(0L, 0D, 0L, 0L);

    public static Stats of(GolemState.Stats stats) {
      return new Stats(stats.kills(), stats.damage(), stats.tasks(), stats.itemsMoved());
    }
  }

  private static final class Stored {
    private UUID id;
    private UUID owner;
    private String body;
    private List<String> cores;
    private List<StoredRune> runes;
    private int channels;
    private String mode;
    private GolemState.Anchor home;
    private GolemState.Anchor homeChest;
    private String customName;
    private long kills;
    private double damage;
    private long tasks;
    private long itemsMoved;
    private boolean masterwork;
    private boolean snowTrail;
    private double essence;
    private List<String> carried;
    private double health;
    private double maxHealthMark;
    private int copperStage;
    private boolean waxed;
    private long takenAt;

    private Stored() {
    }

    private Stored(GolemSnapshot snapshot) {
      id = snapshot.id();
      owner = snapshot.owner();
      body = snapshot.body().id();
      cores = new ArrayList<>(snapshot.cores().size());
      for (GolemCoreType core : snapshot.cores()) {
        cores.add(core.id());
      }
      runes = new ArrayList<>(snapshot.runes().size());
      for (GolemRune rune : snapshot.runes()) {
        runes.add(new StoredRune(rune));
      }
      channels = snapshot.channels();
      mode = snapshot.mode().id();
      home = snapshot.home();
      homeChest = snapshot.homeChest();
      customName = snapshot.customName();
      kills = snapshot.stats().kills();
      damage = snapshot.stats().damage();
      tasks = snapshot.stats().tasks();
      itemsMoved = snapshot.stats().itemsMoved();
      masterwork = snapshot.masterwork();
      snowTrail = snapshot.snowTrail();
      essence = snapshot.essence();
      carried = new ArrayList<>(snapshot.carried());
      health = snapshot.health();
      maxHealthMark = snapshot.maxHealthMark();
      copperStage = snapshot.copperStage();
      waxed = snapshot.waxed();
      takenAt = snapshot.takenAt();
    }

    private Optional<GolemSnapshot> resolve() {
      Optional<GolemBody> resolvedBody = GolemBody.fromId(body);
      List<GolemCoreType> resolvedCores = resolveCores();
      if (id == null || owner == null || resolvedBody.isEmpty() || resolvedCores.isEmpty()) {
        return Optional.empty();
      }
      GolemMode resolvedMode = GolemMode.fromId(mode).orElse(GolemMode.defaultFor(resolvedCores.getFirst()));
      return Optional.of(new GolemSnapshot(id, owner, resolvedBody.get(), resolvedCores, resolveRunes(), channels, resolvedMode, placed(home),
          placed(homeChest), customName, new Stats(kills, damage, tasks, itemsMoved), masterwork, snowTrail, essence, resolveCarried(), health,
          maxHealthMark, copperStage, waxed, takenAt));
    }

    private List<String> resolveCarried() {
      List<String> resolved = new ArrayList<>(carried == null ? 0 : carried.size());
      if (carried == null) {
        return resolved;
      }
      for (String stack : carried) {
        if (stack != null) {
          resolved.add(stack);
        }
      }
      return resolved;
    }

    private static GolemState.Anchor placed(GolemState.Anchor anchor) {
      return anchor == null || anchor.world() == null ? null : anchor;
    }

    private List<GolemCoreType> resolveCores() {
      List<GolemCoreType> resolved = new ArrayList<>(GolemState.MAX_CORES);
      if (cores == null) {
        return resolved;
      }
      for (String core : cores) {
        Optional<GolemCoreType> known = GolemCoreType.fromId(core);
        if (known.isPresent() && resolved.size() < GolemState.MAX_CORES) {
          resolved.add(known.get());
        }
      }
      return resolved;
    }

    private List<GolemRune> resolveRunes() {
      List<GolemRune> resolved = new ArrayList<>(runes == null ? 0 : runes.size());
      if (runes == null) {
        return resolved;
      }
      for (StoredRune rune : runes) {
        GolemRune known = rune == null ? null : rune.resolve();
        if (known != null) {
          resolved.add(known);
        }
      }
      return resolved;
    }
  }

  private static final class StoredRune {
    private String aspect;
    private int tier;

    private StoredRune() {
    }

    private StoredRune(GolemRune rune) {
      aspect = rune.aspect().id();
      tier = rune.tier();
    }

    private GolemRune resolve() {
      if (!GolemRuneType.isTier(tier)) {
        return null;
      }
      return GolemRuneType.fromId(aspect).map(type -> new GolemRune(type, tier)).orElse(null);
    }
  }
}
