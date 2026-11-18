package art.arcane.adapt.content;

import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.content.skill.SkillBlocking;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.content.skill.SkillDiscovery;
import art.arcane.adapt.content.skill.SkillEnchanting;
import art.arcane.adapt.content.skill.SkillExcavation;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.content.skill.SkillKinetics;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.content.skill.SkillRanged;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.content.skill.SkillSeaborne;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.content.skill.SkillTragOul;
import art.arcane.adapt.content.skill.SkillUnarmed;

import java.util.List;

/**
 * Every skill Adapt ships, in registration order (the order of the skills GUI and of skill commands). Every platform
 * registers exactly these; the registry instantiates each.
 */
public final class CoreContent {
  private CoreContent() {
  }

  public static List<Class<? extends Skill<?>>> skillTypes() {
    return List.of(
        SkillAgility.class,
        SkillArchitect.class,
        SkillAxes.class,
        SkillBlocking.class,
        SkillChronos.class,
        SkillCrafting.class,
        SkillDiscovery.class,
        SkillEnchanting.class,
        SkillHerbalism.class,
        SkillHunter.class,
        SkillPickaxes.class,
        SkillRanged.class,
        SkillRift.class,
        SkillSeaborne.class,
        SkillStealth.class,
        SkillSwords.class,
        SkillTaming.class,
        SkillTragOul.class,
        SkillUnarmed.class,
        SkillExcavation.class,
        SkillBrewing.class,
        SkillNether.class,
        SkillKinetics.class,
        SkillGolemancy.class);
  }
}
