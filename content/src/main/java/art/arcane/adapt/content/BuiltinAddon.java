package art.arcane.adapt.content;

import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.content.golem.GolemancyLifecycle;
import art.arcane.adapt.content.golem.command.GolemCommand;
import art.arcane.adapt.content.golem.item.GolemItemGuard;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.node.NodeCodec;
import art.arcane.adapt.content.golem.plinth.PlinthSlotCodec;
import art.arcane.adapt.content.mutation.signature.BuiltinPairs;
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
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.function.Supplier;

/**
 * Every skill Adapt ships, as the addon of namespace {@code adapt}, with its messages. Hosts install it with
 * {@code installBuiltin}.
 */
public final class BuiltinAddon implements AdaptAddon {
  private static final List<Entry> SKILLS = List.of(
      new Entry(Id.adapt("agility"), SkillAgility.class, SkillAgility::new),
      new Entry(Id.adapt("architect"), SkillArchitect.class, SkillArchitect::new),
      new Entry(Id.adapt("axes"), SkillAxes.class, SkillAxes::new),
      new Entry(Id.adapt("blocking"), SkillBlocking.class, SkillBlocking::new),
      new Entry(Id.adapt("chronos"), SkillChronos.class, SkillChronos::new),
      new Entry(Id.adapt("crafting"), SkillCrafting.class, SkillCrafting::new),
      new Entry(Id.adapt("discovery"), SkillDiscovery.class, SkillDiscovery::new),
      new Entry(Id.adapt("enchanting"), SkillEnchanting.class, SkillEnchanting::new),
      new Entry(Id.adapt("herbalism"), SkillHerbalism.class, SkillHerbalism::new),
      new Entry(Id.adapt("hunter"), SkillHunter.class, SkillHunter::new),
      new Entry(Id.adapt("pickaxe"), SkillPickaxes.class, SkillPickaxes::new),
      new Entry(Id.adapt("ranged"), SkillRanged.class, SkillRanged::new),
      new Entry(Id.adapt("rift"), SkillRift.class, SkillRift::new),
      new Entry(Id.adapt("seaborne"), SkillSeaborne.class, SkillSeaborne::new),
      new Entry(Id.adapt("stealth"), SkillStealth.class, SkillStealth::new),
      new Entry(Id.adapt("swords"), SkillSwords.class, SkillSwords::new),
      new Entry(Id.adapt("taming"), SkillTaming.class, SkillTaming::new),
      new Entry(Id.adapt("tragoul"), SkillTragOul.class, SkillTragOul::new),
      new Entry(Id.adapt("unarmed"), SkillUnarmed.class, SkillUnarmed::new),
      new Entry(Id.adapt("excavation"), SkillExcavation.class, SkillExcavation::new),
      new Entry(Id.adapt("brewing"), SkillBrewing.class, SkillBrewing::new),
      new Entry(Id.adapt("nether"), SkillNether.class, SkillNether::new),
      new Entry(Id.adapt("kinetics"), SkillKinetics.class, SkillKinetics::new),
      new Entry(Id.adapt("golemancy"), SkillGolemancy.class, SkillGolemancy::new));

  private static final List<MessageBundle> MESSAGES = List.of(MessageBundle.resources(BuiltinAddon.class, "adapt/lang"));

  /** The built-in skills in registration order. */
  public static List<Entry> skills() {
    return SKILLS;
  }

  /** The built-in messages; config field text comes from the {@code ConfigDoc} annotations. */
  public static List<MessageBundle> messages() {
    return MESSAGES;
  }

  @Override
  public String namespace() {
    return Id.ADAPT;
  }

  @Override
  public void register(ContentRegistrar registrar) {
    MESSAGES.forEach(registrar::messages);
    for (Entry skill : SKILLS) {
      registrar.skill(skill.id(), skill.factory());
    }
    BuiltinPairs.register(registrar);
    GolemRoster.declare(registrar);
    NodeCodec.declare(registrar);
    PlinthSlotCodec.declare(registrar);
    registrar.listener(new GolemancyLifecycle());
    registrar.listener(new GolemItemGuard(GolemItems.recipeBook()));
    registrar.listener(GolemItems.craftCounter());
    registrar.command(new GolemCommand().tree());
  }

  /** One built-in skill: its id, class and constructor. */
  public record Entry(Id id, Class<? extends SimpleSkill<?>> type, Supplier<? extends SimpleSkill<?>> factory) {
  }
}
