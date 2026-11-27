package art.arcane.adapt.content.adaptation.agility.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/**
 * Holds while the player has kept one sprint going for 3 s. The recent form asks instead whether such a sprint ended
 * within the window, or is still going.
 */
public class AgilityState extends StateBlock<AgilityState.Config> {
  private final Map<UUID, Long> sprintStarts = playerState();
  private final Map<UUID, Long> longSprintEnds = playerState();

  public AgilityState() {
    super(SkillAgility.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("golden_boots"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSprintEvent e) {
    PlayerRef player = e.player();
    UUID id = player.uuid();
    long now = System.currentTimeMillis();
    if (e.isSprinting()) {
      if (hasActiveAdaptation(player)) {
        sprintStarts.put(id, now);
      }
      return;
    }
    Long started = sprintStarts.remove(id);
    if (started != null && now - started >= getConfig().sprintMillis) {
      longSprintEnds.put(id, now);
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    Long started = sprintStarts.get(player.uuid());
    return started != null && player.isSprinting()
        && System.currentTimeMillis() - started >= getConfig().sprintMillis;
  }

  @Override
  public boolean heldWithin(PlayerRef player, long windowMillis) {
    Long ended = longSprintEnds.get(player.uuid());
    return holds(player) || (ended != null && System.currentTimeMillis() - ended <= windowMillis);
  }

  @ConfigDescription("You have sprinted for 3 s without stopping.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long one sprint must last, in milliseconds.", impact = "Higher values ask for a longer sprint.")
    long sprintMillis = 3_000L;
  }
}
