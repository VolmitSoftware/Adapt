package art.arcane.adapt.content.adaptation.tragoul.upgraded;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.DeathSaveCooldown;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.damage.PlayerDeathSave;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.mutation.upgrade.UpgradeLoans;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.content.adaptation.tragoul.TragoulBlood;
import art.arcane.adapt.content.adaptation.tragoul.TragoulMarrow;
import art.arcane.adapt.content.adaptation.tragoul.TragoulPreferences;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSpirit;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Lich's Bargain, built on Marrow Armor and Last Rites: bones absorb part of each heavy hit, and a killing blow leaves
 * you at 1 HP, briefly hidden, while your skeletal servants rise around you for free. Sneaking and right-clicking with
 * bones raises a servant on demand, for bones and a little health that blood pays first. Servants come from Skeletal
 * Servant's own pack, so they drop nothing, follow your stance and count as Golemancy bone frames where you have that
 * link; both ways of raising share one cap.
 */
public class Deathless extends SkillUpgrade<Deathless.Config> implements PlayerDeathSave, TragoulSkeletalServant.HandRaiser {
  private static final Id BONE = Id.minecraft("bone");
  private static final double RING_RADIUS = 1.5D;

  private final Cooldowns absorbCooldowns = cooldowns();
  private final Cooldowns raiseCooldowns = cooldowns();
  private final TragoulMarrow marrow = new TragoulMarrow(this, cooldowns());

  public Deathless() {
    super("upgraded-tragoul-deathless");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("skeleton_skull"));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, CommonPreferences.DEATH_SAVE_PRIORITY, TragoulPreferences.BONES);
  }

  @Override
  public int deathSavePriority(PlayerRef player) {
    return preference(player, CommonPreferences.DEATH_SAVE_PRIORITY).ordinal();
  }

  @Override
  public int defaultOrder() {
    return 0;
  }

  @Override
  public boolean raisesByHand(PlayerRef player) {
    return getActiveLevel(player) > 0;
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getAbsorbPercent(level), 0), 1);
    statLore(v, getServantCap(level), 2);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getCooldownMillis(level), 1), 3);
    statLore(v, Form.duration(getServantTicks(level) * 50D, 1), 4);
    statLore(v, C.YELLOW, "* ", getConfig().bonesPerServant, 5);
    statLore(v, C.RED, "* ", Form.f(getConfig().healthPerServant, 1), 6);
  }

  @Handle(priority = EventPriority.HIGH)
  public void onAbsorb(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || e.finalDamage() < getConfig().minDamageToAbsorb) {
      return;
    }
    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      long cooldownMillis = (long) getConfig().absorbCooldownMillis;
      if (level <= 0 || !absorbCooldowns.isReady(p.uuid(), cooldownMillis)) {
        return;
      }
      if (marrow.absorb(p, e, getAbsorbPercent(level), (double absorbed) -> {
        addStat(p, "tragoul.deathless.damage-absorbed", absorbed);
      })) {
        absorbCooldowns.mark(p.uuid(), cooldownMillis);
        cue(p, MutationCue.FIGHT);
      }
    });
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    tryPreventDeath(e);
  }

  @Override
  public void tryPreventDeath(EntityDamageEvent e) {
    if (e.isCancelled() || !(e.entity() instanceof PlayerRef p) || e.finalDamage() < p.health()) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0 || p.isDead()) {
        return;
      }

      long now = System.currentTimeMillis();
      long cooldownMillis = getCooldownMillis(level);
      if (!DeathSaveCooldown.LAST_RITES.activate(p, now, cooldownMillis)) {
        return;
      }
      AbilityGlyphs.cooldown(p.uuid(), this, now, cooldownMillis);

      TragoulSpirit.enter(this, p, e, getConfig().coverTicks, getConfig().resistanceAmplifier, getConfig().targetClearRadius);
      int risen = raiseAround(p, level, getServantCap(level));
      cue(p, MutationCue.BOON);
      addStat(p, "tragoul.deathless.deaths-defied", 1);
      if (risen > 0) {
        addStat(p, "tragoul.deathless.servants-raised", risen);
      }
    });
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    if (!p.isSneaking() || e.hand() != Hand.MAIN_HAND || e.item() == null || !BONE.equals(e.item().type())
        || (e.action() != InteractAction.RIGHT_CLICK_AIR && e.action() != InteractAction.RIGHT_CLICK_BLOCK)) {
      return;
    }
    withAdaptedPlayer(p, () -> {
      int level = getActiveLevel(p);
      if (level <= 0 || !TragoulSkeletalServant.allowsSummonInteraction(e)) {
        return;
      }
      raiseByHand(p, level);
    });
  }

  private void raiseByHand(PlayerRef p, int level) {
    TragoulSkeletalServant pack = pack();
    long cooldownMillis = (long) getConfig().raiseCooldownMillis;
    if (pack == null || !raiseCooldowns.isReady(p.uuid(), cooldownMillis)) {
      return;
    }
    if (pack.servants(p.uuid()).size() + ServantFrame.legion(p.uuid()) >= getServantCap(level)) {
      refuse(p, 1.2F);
      return;
    }
    int bones = Math.max(0, getConfig().bonesPerServant);
    boolean creative = p.gameMode() == GameMode.CREATIVE;
    ItemRef bone = AdaptCore.platform().host().items().create(BONE);
    if (!creative && !p.inventory().containsAtLeast(bone, preference(p, TragoulPreferences.BONES).required(bones))) {
      refuse(p, 0.6F);
      return;
    }
    double health = creative ? 0D : Math.max(0D, getConfig().healthPerServant);
    if (Math.max(0D, p.health() - 1D) + TragoulBlood.blood(this, p) < health) {
      refuse(p, 0.8F);
      return;
    }
    if (!creative && bones > 0 && !payItemCost(p, "raise", bone, bones, () -> {
      p.inventory().removeSimilar(bone, bones);
      return true;
    })) {
      refuse(p, 0.6F);
      return;
    }
    if (pack.raise(p, p.location(), servantLevel(pack, level), getServantTicks(level)) == null) {
      if (!creative && bones > 0) {
        p.inventory().addItem(AdaptCore.platform().host().items().create(BONE, bones));
      }
      return;
    }
    TragoulBlood.payHealth(this, p, health);
    raiseCooldowns.mark(p.uuid(), cooldownMillis);
    cue(p, MutationCue.WORK);
    addStat(p, "tragoul.deathless.servants-raised", 1);
  }

  private int raiseAround(PlayerRef p, int level, int cap) {
    TragoulSkeletalServant pack = pack();
    if (pack == null) {
      return 0;
    }
    int open = cap - pack.servants(p.uuid()).size() - ServantFrame.legion(p.uuid());
    int risen = 0;
    Loc center = p.location();
    for (int index = 0; index < open; index++) {
      double angle = (Math.PI * 2D * index) / Math.max(1, open);
      Loc at = center.add(Math.cos(angle) * RING_RADIUS, 0D, Math.sin(angle) * RING_RADIUS);
      if (pack.raise(p, at, servantLevel(pack, level), getServantTicks(level)) != null) {
        risen++;
      }
    }
    return risen;
  }

  private void refuse(PlayerRef p, float pitch) {
    sfx(p.location(), Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.8F, pitch);
  }

  private int servantLevel(TragoulSkeletalServant pack, int level) {
    return UpgradeLoans.lent(pack, this, level);
  }

  private @Nullable TragoulSkeletalServant pack() {
    if (getSkill() == null) {
      return null;
    }
    for (Adaptation<?> adaptation : getSkill().getAdaptations()) {
      if (adaptation instanceof TragoulSkeletalServant pack) {
        return pack;
      }
    }
    return null;
  }

  double getAbsorbPercent(int level) {
    return Math.min(getConfig().maxAbsorbPercent, Math.max(0D, getConfig().absorbPercentBase + (level * getConfig().absorbPercentPerLevel)));
  }

  int getServantCap(int level) {
    return Math.max(0, getConfig().servantsBase + (level * getConfig().servantsPerLevel));
  }

  int getServantTicks(int level) {
    return Math.max(20, getConfig().servantTicksBase + (level * getConfig().servantTicksPerLevel));
  }

  long getCooldownMillis(int level) {
    return (long) Math.max(getConfig().minimumCooldownMillis,
        getConfig().cooldownMillisBase - (level * getConfig().cooldownMillisPerLevel));
  }

  @ConfigDescription("Bones absorb heavy hits; a killing blow leaves you at 1 HP as your servants rise, and bones raise servants on demand.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Smallest hit, after armor, that spends a bone.", impact = "Higher values save bones for real hits.")
    double minDamageToAbsorb = 2.0;
    @ConfigDoc(value = "Fraction of a hit a bone absorbs before level scaling.", impact = "Higher values absorb more per bone.")
    double absorbPercentBase = 0.25;
    @ConfigDoc(value = "Absorbed fraction added per level.", impact = "Higher values reward higher levels more.")
    double absorbPercentPerLevel = 0.10;
    @ConfigDoc(value = "Highest absorbed fraction at any level.", impact = "Caps the absorption so hits always land.")
    double maxAbsorbPercent = 0.65;
    @ConfigDoc(value = "Time between two absorbed hits, in milliseconds.", impact = "Higher values stop bursts from draining your bones.")
    double absorbCooldownMillis = 2500;
    @ConfigDoc(value = "Most living servants before level scaling, raised by the save and by hand together.", impact = "Higher values field a larger guard.")
    int servantsBase = 0;
    @ConfigDoc(value = "Servants added to the cap per level.", impact = "Higher values reward higher levels more.")
    int servantsPerLevel = 1;
    @ConfigDoc(value = "Servant lifetime in ticks before level scaling.", impact = "Higher values keep servants longer.")
    int servantTicksBase = 300;
    @ConfigDoc(value = "Servant lifetime in ticks added per level.", impact = "Higher values reward higher levels more.")
    int servantTicksPerLevel = 300;
    @ConfigDoc(value = "Bones spent for each servant raised by hand.", impact = "Higher values make raising by hand cost more.")
    int bonesPerServant = 4;
    @ConfigDoc(value = "Health spent for each servant raised by hand, blood first; it never takes you below 1 HP.", impact = "Higher values make raising by hand cost more life.")
    double healthPerServant = 2.0;
    @ConfigDoc(value = "Time between two servants raised by hand, in milliseconds.", impact = "Higher values slow raising by hand.")
    double raiseCooldownMillis = 1500;
    @ConfigDoc(value = "Ticks you stay hidden and resistant after a defied killing blow.", impact = "Higher values give your servants longer to cover you.")
    int coverTicks = 60;
    @ConfigDoc(value = "Resistance amplifier while hidden after a defied killing blow.", impact = "Higher values reduce more damage while servants rise.")
    int resistanceAmplifier = 3;
    @ConfigDoc(value = "Radius in blocks in which mobs hunting you lose their target.", impact = "Higher values shake off pursuers from farther away.")
    double targetClearRadius = 12;
    @ConfigDoc(value = "Death-save cooldown before level scaling, in milliseconds. Shared with Last Rites.", impact = "Higher values make defying death rarer.")
    double cooldownMillisBase = 420000;
    @ConfigDoc(value = "Cooldown removed per level, in milliseconds.", impact = "Higher values let higher levels defy death more often.")
    double cooldownMillisPerLevel = 60000;
    @ConfigDoc(value = "Shortest death-save cooldown at any level, in milliseconds.", impact = "Lower values allow more frequent saves.")
    double minimumCooldownMillis = 30000;
  }
}
