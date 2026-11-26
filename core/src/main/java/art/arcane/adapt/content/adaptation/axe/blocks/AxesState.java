package art.arcane.adapt.content.adaptation.axe.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.StateBlock;
import art.arcane.adapt.content.adaptation.axe.AxeFelling;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.Map;
import java.util.UUID;

/** Holds for a few seconds after chopping wood or landing a melee hit with an axe. */
public class AxesState extends StateBlock<AxesState.Config> {
  private final Map<UUID, Long> lastSwing = playerState();

  public AxesState() {
    super("block-axes-state");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("stone_axe"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, this::isAxe);
    if (combat != null) {
      lastSwing.put(combat.attacker().uuid(), System.currentTimeMillis());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef player = e.player();
    if (!e.protectionProbe() && AxeFelling.isWood(e.block().type()) && isAxe(player.inventory().mainHand())
        && hasActiveAdaptation(player)) {
      lastSwing.put(player.uuid(), System.currentTimeMillis());
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    return heldWithin(player, 0L);
  }

  @Override
  public boolean heldWithin(PlayerRef player, long windowMillis) {
    Long swing = lastSwing.get(player.uuid());
    return swing != null && System.currentTimeMillis() - swing <= getConfig().windowMillis + windowMillis;
  }

  @ConfigDescription("You chopped wood or hit with an axe in the last 4 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long after chopping or an axe hit the State holds, in milliseconds.", impact = "Higher values keep the State longer.")
    long windowMillis = 4_000L;
  }
}
