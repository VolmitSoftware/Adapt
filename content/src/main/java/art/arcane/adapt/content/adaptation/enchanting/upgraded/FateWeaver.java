package art.arcane.adapt.content.adaptation.enchanting.upgraded;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingOffers;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingRerolls;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.EnchantOffer;
import art.arcane.adapt.platform.event.inventory.PrepareItemEnchantEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;

/**
 * Offer Reroll, Rune Sight and Bookshelf Attunement fused: every table offer is raised by virtual bookshelf power and
 * shown in full, and a sneak-right-click on the table rerolls the offers, which come back at that raised power.
 */
public class FateWeaver extends SkillUpgrade<FateWeaver.Config> {
  public static final Id ID = Id.adapt("enchanting/upgrade/fate_weaver");

  private final Cooldowns revealThrottle = cooldowns();

  public FateWeaver() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("enchanting_table"));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getShelfPower(level), 1);
    statLore(v, TextFormat.YELLOW, "* ", getLapisCost(level), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PrepareItemEnchantEvent e) {
    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    List<EnchantOffer> offers = e.offers();
    if (level <= 0 || offers == null || offers.isEmpty()) {
      return;
    }

    if (EnchantingOffers.boost(offers, getShelfPower(level))) {
      addStat(p, "enchanting.fate-weaver.offers-raised", 1);
    }

    if (!revealThrottle.isReady(p.uuid(), (long) getConfig().revealThrottleMillis)) {
      return;
    }

    EnchantingOffers.Reveal reveal = EnchantingOffers.reveal(offers, offers.size());
    if (reveal.revealed() <= 0) {
      return;
    }

    revealThrottle.mark(p.uuid());
    HudStatus.show(p, reveal.message());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if ((action != InteractAction.RIGHT_CLICK_BLOCK && action != InteractAction.RIGHT_CLICK_AIR) || e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isSneaking()) {
      return;
    }

    int level = getActiveLevel(p, PlayerRef::isSneaking);
    if (level <= 0 || p.hasCooldown(EnchantingRerolls.ENCHANTING_TABLE)) {
      return;
    }

    BlockRef table = action == InteractAction.RIGHT_CLICK_BLOCK ? e.clickedBlock() : p.targetBlock(5);
    if (table == null || !table.type().equals(EnchantingRerolls.ENCHANTING_TABLE)) {
      return;
    }

    Loc tableTop = table.location().add(0.5D, 1.0D, 0.5D);
    int lapisCost = getLapisCost(level);
    if (p.level() < getConfig().xpLevelCost || !EnchantingRerolls.hasLapis(p, lapisCost)) {
      FxPresets.failFizzle(this, tableTop);
      return;
    }

    EnchantingRerolls.Outcome outcome = EnchantingRerolls.reroll(this, p, lapisCost, getConfig().xpLevelCost);
    if (outcome == EnchantingRerolls.Outcome.UNPAID) {
      FxPresets.failFizzle(this, tableTop);
      return;
    }
    if (outcome == EnchantingRerolls.Outcome.FAILED) {
      return;
    }

    int cooldownTicks = getCooldownTicks(level);
    p.setCooldown(EnchantingRerolls.ENCHANTING_TABLE, cooldownTicks);
    HudStatus.cooldown(p, this, cooldownTicks * 50L);
    e.setCancelled(true);
    addStat(p, "enchanting.fate-weaver.rerolls", 1);
    cue(p, MutationCue.WORK);
    fx(tableTop, FxPriority.TRANSITION)
        .dome(Particles.ENCHANT, 1.4D, 12 + (getShelfPower(level) * 2))
        .particle(Particles.PORTAL, 10, 0, 0.4D, 0, 0.6D, 0.1D);
  }

  int getShelfPower(int level) {
    return Math.max(1, (int) Math.round(getConfig().shelfPowerBase + (level * getConfig().shelfPowerPerLevel)));
  }

  int getLapisCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().lapisCostBase - (level * getConfig().lapisCostReductionPerLevel)));
  }

  int getCooldownTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().cooldownTicksBase - (level * getConfig().cooldownTicksReductionPerLevel)));
  }

  @ConfigDescription("Enchanting-table offers are raised by virtual bookshelf power and shown in full; sneak-right-click the table to reroll them.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Virtual bookshelf power before level scaling.", impact = "Higher values raise offer cost and level more.")
    double shelfPowerBase = 6;
    @ConfigDoc(value = "Virtual bookshelf power added per level.", impact = "Higher values widen the gap between levels.")
    double shelfPowerPerLevel = 3;
    @ConfigDoc(value = "Lapis a reroll costs before level scaling.", impact = "Higher values make rerolls dearer.")
    double lapisCostBase = 3;
    @ConfigDoc(value = "Lapis removed from the reroll cost per level; the cost never drops below 1.", impact = "Higher values make higher levels cheaper.")
    double lapisCostReductionPerLevel = 1;
    @ConfigDoc(value = "Experience levels a reroll costs.", impact = "Higher values make rerolls dearer.")
    int xpLevelCost = 1;
    @ConfigDoc(value = "Cooldown between rerolls before level scaling, in ticks.", impact = "Higher values make rerolls rarer.")
    double cooldownTicksBase = 200;
    @ConfigDoc(value = "Cooldown ticks removed per level; the cooldown never drops below 20.", impact = "Higher values allow faster rerolls at higher levels.")
    double cooldownTicksReductionPerLevel = 50;
    @ConfigDoc(value = "Shortest time between two offer readouts, in milliseconds.", impact = "Lower values refresh the readout more often.")
    double revealThrottleMillis = 400;
  }
}
