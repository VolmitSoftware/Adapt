package art.arcane.adapt.content.adaptation.architect.blocks;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.architect.ArchitectChalkLine;
import art.arcane.adapt.content.adaptation.architect.ArchitectReach;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * For a few seconds the player's placements reach farther and snap to the chalk guide they carry. The reach rides on the
 * block interaction range attribute where the server has it; elsewhere a right-click into the air places the held block
 * where the look ray lands within the longer reach. A placement aimed near an open spot of the guide lands on that spot.
 * Nothing is ever given back, so this is not a payout.
 */
public class ArchitectWorkStrike extends StrikeBlock<ArchitectWorkStrike.Config> {
  private static final String REACH_SLOT = "reach";
  private final Map<UUID, Window> windows = playerState();
  private volatile ArchitectChalkLine chalk;

  public ArchitectWorkStrike() {
    super(SkillArchitect.ID, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("scaffolding"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    double extra = Math.round(use.scaled(getConfig().reachAtFirstLevel, getConfig().reachAtTopLevel));
    int ticks = (int) Math.max(1L, getConfig().windowMillis / 50L);
    windows.put(player.uuid(), new Window(System.currentTimeMillis() + getConfig().windowMillis, extra));
    if (player.hasAttribute(Attributes.BLOCK_INTERACTION_RANGE)) {
      AdaptAttributeService.get().applyTimed(player, getId().toString(), REACH_SLOT, Attributes.BLOCK_INTERACTION_RANGE, extra,
          AttributeOperation.ADD_VALUE, ticks);
    }
    return Outcome.lasting(player, ticks);
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    if (!e.action().isRightClick() || e.hand() != Hand.MAIN_HAND) {
      return;
    }
    PlayerRef player = e.player();
    Window window = windows.get(player.uuid());
    if (window == null) {
      return;
    }
    if (System.currentTimeMillis() > window.until()) {
      windows.remove(player.uuid());
      return;
    }
    if (!hasActiveAdaptation(player)) {
      return;
    }
    boolean nativeReach = player.hasAttribute(Attributes.BLOCK_INTERACTION_RANGE);
    double reach = nativeReach
        ? AdaptAttributeService.get().value(player, Attributes.BLOCK_INTERACTION_RANGE)
        : ArchitectReach.VANILLA_REACH + window.extra();
    BlockRef spot = ArchitectReach.snapped(player, guide(player), reach);
    if (spot == null && !nativeReach && e.action() == InteractAction.RIGHT_CLICK_AIR) {
      spot = ArchitectReach.aimed(player, reach);
    }
    if (spot == null || vanillaTarget(e, spot) || !ArchitectReach.placeHeld(this, player, spot)) {
      return;
    }
    e.setCancelled(true);
    addStat(player, "architect.mutated.reach-placements", 1);
  }

  private static boolean vanillaTarget(PlayerInteractEvent e, BlockRef spot) {
    BlockRef clicked = e.clickedBlock();
    return clicked != null && e.clickedFace() != null && clicked.relative(e.clickedFace()).equals(spot);
  }

  private List<BlockRef> guide(PlayerRef player) {
    ArchitectChalkLine line = chalk;
    if (line == null) {
      for (Adaptation<?> adaptation : getSkill().getAdaptations()) {
        if (adaptation instanceof ArchitectChalkLine found) {
          line = found;
          chalk = found;
          break;
        }
      }
    }
    return line == null ? List.of() : line.openGuideSpots(player);
  }

  private record Window(long until, double extra) {
  }

  @ConfigDescription("For 5 s your placements reach 3 more blocks and snap to your chalk guide.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the longer reach lasts, in milliseconds.", impact = "Higher values give more time to build at range.")
    long windowMillis = 5_000L;
    @ConfigDoc(value = "Blocks of extra placement reach at level 1.", impact = "Higher values place blocks farther away.")
    double reachAtFirstLevel = 3D;
    @ConfigDoc(value = "Blocks of extra placement reach at the top level.", impact = "Higher values place blocks farther away.")
    double reachAtTopLevel = 5D;
  }
}
