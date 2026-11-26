package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.batch.BuildBatches;
import art.arcane.adapt.api.batch.BuildResult;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.content.adaptation.architect.ArchitectPlacement;
import art.arcane.adapt.content.adaptation.architect.upgraded.MasterBuilder;
import art.arcane.adapt.content.adaptation.architect.upgraded.Skyhook;
import art.arcane.adapt.content.adaptation.architect.upgraded.WreckingHand;
import art.arcane.adapt.content.adaptation.chronos.ChronosRewind;
import art.arcane.adapt.content.adaptation.chronos.ChronosSoundFX;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.Set;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Chronos with Architect, replacing Convergence: the clock gesture takes back your newest chalk fill, stamp, wand face
 * or replace, ten times further back than Wrecking Hand's undo reaches. Like that undo, it returns exactly what each
 * block that still stands cost, so nothing can come back twice. For a while after, Time Lord's restoration near the
 * spot places the undone build back, paid again from your inventory.
 */
public final class LongUndo extends Signature<LongUndo.Config> {
  private static final Set<String> BUILDS = Set.of(MasterBuilder.ID, Skyhook.ID, WreckingHand.ID, ArchitectPlacement.ID);

  public LongUndo(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("brush"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Form.duration(windowMillis(level), 0), 1);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef player = e.player();
    if (SignatureKit.clockSwap(e) && getActiveLevel(player) > 0) {
      SignatureKit.settle(e, fire(player, null, player.location(), false));
    }
  }

  @Override
  protected Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    if (BuildBatches.get().isRunning(player)) {
      AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(ArchitectMessages.MASTER_BUILDER_BUSY));
      SignatureKit.refuse(player);
      return Outcome.MISSED;
    }
    boolean started = BuildBatches.get().undo(player, BUILDS, windowMillis(use.level()), getConfig().blocksPerTick,
        (long) (getConfig().redoMinutes * 60_000D), (BuildResult result) -> {
          WreckingHand wreckingHand = WreckingHand.find();
          if (wreckingHand != null) {
            wreckingHand.forget(player, result.blocks());
          }
          AdaptHud.actionBar(player, C.AQUA + AdaptLanguage.text(ArchitectMessages.WRECKING_HAND_UNDONE,
              trusted("count", String.valueOf(result.undone()))));
          ChronosRewind.arriveFx(this, player);
        });
    if (!started) {
      AdaptHud.actionBar(player, C.GRAY + AdaptLanguage.text(ArchitectMessages.WRECKING_HAND_NOTHING_TO_UNDO));
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
