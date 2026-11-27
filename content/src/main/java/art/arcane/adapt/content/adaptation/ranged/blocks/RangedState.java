package art.arcane.adapt.content.adaptation.ranged.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.content.adaptation.ranged.RangedTrajectorySight;
import art.arcane.adapt.content.skill.SkillRanged;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/**
 * Holds while a bow or crossbow has been drawn for the full draw time. Asked for a recent window, it also holds when
 * the player loosed a fully drawn shot within it.
 */
public class RangedState extends StateBlock<RangedState.Config> {
  private static final Id BOW = Id.minecraft("bow");
  private static final Id CROSSBOW = Id.minecraft("crossbow");
  private final Map<UUID, Long> drawnSince = playerState();
  private final Map<UUID, Long> loosedFull = playerState();

  public RangedState() {
    super(SkillRanged.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("target"));
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    PlayerRef player = e.player();
    ItemRef item = e.item();
    if (!e.action().isRightClick() || e.useItem() == UseResult.DENY || item == null
        || !(item.type().equals(BOW) || item.type().equals(CROSSBOW)) || player.isHandRaised()
        || !hasActiveAdaptation(player)) {
      return;
    }
    drawnSince.put(player.uuid(), System.currentTimeMillis());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityShootBowEvent e) {
    if (e.shooter() instanceof PlayerRef player && e.force() >= 1F && hasActiveAdaptation(player)) {
      loosedFull.put(player.uuid(), System.currentTimeMillis());
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    Long since = drawnSince.get(player.uuid());
    return since != null && RangedTrajectorySight.isDrawingBow(player, player.activeItem())
        && System.currentTimeMillis() - since >= getConfig().fullDrawMillis;
  }

  @Override
  public boolean heldWithin(PlayerRef player, long windowMillis) {
    Long loosed = loosedFull.get(player.uuid());
    return holds(player) || (loosed != null && System.currentTimeMillis() - loosed <= windowMillis);
  }

  @ConfigDescription("You are at full draw.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long a bow must be drawn to count as full draw, in milliseconds.", impact = "Higher values ask for a longer draw.")
    long fullDrawMillis = 1_000L;
  }
}
