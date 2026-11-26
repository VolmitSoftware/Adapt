package art.arcane.adapt.content.adaptation.crafting.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.content.adaptation.crafting.Compaction;
import art.arcane.adapt.content.adaptation.crafting.CraftingCompactor;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

/**
 * Deconstruction, Ore Reconstruction and Compactor fused: break items and blocks into parts, recraft ores, and compact
 * full stacks by sneaking and swapping hands anywhere, with no Crafting Table in sight.
 */
public class Unmaker extends SkillUpgrade<Unmaker.Config> {
  private static final String COMPACTOR = "crafting-compactor";

  private final Compaction compaction = new Compaction(this);
  private final Cooldowns cooldowns = cooldowns();

  public Unmaker() {
    super("upgraded-crafting-unmaker");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("grindstone"));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, CraftingCompactor.MATERIALS, CraftingCompactor.BATCH, CraftingCompactor.RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    grantedLore(v, level);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 2);
  }

  @Override
  protected boolean runsOnGrant(Adaptation<?> original) {
    return !COMPACTOR.equals(original.getName());
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.NORMAL)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef p = e.player();
    if (!p.isSneaking() || p.hasOpenContainer()) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0 || !cooldowns.isReady(p.uuid(), getCooldownMillis(level))) {
      return;
    }

    int blocks = compaction.compact(p);
    if (blocks > 0) {
      e.setCancelled(true);
      cooldowns.mark(p.uuid(), getCooldownMillis(level));
      addStat(p, "crafting.unmaker.blocks-compacted", blocks);
      cue(p, MutationCue.WORK);
    }
  }

  long getCooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillisBase - (level * getConfig().cooldownMillisPerLevel));
  }

  @ConfigDescription("Break items and blocks into parts, recraft ores, and compact full stacks anywhere.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Time between compactions before level scaling, in milliseconds.", impact = "Higher values make compacting slower to repeat.")
    long cooldownMillisBase = 4000;
    @ConfigDoc(value = "Cooldown removed per level, in milliseconds.", impact = "Higher values let higher levels compact more often.")
    long cooldownMillisPerLevel = 1000;
  }
}
