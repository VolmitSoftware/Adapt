package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.format.C;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Relic Appraiser's stamp: how a relic is appraised, paid out and kept appraised through placement. */
public final class RelicAppraisal {
  public static final DataKey<Byte> APPRAISED = DataKey.adapt("relic_appraised", DataCodecs.BYTE);
  private static final DataKey<byte[]> PLACED = DataKey.adapt("relic_appraised_item", DataCodecs.BYTE_ARRAY);

  private RelicAppraisal() {
  }

  /** A sneaking main-hand right click, the gesture that appraises the held relic. */
  public static boolean isAppraisalClick(PlayerInteractEvent e) {
    InteractAction action = e.action();
    return e.hand() == Hand.MAIN_HAND
        && (action == InteractAction.RIGHT_CLICK_AIR || action == InteractAction.RIGHT_CLICK_BLOCK)
        && e.player().isSneaking();
  }

  public static boolean isAppraised(ItemRef item) {
    return DiscoveryRelicAppraiser.isAppraised(item, APPRAISED);
  }

  /** Marks the held relic as appraised and adds the appraisal line to its lore. */
  public static void stamp(PlayerRef player, ItemRef hand) {
    hand.data().set(APPRAISED, (byte) 1);
    List<String> lore = new ArrayList<>(hand.lore());
    lore.add(C.LIGHT_PURPLE + "" + C.ITALIC + AdaptLanguage.text(DiscoveryMessages.RELIC_APPRAISER_LORE_TAG));
    hand.setLore(lore);
    player.inventory().setMainHand(hand);
  }

  /** Pays a random amount between the bounds to one random enabled skill other than {@code source}. */
  public static void grantRandomSkillXp(PlayerRef player, Skill<?> source, double minimum, double maximum) {
    List<Skill<?>> eligible = DiscoveryRelicAppraiser.eligibleRandomSkills(
        AdaptCore.get().getAdaptServer().getSkillRegistry().getSkills(),
        player,
        source
    );
    if (eligible.isEmpty()) {
      return;
    }
    ThreadLocalRandom random = ThreadLocalRandom.current();
    double reward = DiscoveryRelicAppraiser.randomSkillXp(minimum, maximum, random.nextDouble());
    if (reward <= 0D) {
      return;
    }
    Skill<?> selected = eligible.get(random.nextInt(eligible.size()));
    selected.xp(player, reward, "relic-appraiser");
  }

  public static void rememberPlaced(BlockPlaceEvent e) {
    ItemRef placed = e.itemInHand();
    if (!DiscoveryRelicAppraiser.isAppraisableBlock(placed.type()) || !isAppraised(placed)) {
      return;
    }

    DataHolder tile = e.block().blockEntityData();
    if (tile == null) {
      return;
    }

    ItemRef snapshot = placed.copyWithAmount(1);
    tile.set(
        PLACED,
        AdaptCore.platform().host().items().serialize(snapshot)
    );
  }

  public static void restorePlaced(BlockDropItemEvent e) {
    BlockStateRef state = e.brokenState();
    DataHolder tile = e.brokenBlockEntityData();
    if (!DiscoveryRelicAppraiser.isAppraisableBlock(state.type()) || tile == null) {
      return;
    }

    byte[] encoded = tile.get(PLACED);
    if (encoded == null || encoded.length == 0) {
      return;
    }

    ItemRef snapshot;
    try {
      snapshot = AdaptCore.platform().host().items().deserialize(encoded);
    } catch (IllegalArgumentException ex) {
      AdaptLog.error("Could not restore an appraised relic broken at " + e.block().location() + ".");
      AdaptLog.error(ex);
      return;
    }

    if (!DiscoveryRelicAppraiser.isAppraisableBlock(snapshot.type()) || !isAppraised(snapshot)) {
      return;
    }
    DiscoveryRelicAppraiser.restoreAppraisedDrop(e.items(), snapshot);
  }
}
