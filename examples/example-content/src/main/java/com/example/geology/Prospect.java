package com.example.geology;

import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.List;

/** Active: sneak and right-click stone with a pickaxe to reveal nearby ore, then wait out the cooldown. */
public final class Prospect extends SimpleAdaptation<Prospect.Config> {
  public static final Id ID = Id.of(GeologyAddon.NAMESPACE, "geology/prospect");
  public static final String USES_STAT = "example.geology.prospects";

  private final Cooldowns cooldowns = cooldowns();

  public Prospect() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spyglass"));
  }

  @Override
  public void addStats(int level, StatLines lines) {
    statLore(lines, Format.f(radius(level, false)), 1);
    statLore(lines, Format.duration(cooldown(level), 1), 2);
  }

  @Handle
  public void on(PlayerInteractEvent event) {
    PlayerRef player = event.player();
    if (event.action() != InteractAction.RIGHT_CLICK_BLOCK || !player.isSneaking() || !Rocks.isPickaxe(event.item())) {
      return;
    }
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    long cooldown = cooldown(level);
    if (!cooldowns.isReady(player.uuid(), cooldown)) {
      return;
    }
    cooldowns.mark(player.uuid());
    HudStatus.cooldown(player, this, cooldown);
    List<BlockRef> ores = scan(event.clickedBlock(), radius(level, GeodeLens.holds(player)));
    for (BlockRef found : ores.subList(0, Math.min(ores.size(), getConfig().maxMarkers))) {
      fx(found.location().add(0.5D, 0.5D, 0.5D), FxPriority.GAMEPLAY).only(player).burst(Particles.WAX_OFF, 6, 0.3D);
    }
    fx(player.location(), FxPriority.GAMEPLAY).sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, ores.isEmpty() ? 0.6F : 1.4F);
    HudStatus.show(player, this, text("hud.found").with("count", ores.size()));
    addStat(player, USES_STAT, 1D);
  }

  private static List<BlockRef> scan(BlockRef center, int radius) {
    WorldRef world = center.world();
    List<BlockRef> ores = new ArrayList<>();
    for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
        for (int z = -radius; z <= radius; z++) {
          BlockRef block = world.blockAt(center.x() + x, center.y() + y, center.z() + z);
          if (Rocks.isOre(block.type())) {
            ores.add(block);
          }
        }
      }
    }
    return ores;
  }

  private int radius(int level, boolean lens) {
    int radius = getConfig().baseRadius + getConfig().radiusPerLevel * level;
    return lens ? radius * 2 : radius;
  }

  private long cooldown(int level) {
    return Math.max(getConfig().minimumCooldownMillis, getConfig().cooldownMillis - getConfig().cooldownCutPerLevel * level);
  }

  public static final class Config extends AdaptationConfig {
    @ConfigDoc("Scan radius in blocks before levels.")
    public int baseRadius = 2;
    @ConfigDoc("Scan radius added per level.")
    public int radiusPerLevel = 1;
    @ConfigDoc("Cooldown in milliseconds before levels.")
    public long cooldownMillis = 30000L;
    @ConfigDoc("Milliseconds taken off the cooldown per level.")
    public long cooldownCutPerLevel = 4000L;
    @ConfigDoc("Shortest possible cooldown in milliseconds.")
    public long minimumCooldownMillis = 8000L;
    @ConfigDoc("Most ore blocks marked with particles per use.")
    public int maxMarkers = 24;

    public Config() {
      baseCost = 3;
      initialCost = 2;
      costFactor = 0.5D;
      maxLevel = 5;
    }
  }
}
