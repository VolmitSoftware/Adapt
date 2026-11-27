package art.arcane.adapt.content.adaptation.discovery.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryFindEvent;
import art.arcane.adapt.content.skill.SkillDiscovery;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.Map;
import java.util.UUID;

/** Holds while the player stands in the chunk Discovery last recorded as one they had never entered. */
public class DiscoveryState extends StateBlock<DiscoveryState.Config> {
  private final Map<UUID, Chunk> freshChunks = playerState();

  public DiscoveryState() {
    super(SkillDiscovery.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("filled_map"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(DiscoveryFindEvent e) {
    if (e.chunk() && hasActiveAdaptation(e.player())) {
      freshChunks.put(e.player().uuid(), Chunk.of(e.at()));
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    Chunk fresh = freshChunks.get(player.uuid());
    return fresh != null && fresh.equals(Chunk.of(player.location()));
  }

  private record Chunk(UUID world, int x, int z) {
    static Chunk of(Loc at) {
      return new Chunk(at.world().uid(), at.chunkX(), at.chunkZ());
    }
  }

  @ConfigDescription("You are in a chunk you had never entered.")
  public static class Config extends MutationBlock.Config {
  }
}
