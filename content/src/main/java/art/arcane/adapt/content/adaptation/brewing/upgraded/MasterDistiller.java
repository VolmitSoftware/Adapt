package art.arcane.adapt.content.adaptation.brewing.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.world.BlockOwners;
import art.arcane.adapt.content.adaptation.brewing.BrewingDuration;
import art.arcane.adapt.content.adaptation.brewing.BrewingHeat;
import art.arcane.adapt.content.adaptation.brewing.BrewingLingering;
import art.arcane.adapt.content.adaptation.brewing.BrewingSuperHeated;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryMoveItemEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.UUID;

/**
 * Lingering Brew and Super Heated Brew fused: the owner's stands brew faster, more so beside fire and lava, and every
 * potion they brew lasts twice as long. Hands out the Alchemist's Bandolier, the potion source for the quarter-dose
 * blocks.
 */
public class MasterDistiller extends SkillUpgrade<MasterDistiller.Config> {
  public static final Id ID = Id.adapt("brewing/upgrade/master_distiller");

  private static final Id BREWING_STAND = Id.minecraft("brewing_stand");
  private static final Rgb AMBER = Rgb.of(0xE8A33D);
  private static final int HOTBAR = 9;
  private final BrewingHeat heat = new BrewingHeat(this, BrewingSuperHeated::heatAmbience);
  private final AlchemistsBandolier bandolier;

  public MasterDistiller() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("brewing_stand"));
    setInterval(253);
    bandolier = registerTool(new AlchemistsBandolier(this));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, BrewingLingering.LORE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getBrewSpeed(level), 0), 1);
    statLore(v, Format.pc(getConfig().lavaBoost, 0), 2);
    statLore(v, Format.pc(getConfig().durationMultiplier, 0), 3);
    statLore(v, getBandolierSlots(level), 4);
  }

  @Handle(priority = EventPriority.NORMAL)
  public void on(PlayerItemHeldEvent e) {
    PlayerRef player = e.player();
    if (!player.isSneaking() || !bandolier.is(player.inventory().item(e.previousSlot()))
        || bandolier.level(player) <= 0) {
      return;
    }
    e.setCancelled(true);
    int forward = Math.floorMod(e.newSlot() - e.previousSlot(), HOTBAR);
    bandolier.cycle(player, e.previousSlot(), forward <= HOTBAR / 2 ? 1 : -1);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PrepareItemCraftEvent e) {
    for (ItemRef item : e.matrix()) {
      if (AlchemistsBandolier.loaded(item)) {
        e.setResult(AdaptApi.platform().host().items().empty());
        return;
      }
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(InventoryMoveItemEvent e) {
    heat.watch(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(InventoryClickEvent e) {
    heat.watch(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BrewEvent e) {
    BlockRef block = e.block();
    heat.keepIfBrewing(block);
    if (!block.type().equals(BREWING_STAND)) {
      return;
    }
    UUID owner = BlockOwners.owner(block).orElse(null);
    if (owner == null || brewerLevel(owner) <= 0) {
      return;
    }

    PlayerRef player = AdaptApi.platform().host().player(owner);
    int extended = BrewingDuration.extendAll(e.results(), 0D, getConfig().durationMultiplier,
        getConfig().useCustomLore && player != null && preferenceEnabled(player, BrewingLingering.LORE));
    if (extended <= 0) {
      return;
    }
    AdaptApi.players().find(owner).ifPresent((PlayerProfile profile) -> profile.addStat("brewing.master-distiller.potions-extended", extended));
    fx(block.location().add(0.5D, 0.6D, 0.5D), FxPriority.TRANSITION)
        .dustRing(AMBER, 0.6D, 12, 1.0F)
        .particle(Particles.DRAGON_BREATH, 8, 0, 0.2D, 0, 0.3D, 0.01D);
    if (player != null) {
      cue(player, MutationCue.WORK);
    }
  }

  @Override
  public boolean hasTickDemand() {
    return !heat.isIdle();
  }

  @Override
  public void onTick() {
    heat.tick(this::brewerLevel, (int level, double fire, double lava) -> getWarpTicks(level, fire, lava));
  }

  int getWarpTicks(int level, double fire, double lava) {
    return BrewingHeat.computeWarpTicks(getInterval(), getBrewSpeed(level), 0D, 1D, 0D)
        + BrewingHeat.computeWarpTicks(getInterval(), getConfig().fireBoost, getConfig().lavaBoost, fire, lava);
  }

  private int brewerLevel(UUID owner) {
    PlayerProfile brewer = AdaptApi.players().find(owner).orElse(null);
    int level = brewer == null ? 0 : getLevel(brewer);
    return level > 0 && isPlayerEnabled(brewer.player()) ? level : 0;
  }

  int getBandolierSlots(int level) {
    int top = Math.max(1, getMaxLevel() - 1);
    double share = Math.max(0, level - 1) / (double) top;
    return (int) (getConfig().bandolierSlotsAtFirstLevel
        + (share * (getConfig().bandolierSlotsAtTopLevel - getConfig().bandolierSlotsAtFirstLevel)));
  }

  double getBrewSpeed(int level) {
    return getConfig().brewSpeedBase + (level * getConfig().brewSpeedPerLevel);
  }

  @ConfigDescription("Your brewing stands brew faster, more so beside fire and lava, their potions last twice as long, and you carry potions in a bandolier.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Extra brewing speed before level scaling, as a fraction of normal speed.", impact = "Higher values finish every brew sooner.")
    double brewSpeedBase = 0.15;
    @ConfigDoc(value = "Extra brewing speed added per level, as a fraction of normal speed.", impact = "Higher values reward higher levels more.")
    double brewSpeedPerLevel = 0.15;
    @ConfigDoc(value = "Extra brewing speed for each fire block touching the stand.", impact = "Higher values make fire speed up brewing more.")
    double fireBoost = 0.19;
    @ConfigDoc(value = "Extra brewing speed for each lava block touching the stand.", impact = "Higher values make lava speed up brewing more.")
    double lavaBoost = 0.92;
    @ConfigDoc(value = "Duration multiplier for every lasting effect of a brewed potion.", impact = "Higher values make brewed potions last longer.")
    double durationMultiplier = 2.0;
    @ConfigDoc(value = "Write the extended durations into the potion's lore.", impact = "True shows the new durations on the item.")
    boolean useCustomLore = true;
    @ConfigDoc(value = "Potions the Alchemist's Bandolier holds at the first level.", impact = "Higher values carry more potions early.")
    int bandolierSlotsAtFirstLevel = 3;
    @ConfigDoc(value = "Potions the Alchemist's Bandolier holds at the top level.", impact = "Higher values carry more potions at the top level.")
    int bandolierSlotsAtTopLevel = 6;
  }
}
