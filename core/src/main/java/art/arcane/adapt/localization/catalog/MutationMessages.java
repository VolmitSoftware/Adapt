package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

/** Text of the Mutate menus and the mutation toast. */
public final class MutationMessages {
  public static final TextKey MUTATE = TextKey.of("gui.mutation.mutate", "Mutate");
  public static final TextKey PICK_PARTNER = TextKey.of("gui.mutation.pick_partner", "Pick a partner");
  public static final TextKey PICK_LEAD = TextKey.of("gui.mutation.pick_lead", "Pick a skill");
  public static final TextKey LEVEL = TextKey.of("gui.mutation.level", "Level {level}");
  public static final TextKey KNOWLEDGE = TextKey.of("gui.mutation.knowledge", "{knowledge} knowledge");
  public static final TextKey LEAD = TextKey.of("gui.mutation.lead", "Lead: {skill}");
  public static final TextKey SWAP_LEAD = TextKey.of("gui.mutation.swap_lead", "Swap lead");
  public static final TextKey ADAPTATIONS = TextKey.of("gui.mutation.adaptations", "Adaptations");
  public static final TextKey LOST = TextKey.of("gui.mutation.lost", "Lost");
  public static final TextKey LOST_LEVELS = TextKey.of("gui.mutation.lost_levels", "{count} {skill} levels");
  public static final TextKey LOST_KNOWLEDGE = TextKey.of("gui.mutation.lost_knowledge", "{count} {skill} knowledge");
  public static final TextKey LOST_ADAPTATION = TextKey.of("gui.mutation.lost_adaptation", "{adaptation} {level}");
  public static final TextKey LOST_PERMANENT_ADAPTATION = TextKey.of("gui.mutation.lost_permanent_adaptation",
      "{adaptation} {level} (permanent, still unlearned)");
  public static final TextKey NOTHING_LOST = TextKey.of("gui.mutation.nothing_lost", "Nothing");
  public static final TextKey CONFIRM_AGAIN = TextKey.of("gui.mutation.confirm_again", "Confirm: permanent");
  public static final TextKey UNMUTATE = TextKey.of("gui.mutation.unmutate", "Unmutate");
  public static final TextKey UNMUTATE_CONFIRM = TextKey.of("gui.mutation.unmutate_confirm", "Confirm unmutate");
  public static final TextKey LEAD_ROLE = TextKey.of("gui.mutation.lead_role", "Lead");
  public static final TextKey PARTNER_ROLE = TextKey.of("gui.mutation.partner_role", "Partner");
  public static final TextKey GAINS = TextKey.of("gui.mutation.gains", "Gains");
  public static final TextKey LOSES = TextKey.of("gui.mutation.loses", "Loses");
  public static final TextKey HOLD = TextKey.of("gui.mutation.hold", "Hold to mutate");
  public static final TextKey PERMANENT = TextKey.of("gui.mutation.permanent", "This cannot be undone");
  public static final TextKey LEVELS = TextKey.of("gui.mutation.levels", "{count} levels");
  public static final TextKey TOOL = TextKey.of("gui.mutation.tool", "Tool");
  public static final TextKey NEEDS_LEVEL = TextKey.of("gui.mutation.needs_level", "Needs level {level}");
  public static final TextKey IN_MUTATION = TextKey.of("gui.mutation.in_mutation", "Already mutated");
  public static final TextKey AVAILABLE = TextKey.of("gui.mutation.available", "Mutation available");
  public static final TextKey AVAILABLE_QUIP = TextKey.of("gui.mutation.available_quip", "Two of your skills are getting ideas");
  public static final TextKey UPGRADE_QUIP_1 = TextKey.of("gui.mutation.upgrade_quip_1", "Same trick, bigger teeth");
  public static final TextKey UPGRADE_QUIP_2 = TextKey.of("gui.mutation.upgrade_quip_2", "An old trick learns a new one");
  public static final TextKey MUTATED_QUIP_1 = TextKey.of("gui.mutation.mutated_quip_1", "Two skills, one strange new trick");
  public static final TextKey MUTATED_QUIP_2 = TextKey.of("gui.mutation.mutated_quip_2", "Nature did not plan for this");
  public static final TextKey SIGNATURE_QUIP_1 = TextKey.of("gui.mutation.signature_quip_1", "Nobody else can do this one");
  public static final TextKey SIGNATURE_QUIP_2 = TextKey.of("gui.mutation.signature_quip_2", "Uniquely, unmistakably yours");

  private MutationMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(MUTATE);
    builder.add(PICK_PARTNER);
    builder.add(PICK_LEAD);
    builder.add(LEVEL);
    builder.add(KNOWLEDGE);
    builder.add(LEAD);
    builder.add(SWAP_LEAD);
    builder.add(ADAPTATIONS);
    builder.add(LOST);
    builder.add(LOST_LEVELS);
    builder.add(LOST_KNOWLEDGE);
    builder.add(LOST_ADAPTATION);
    builder.add(LOST_PERMANENT_ADAPTATION);
    builder.add(NOTHING_LOST);
    builder.add(CONFIRM_AGAIN);
    builder.add(UNMUTATE);
    builder.add(UNMUTATE_CONFIRM);
    builder.add(LEAD_ROLE);
    builder.add(PARTNER_ROLE);
    builder.add(GAINS);
    builder.add(LOSES);
    builder.add(HOLD);
    builder.add(PERMANENT);
    builder.add(LEVELS);
    builder.add(TOOL);
    builder.add(NEEDS_LEVEL);
    builder.add(IN_MUTATION);
    builder.add(AVAILABLE);
    builder.add(AVAILABLE_QUIP);
    builder.add(UPGRADE_QUIP_1);
    builder.add(UPGRADE_QUIP_2);
    builder.add(MUTATED_QUIP_1);
    builder.add(MUTATED_QUIP_2);
    builder.add(SIGNATURE_QUIP_1);
    builder.add(SIGNATURE_QUIP_2);
  }
}
