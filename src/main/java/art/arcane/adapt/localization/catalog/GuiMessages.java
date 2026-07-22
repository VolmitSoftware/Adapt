package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class GuiMessages {
  public static final TextKey FIRST = TextKey.of("gui.common.first", "First");
  public static final TextKey PREVIOUS = TextKey.of("gui.common.previous", "Previous");
  public static final TextKey NEXT = TextKey.of("gui.common.next", "Next");
  public static final TextKey LAST = TextKey.of("gui.common.last", "Last");
  public static final TextKey BACK = TextKey.of("gui.common.back", "Back");
  public static final TextKey CONFIRM = TextKey.of("gui.common.confirm", "Confirm");
  public static final TextKey CANCEL = TextKey.of("gui.common.cancel", "Cancel");
  public static final TextKey APPLY_CHANGE = TextKey.of("gui.common.apply_change", "Apply this change?");
  public static final TextKey LEFT_CLICK_PREVIOUS = TextKey.of("gui.common.left_click_previous", "Left click: previous page");
  public static final TextKey LEFT_CLICK_NEXT = TextKey.of("gui.common.left_click_next", "Left click: next page");
  public static final TextKey RIGHT_CLICK_JUMP_BACK = TextKey.of("gui.common.right_click_jump_back", "Right click: jump -{pages} pages");
  public static final TextKey RIGHT_CLICK_JUMP_FORWARD = TextKey.of("gui.common.right_click_jump_forward", "Right click: jump +{pages} pages");
  public static final TextKey PAGE_OF = TextKey.of("gui.common.page_of", "Page {page}/{pages}");
  public static final TextKey SHOWING_RANGE = TextKey.of("gui.common.showing_range", "Showing {from}-{to} of {total}");
  public static final TextKey PAGE_SHOWING_RANGE = TextKey.of("gui.common.page_showing_range", "Page {page}/{pages} • Showing {from}-{to} of {total}");
  public static final TextKey FIRST_PAGE = TextKey.of("gui.common.first_page", "First page");
  public static final TextKey NO_PREVIOUS_PAGE = TextKey.of("gui.common.no_previous_page", "No previous page");
  public static final TextKey NO_NEXT_PAGE = TextKey.of("gui.common.no_next_page", "No next page");
  public static final TextKey LAST_PAGE = TextKey.of("gui.common.last_page", "Last page");
  public static final TextKey BACK_TO_SKILLS = TextKey.of("gui.common.back_to_skills", "Back to Skills");
  public static final TextKey BACK_TO_MUTATIONS = TextKey.of("gui.common.back_to_mutations", "Back to Mutations");
  public static final TextKey SKILLS = TextKey.of("gui.common.skills", "Skills");
  public static final TextKey ADAPTATIONS = TextKey.of("gui.common.adaptations", "Adaptations");
  public static final TextKey LEVELS = TextKey.of("gui.common.levels", "Levels");
  public static final TextKey NO_SKILLS_AVAILABLE = TextKey.of("gui.skills.none_available", "No skills available");
  public static final TextKey NO_ELIGIBLE_SKILLS = TextKey.of("gui.skills.none_eligible", "No eligible skills were found for this player.");
  public static final TextKey NO_ADAPTATIONS_AVAILABLE = TextKey.of("gui.adaptations.none_available", "No adaptations available");
  public static final TextKey PERMANENT_LEARN_CONFIRM = TextKey.of("gui.adaptations.permanent_learn_confirm", "Click again to confirm permanent learn");
  public static final TextKey PERMANENT_LEARN_CONFIRM_NOW = TextKey.of("gui.adaptations.permanent_learn_confirm_now", "Click again now to confirm permanent learn.");
  public static final TextKey PERMANENT_LEARN_DOUBLE_CLICK = TextKey.of("gui.adaptations.permanent_learn_double_click", "Double-click required to confirm permanent learn.");
  public static final TextKey KNOWLEDGE_COST = TextKey.of("gui.adaptations.knowledge_cost", "{cost} Knowledge Cost");
  public static final TextKey KNOWLEDGE_COST_NO_REFUNDS = TextKey.of("gui.adaptations.knowledge_cost_no_refunds", "{cost} Knowledge Cost — HARDCORE, REFUNDS DISABLED");
  public static final TextKey ALREADY_LEARNED_NO_REFUNDS = TextKey.of("gui.adaptations.already_learned_no_refunds", "Already Learned — HARDCORE, REFUNDS DISABLED");
  public static final TextKey ALREADY_LEARNED_REFUND = TextKey.of("gui.adaptations.already_learned_refund", "Already Learned — Click to Unlearn & Refund {refund} Knowledge Cost");
  public static final TextKey CLICK_TO_LEARN = TextKey.of("gui.adaptations.click_to_learn", "Click to Learn {adaptation}");
  public static final TextKey KNOWLEDGE_SHORTAGE = TextKey.of("gui.adaptations.knowledge_shortage", "(You only have {knowledge} Knowledge Available)");
  public static final TextKey POWER_DRAIN = TextKey.of("gui.adaptations.power_drain", "{power} Power Drain");
  public static final TextKey EXPERIMENTAL_MUTATIONS = TextKey.of("gui.mutations.experimental_mutations", "Experimental Mutations");
  public static final TextKey MUTATIONS_MENU_SUMMARY = TextKey.of("gui.mutations.menu_summary", "Optional build choices unlocked by your Adapt level.");
  public static final TextKey MUTATIONS_MENU_OPEN = TextKey.of("gui.mutations.menu_open", "Click to view your slots and requirements.");
  public static final TextKey SKILLS_TITLE = TextKey.of("gui.skills.title", "Level {level} ({used}/{maximum} Power Used)");
  public static final TextKey SKILL_TITLE = TextKey.of("gui.skill.title", "{skill} {progress} ({xp}XP {nextLevel})");

  private GuiMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(FIRST);
    builder.add(PREVIOUS);
    builder.add(NEXT);
    builder.add(LAST);
    builder.add(BACK);
    builder.add(CONFIRM);
    builder.add(CANCEL);
    builder.add(APPLY_CHANGE);
    builder.add(LEFT_CLICK_PREVIOUS);
    builder.add(LEFT_CLICK_NEXT);
    builder.add(RIGHT_CLICK_JUMP_BACK);
    builder.add(RIGHT_CLICK_JUMP_FORWARD);
    builder.add(PAGE_OF);
    builder.add(SHOWING_RANGE);
    builder.add(PAGE_SHOWING_RANGE);
    builder.add(FIRST_PAGE);
    builder.add(NO_PREVIOUS_PAGE);
    builder.add(NO_NEXT_PAGE);
    builder.add(LAST_PAGE);
    builder.add(BACK_TO_SKILLS);
    builder.add(BACK_TO_MUTATIONS);
    builder.add(SKILLS);
    builder.add(ADAPTATIONS);
    builder.add(LEVELS);
    builder.add(NO_SKILLS_AVAILABLE);
    builder.add(NO_ELIGIBLE_SKILLS);
    builder.add(NO_ADAPTATIONS_AVAILABLE);
    builder.add(PERMANENT_LEARN_CONFIRM);
    builder.add(PERMANENT_LEARN_CONFIRM_NOW);
    builder.add(PERMANENT_LEARN_DOUBLE_CLICK);
    builder.add(KNOWLEDGE_COST);
    builder.add(KNOWLEDGE_COST_NO_REFUNDS);
    builder.add(ALREADY_LEARNED_NO_REFUNDS);
    builder.add(ALREADY_LEARNED_REFUND);
    builder.add(CLICK_TO_LEARN);
    builder.add(KNOWLEDGE_SHORTAGE);
    builder.add(POWER_DRAIN);
    builder.add(EXPERIMENTAL_MUTATIONS);
    builder.add(MUTATIONS_MENU_SUMMARY);
    builder.add(MUTATIONS_MENU_OPEN);
    builder.add(SKILLS_TITLE);
    builder.add(SKILL_TITLE);
  }
}
