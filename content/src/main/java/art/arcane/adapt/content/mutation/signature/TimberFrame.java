package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.content.adaptation.axe.AxeFelling;
import art.arcane.adapt.content.adaptation.axe.AxePreferences;
import art.arcane.adapt.content.adaptation.axe.blocks.AxesWorkStrike;
import art.arcane.adapt.content.adaptation.axe.upgraded.Timberfall;
import art.arcane.adapt.api.world.BonusBreaks;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.adapt.api.world.BlockFlag.VEIN_MINED;

/**
 * Axes and Architect, either leading, replacing Convergence: the logs a Timberfall swing fells drop stripped, ready as
 * beams, and from level 2 the felled-logs choice cuts them into planks at the table's 4 per log instead.
 */
public final class TimberFrame extends Signature<TimberFrame.Config> {
  private static final int PLANKS_PER_LOG = 4;
  private final Map<UUID, Felling> fellings = new ConcurrentHashMap<>();

  public TimberFrame(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("stripped_oak_log"));
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.FELLED_LOGS);
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, logs(level), 1);
    statLore(v, level >= getConfig().planksLevel ? PLANKS_PER_LOG : 0, 2);
  }

  @Override
  public long cooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef player = e.player();
    BlockRef origin = e.block();
    if (e.protectionProbe() || BonusBreaks.isActive(player) || fellings.containsKey(player.uuid())
        || !Timberfall.fells(origin) || !AxeFelling.isWood(origin.type()) || !ItemKinds.isAxe(player.inventory().mainHand())
        || getActiveLevel(player) <= 0) {
      return;
    }
    fire(player, null, origin.location(), false);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(BlockDropItemEvent e) {
    PlayerRef player = e.player();
    Felling felling = fellings.get(player.uuid());
    if (felling == null) {
      return;
    }
    if (felling.left()[0] <= 0 || (!BonusBreaks.isActive(player) && !VEIN_MINED.get(e.block()))) {
      return;
    }
    ItemOps items = AdaptApi.platform().host().items();
    for (ItemEntityRef drop : e.items()) {
      ItemRef stack = drop.item();
      Id shaped = felling.planks() ? AxesWorkStrike.planksOf(stack.type()) : AxesWorkStrike.strippedOf(stack.type());
      if (shaped == null || !items.isItem(shaped) || felling.left()[0] < stack.amount()) {
        continue;
      }
      felling.left()[0] -= stack.amount();
      drop.setItem(items.create(shaped, stack.amount() * (felling.planks() ? PLANKS_PER_LOG : 1)));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    fellings.remove(e.player().uuid());
  }

  @Override
  public Outcome run(BlockUse use) {
    boolean planks = use.level() >= getConfig().planksLevel
        && use.adaptation().preference(use.player(), AxePreferences.FELLED_LOGS) == AxePreferences.FelledLogs.PLANKS;
    PlayerRef player = use.player();
    Felling felling = new Felling(planks, new int[]{logs(use.level())});
    fellings.put(player.uuid(), felling);
    if (!AdaptApi.scheduler().runEntity(player, () -> fellings.remove(player.uuid(), felling), Math.max(2, getConfig().fellWindowTicks))) {
      fellings.remove(player.uuid(), felling);
      return Outcome.MISSED;
    }
    return Outcome.INSTANT;
  }

  int logs(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().logsAtFirstLevel,
        getConfig().logsAtTopLevel));
  }

  private record Felling(boolean planks, int[] left) {
  }

  @ConfigDescription("Logs a Timberfall swing fells drop stripped, or as planks by choice.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Logs shaped per felled tree at level 1.", impact = "Higher values shape more of each tree.")
    int logsAtFirstLevel = 32;
    @ConfigDoc(value = "Logs shaped per felled tree at the top level.", impact = "Higher values shape more of each tree.")
    int logsAtTopLevel = 160;
    @ConfigDoc(value = "Level from which the felled-logs choice can cut planks.", impact = "Lower values unlock planks sooner.")
    int planksLevel = 2;
    @ConfigDoc(value = "Ticks after the first log during which the tree's drops are shaped.", impact = "Higher values catch slower fells.")
    int fellWindowTicks = 4;
    @ConfigDoc(value = "Cooldown after a tree is shaped, in milliseconds.", impact = "Higher values leave more trees unshaped.")
    long cooldownMillis = 0;
  }
}
