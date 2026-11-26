package art.arcane.adapt.content.adaptation.crafting.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.Map;
import java.util.UUID;

/** A sharpened edge: the player's next hit within the window ignores part of the target's armor. */
public class CraftingFightStrike extends StrikeBlock<CraftingFightStrike.Config> {
  private static final double ARMOR_CAP = 20D;
  private static final double ARMOR_SCALE = 25D;
  private static final double MIN_ARMOR_SHARE = 0.2D;

  private final Map<UUID, Edge> edges = playerState();

  public CraftingFightStrike() {
    super("block-crafting-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("anvil"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    Edge edge = new Edge(use.scaled(getConfig().armorPierceAtFirstLevel, getConfig().armorPierceAtTopLevel),
        System.currentTimeMillis() + getConfig().windowMillis);
    Tasks.runEntity(player, () -> edges.put(player.uuid(), edge), 1);
    fx(player.location().add(0D, 1D, 0D), FxPriority.COMBAT)
        .particle(Particles.CRIT, 6, 0D, 0D, 0D, 0.3D, 0.05D);
    return Outcome.INSTANT;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    EntityRef source = e.causingEntity() == null ? e.damager() : e.causingEntity();
    if (!(source instanceof PlayerRef player) || !(e.entity() instanceof LivingRef target)) {
      return;
    }
    Edge edge = edges.remove(player.uuid());
    if (edge == null || System.currentTimeMillis() > edge.until() || !hasActiveAdaptation(player)
        || !target.hasAttribute(Attributes.ARMOR)) {
      return;
    }
    double toughness = target.hasAttribute(Attributes.ARMOR_TOUGHNESS) ? target.attributeValue(Attributes.ARMOR_TOUGHNESS) : 0D;
    e.setDamage(e.damage() * pierced(e.damage(), target.attributeValue(Attributes.ARMOR), toughness, edge.share()));
    addStat(player, "crafting.mutated.armor-pierced", 1);
  }

  /** How much more damage gets through when {@code share} of {@code armor} is ignored, by the vanilla armor formula. */
  static double pierced(double damage, double armor, double toughness, double share) {
    double through = through(damage, armor, toughness);
    return through <= 0D ? 1D : through(damage, armor * (1D - Math.max(0D, Math.min(1D, share))), toughness) / through;
  }

  private static double through(double damage, double armor, double toughness) {
    double effective = Math.max(armor * MIN_ARMOR_SHARE, armor - (damage / (2D + (toughness / 4D))));
    return 1D - (Math.min(ARMOR_CAP, effective) / ARMOR_SCALE);
  }

  private record Edge(double share, long until) {
  }

  @ConfigDescription("Your next hit within 5 s ignores 20% of the target's armor.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Share of the target's armor the next hit ignores at level 1.", impact = "Higher values let more damage through armor.")
    double armorPierceAtFirstLevel = 0.2D;
    @ConfigDoc(value = "Share of the target's armor the next hit ignores at the top level.", impact = "Higher values let more damage through armor.")
    double armorPierceAtTopLevel = 0.4D;
    @ConfigDoc(value = "How long the sharpened edge waits for a hit, in milliseconds.", impact = "Higher values give more time to land the hit.")
    long windowMillis = 5_000L;
  }
}
