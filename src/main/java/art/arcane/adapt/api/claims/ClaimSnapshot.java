package art.arcane.adapt.api.claims;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClaimSnapshot {
  private final Map<UUID, ClaimCity> cities;
  private final Map<UUID, ClaimPlot> plots;
  private final Map<ClaimChunk, UUID> cityChunks;
  private final Map<ClaimChunk, UUID> plotChunks;
  private final Map<UUID, UUID> memberships;

  public ClaimSnapshot(Map<UUID, ClaimCity> cities, Map<UUID, ClaimPlot> plots) {
    this.cities = Map.copyOf(cities);
    this.plots = Map.copyOf(plots);
    Map<ClaimChunk, UUID> cityIndex = new HashMap<>();
    Map<ClaimChunk, UUID> plotIndex = new HashMap<>();
    Map<UUID, UUID> memberIndex = new HashMap<>();
    for (Map.Entry<UUID, ClaimCity> entry : this.cities.entrySet()) {
      ClaimCity city = entry.getValue();
      if (!entry.getKey().equals(city.id()) || !city.chunks().containsAll(footprint(city.anchor().chunk()))) {
        throw new IllegalArgumentException("Invalid city footprint");
      }
      putUnique(memberIndex, city.founder(), city.id(), "A founder cannot belong to another city");
      for (UUID member : city.members()) {
        putUnique(memberIndex, member, city.id(), "A player cannot belong to two cities");
      }
      for (ClaimChunk chunk : city.chunks()) {
        putUnique(cityIndex, chunk, city.id(), "City footprints overlap");
      }
    }
    for (Map.Entry<UUID, ClaimPlot> entry : this.plots.entrySet()) {
      ClaimPlot plot = entry.getValue();
      ClaimCity city = this.cities.get(plot.cityId());
      if (!entry.getKey().equals(plot.owner()) || city == null || !city.chunks().containsAll(plot.chunks())
          || plot.chunks().contains(city.anchor().chunk())) {
        throw new IllegalArgumentException("Invalid personal plot ownership");
      }
      for (ClaimChunk chunk : plot.chunks()) {
        putUnique(plotIndex, chunk, plot.owner(), "Personal footprints overlap");
      }
    }
    cityChunks = Map.copyOf(cityIndex);
    plotChunks = Map.copyOf(plotIndex);
    memberships = Map.copyOf(memberIndex);
  }

  public static Set<ClaimChunk> footprint(ClaimChunk center) {
    Set<ClaimChunk> chunks = new HashSet<>(9);
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        chunks.add(new ClaimChunk(center.worldId(), Math.addExact(center.x(), x), Math.addExact(center.z(), z)));
      }
    }
    return Set.copyOf(chunks);
  }

  public Map<UUID, ClaimCity> cities() {
    return cities;
  }

  public Map<UUID, ClaimPlot> plots() {
    return plots;
  }

  public Optional<ClaimCity> cityAt(ClaimChunk chunk) {
    UUID city = cityChunks.get(chunk);
    return city == null ? Optional.empty() : Optional.of(cities.get(city));
  }

  public Optional<ClaimPlot> plotAt(ClaimChunk chunk) {
    UUID owner = plotChunks.get(chunk);
    return owner == null ? Optional.empty() : Optional.of(plots.get(owner));
  }

  public Optional<ClaimCity> cityOf(UUID player) {
    UUID city = memberships.get(player);
    return city == null ? Optional.empty() : Optional.of(cities.get(city));
  }

  private static <K, V> void putUnique(Map<K, V> map, K key, V value, String failure) {
    if (map.putIfAbsent(key, value) != null) {
      throw new IllegalArgumentException(failure);
    }
  }
}
