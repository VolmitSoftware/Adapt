package art.arcane.adapt.api.claims;

import java.util.List;

public class ClaimsConfig {
  public List<String> worldAllowlist = List.of();
  public boolean overworldOnly = true;
  public int excludedMinimum = -1000;
  public int excludedMaximum = 1000;
  public long offlineGraceMillis = 300_000L;
  public long invitationDurationMillis = 120_000L;
  public int placementAttemptsPerSecond = 4;
  public List<Integer> memberCapacities = List.of(4, 8, 16, 32);
  public List<Integer> chunkCapacities = List.of(25, 49, 81, 121);
  public ClaimResistanceRanks resistanceRanks = new ClaimResistanceRanks();

  public void validate() {
    if (worldAllowlist == null || memberCapacities == null || chunkCapacities == null || resistanceRanks == null) {
      throw new IllegalArgumentException("Claims configuration lists are required");
    }
    if (excludedMinimum > excludedMaximum || offlineGraceMillis < 0 || invitationDurationMillis < 1
        || placementAttemptsPerSecond < 1 || placementAttemptsPerSecond > 4) {
      throw new IllegalArgumentException("Invalid Claims limits");
    }
    if (memberCapacities.size() != 4 || chunkCapacities.size() != 4) {
      throw new IllegalArgumentException("Claims requires four rank values");
    }
    int previousMembers = 0;
    int previousChunks = 9;
    for (int index = 0; index < 4; index++) {
      Integer members = memberCapacities.get(index);
      Integer chunks = chunkCapacities.get(index);
      if (members == null || chunks == null || members < previousMembers || chunks < previousChunks) {
        throw new IllegalArgumentException("Claims capacity must increase with each rank");
      }
      previousMembers = members;
      previousChunks = chunks;
    }
    worldAllowlist = List.copyOf(worldAllowlist);
    memberCapacities = List.copyOf(memberCapacities);
    chunkCapacities = List.copyOf(chunkCapacities);
    resistanceRanks.validate();
  }

  public ClaimsConfig validatedCopy() {
    ClaimsConfig copy = new ClaimsConfig();
    copy.worldAllowlist = worldAllowlist;
    copy.overworldOnly = overworldOnly;
    copy.excludedMinimum = excludedMinimum;
    copy.excludedMaximum = excludedMaximum;
    copy.offlineGraceMillis = offlineGraceMillis;
    copy.invitationDurationMillis = invitationDurationMillis;
    copy.placementAttemptsPerSecond = placementAttemptsPerSecond;
    copy.memberCapacities = memberCapacities;
    copy.chunkCapacities = chunkCapacities;
    copy.resistanceRanks = resistanceRanks == null ? null : resistanceRanks.validatedCopy();
    copy.validate();
    return copy;
  }

  public boolean allows(ClaimWorldAccess world) {
    return worldAllowlist.isEmpty() ? !overworldOnly || world.overworld() : worldAllowlist.contains(world.name());
  }

  public boolean excludes(ClaimChunk chunk) {
    long minimumX = (long) chunk.x() * 16;
    long minimumZ = (long) chunk.z() * 16;
    return minimumX <= excludedMaximum && minimumX + 15 >= excludedMinimum
        && minimumZ <= excludedMaximum && minimumZ + 15 >= excludedMinimum;
  }

  public int memberCapacity(ClaimRanks ranks) {
    return ranks.citizenship() == 0 ? 0 : memberCapacities.get(ranks.citizenship() - 1);
  }

  public int chunkCapacity(ClaimRanks ranks) {
    return ranks.expansion() == 0 ? 9 : chunkCapacities.get(ranks.expansion() - 1);
  }

  public int resistance(ClaimRanks ranks, boolean online, ClaimAction action) {
    return resistanceRanks.forRank(Math.max(1, ranks.fortification())).required(action, online);
  }
}
