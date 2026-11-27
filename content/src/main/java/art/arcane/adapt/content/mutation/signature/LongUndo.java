package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.architect.ArchitectPlacement;
import art.arcane.adapt.content.adaptation.architect.upgraded.MasterBuilder;
import art.arcane.adapt.content.adaptation.architect.upgraded.Skyhook;
import art.arcane.adapt.content.adaptation.architect.upgraded.WreckingHand;
import art.arcane.adapt.content.adaptation.chronos.ChronosRewind;
import art.arcane.adapt.content.adaptation.chronos.ChronosSoundFX;
import art.arcane.adapt.api.world.BuildBatches;
import art.arcane.adapt.api.world.BuildResult;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Set;


/**
 * Chronos with Architect, replacing Convergence: the clock gesture takes back your newest chalk fill, stamp, wand face
 * or replace, ten times further back than Wrecking Hand's undo reaches. Like that undo, it returns exactly what each
 * block that still stands cost, so nothing can come back twice. For a while after, Time Lord's restoration near the
 * spot places the undone build back, paid again from your inventory.
 */
public final class LongUndo extends Signature<LongUndo.Config> {
  private static final Set<Id> BUILDS = Set.of(MasterBuilder.ID, Skyhook.ID, WreckingHand.ID, ArchitectPlacement.ID);

  public LongUndo(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("brush"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Format.duration(windowMillis(level), 0), 1);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef player = e.player();
    if (SignatureKit.clockSwap(e) && getActiveLevel(player) > 0) {
      SignatureKit.settle(e, fire(player, null, player.location(), false));
    }
  }

  @Override
  public Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    if (BuildBatches.isRunning(player)) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.upgrade.architect.master_builder.busy").resolve());
      SignatureKit.refuse(player);
      return Outcome.MISSED;
    }
    boolean started = BuildBatches.undo(player, BUILDS, windowMillis(use.level()), getConfig().blocksPerTick,
        (long) (getConfig().redoMinutes * 60_000D), (BuildResult result) -> {
          WreckingHand wreckingHand = WreckingHand.find();
          if (wreckingHand != null) {
            wreckingHand.forget(player, result.blocks());
          }
          HudStatus.show(player, TextFormat.AQUA + Text.key("adapt.upgrade.architect.wrecking_hand.undone").withMarkup("count", String.valueOf(result.undone())).resolve());
          ChronosRewind.arriveFx(this, player);
        });
    if (!started) {
      HudStatus.show(player, TextFormat.GRAY + Text.key("adapt.upgrade.architect.wrecking_hand.nothing_to_undo").resolve());
      ChronosSoundFX.playClockReject(player);
      SignatureKit.refuse(player);
      return Outcome.MISSED;
    }
    return Outcome.INSTANT;
  }

  long windowMillis(int level) {
    double minutes = switch (Math.max(1, Math.min(3, level))) {
      case 1 -> getConfig().windowMinutesLevel1;
      case 2 -> getConfig().windowMinutesLevel2;
      default -> getConfig().windowMinutesLevel3;
    };
    return (long) (minutes * 60_000D);
  }

  @ConfigDescription("Undo your newest Architect build from far further back than Wrecking Hand allows.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "How old a build may be and still be undone at level 1, in minutes.", impact = "Higher values let older builds be undone.")
    double windowMinutesLevel1 = 10;
    @ConfigDoc(value = "How old a build may be and still be undone at level 2, in minutes.", impact = "Higher values let older builds be undone.")
    double windowMinutesLevel2 = 20;
    @ConfigDoc(value = "How old a build may be and still be undone at level 3, in minutes.", impact = "Higher values let older builds be undone.")
    double windowMinutesLevel3 = 40;
    @ConfigDoc(value = "How long Time Lord's restoration can still place an undone build back, in minutes.", impact = "Higher values leave more time to take an undo back.")
    double redoMinutes = 10;
    @ConfigDoc(value = "Blocks the undo takes back per tick, before the server-wide cap.", impact = "Higher values finish sooner but cost more per tick.")
    int blocksPerTick = 8;
  }
}
