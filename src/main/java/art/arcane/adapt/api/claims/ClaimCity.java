package art.arcane.adapt.api.claims;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ClaimCity(UUID id, String name, UUID founder, ClaimBlock anchor, Set<UUID> members,
                        Set<ClaimChunk> chunks, ClaimRanks ranks, long offlineSince) {
  public ClaimCity {
    Objects.requireNonNull(id);
    Objects.requireNonNull(name);
    Objects.requireNonNull(founder);
    Objects.requireNonNull(anchor);
    Objects.requireNonNull(ranks);
    members = Set.copyOf(members);
    chunks = Set.copyOf(chunks);
    if (members.contains(founder) || !chunks.contains(anchor.chunk()) || offlineSince < 0) {
      throw new IllegalArgumentException("Invalid city ownership");
    }
    for (ClaimChunk chunk : chunks) {
      if (!chunk.worldId().equals(anchor.worldId())) {
        throw new IllegalArgumentException("City land must share the anchor world");
      }
    }
  }

  public boolean includes(UUID player) {
    return founder.equals(player) || members.contains(player);
  }
}
