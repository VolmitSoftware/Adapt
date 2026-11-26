package art.arcane.adapt.content.adaptation.excavation.upgraded;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.content.adaptation.excavation.ExcavationFinds;
import art.arcane.adapt.content.adaptation.excavation.ExcavationLoot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Treasure Hunter, Grave Digger and Mudlark fused: shovel digs roll archaeology treasure in loose ground, bone loot in
 * earth, and a bonus block from muddy ground, each with its own chance.
 */
public class Gravekeeper extends SkillUpgrade<Gravekeeper.Config> {
  private final ExcavationLoot treasure = new ExcavationLoot();
  private final ExcavationLoot bones = new ExcavationLoot();

  public Gravekeeper() {
    super("upgraded-excavation-gravekeeper");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bone_block"));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getTreasureChance(level), 1), 1);
    statLore(v, Form.pc(getBoneChance(level), 1), 2);
    statLore(v, Form.pc(getBonusChance(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    Id type = e.block().type();
    if (!isShovel(p.inventory().mainHand())
        || !(ExcavationFinds.isTreasureBlock(type) || ExcavationFinds.isGraveSoil(type)
        || ExcavationFinds.isMudlarkBlock(type))) {
      return;
    }

    BlockActionContext context = resolveBlockBreakContext(p, e.block().location());
    if (context == null || MutationPayouts.farmed(e.block())) {
      return;
    }

    int level = context.level();
    ThreadLocalRandom random = ThreadLocalRandom.current();
    Loc center = e.block().center();
    int finds = 0;
    if (ExcavationFinds.isTreasureBlock(type) && random.nextDouble() < getTreasureChance(level)) {
      ExcavationLoot.Roll roll = treasure.roll(getConfig().treasureTable, random);
      if (roll != null) {
        ExcavationFinds.unearthTreasure(this, center, roll);
        finds++;
      }
    }
    if (ExcavationFinds.isGraveSoil(type) && random.nextDouble() < getBoneChance(level)) {
      ExcavationLoot.Roll roll = bones.roll(getConfig().boneTable, random);
      if (roll != null) {
        ExcavationFinds.unearthBones(this, center, roll);
        finds++;
      }
    }
    if (ExcavationFinds.isMudlarkBlock(type) && random.nextDouble() < getBonusChance(level)) {
      ExcavationFinds.dropMudlarkBonus(this, center, ExcavationFinds.mudlarkBonus(type));
      finds++;
    }

    if (finds > 0) {
      addStat(p, "excavation.gravekeeper.finds", finds);
      xpOn(p, e.block(), finds * getConfig().xpPerFind);
      cue(p, MutationCue.WORK);
    }
  }

  double getTreasureChance(int level) {
    return Math.min(1D, getConfig().treasureChanceBase + (level * getConfig().treasureChancePerLevel));
  }

  double getBoneChance(int level) {
    return Math.min(1D, getConfig().boneChanceBase + (level * getConfig().boneChancePerLevel));
  }

  double getBonusChance(int level) {
    return Math.min(1D, getConfig().bonusChanceBase + (level * getConfig().bonusChancePerLevel));
  }

  @ConfigDescription("Shovel digs can turn up treasure, bone loot and bonus muddy blocks.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Treasure chance per sand, gravel, mud or clay dig before level scaling.", impact = "Higher values turn up treasure more often.")
    double treasureChanceBase = 0.03;
    @ConfigDoc(value = "Treasure chance added per level.", impact = "Higher values reward higher levels more.")
    double treasureChancePerLevel = 0.02;
    @ConfigDoc(value = "Bone loot chance per earthen dig before level scaling.", impact = "Higher values turn up bone loot more often.")
    double boneChanceBase = 0.03;
    @ConfigDoc(value = "Bone loot chance added per level.", impact = "Higher values reward higher levels more.")
    double boneChancePerLevel = 0.02;
    @ConfigDoc(value = "Bonus drop chance per clay, mud or soul block dig before level scaling.", impact = "Higher values give bonus drops more often.")
    double bonusChanceBase = 0.10;
    @ConfigDoc(value = "Bonus drop chance added per level.", impact = "Higher values reward higher levels more.")
    double bonusChancePerLevel = 0.07;
    @ConfigDoc(value = "Weighted treasure entries formatted as MATERIAL:weight:min:max.", impact = "Adding entries or raising weights changes which treasure turns up and how often.")
    List<String> treasureTable = new ArrayList<>(ExcavationFinds.TREASURE_TABLE);
    @ConfigDoc(value = "Weighted bone loot entries formatted as MATERIAL:weight:min:max.", impact = "Adding entries or raising weights changes which bone loot turns up and how often.")
    List<String> boneTable = new ArrayList<>(ExcavationFinds.BONE_TABLE);
    @ConfigDoc(value = "Skill XP for each find.", impact = "Higher values reward lucky digs more.")
    double xpPerFind = 6;
  }
}
