package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.kit.Label;
import art.arcane.adapt.client.ui.kit.Popover;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.XpGuideView;

/**
 * A skill's description and how it earns XP, in a popover under its (i): the description first, then the lines one
 * under another, or for a mutated skill each parent's lines under that parent's name in its colour, then the
 * mutation's rules under their own name.
 */
final class XpGuidePopover {
    static final int WIDTH = 190;
    /** A mutated skill's guide is wider, so its three sections stay short enough for a small screen. */
    static final int MUTATED_WIDTH = 290;
    static final String ID = "guide.popover";
    private static final int LINE_GAP = 3;
    private static final int SECTION_GAP = 6;

    private XpGuidePopover() {
    }

    /**
     * The guide titled with the skill's name and the server's heading, such as "Agility · Earning XP", under the skill's
     * {@code description}; either may be missing.
     */
    static Popover of(String skillName, String heading, String description, XpGuideView guide, CatalogIndex catalog, UiRect anchor) {
        String title = heading.isEmpty() || guide == null ? skillName : skillName + " · " + heading;
        UiGroup content = new UiGroup("guide", UiLayoutPolicy.column(SECTION_GAP));
        if (!description.isBlank()) {
            content.add(new Label("guide.description", description).setWrap(true));
        }
        if (guide == null) {
            return new Popover(ID, title, anchor, WIDTH, content);
        }
        int index = 0;
        for (XpGuideView.Section section : guide.sections()) {
            UiGroup lines = content.add(new UiGroup("guide.section:" + index, UiLayoutPolicy.column(LINE_GAP)));
            if (guide.mutated()) {
                SkillView source = section.skillId().isEmpty() || catalog == null ? null : catalog.skill(section.skillId());
                lines.add(new Label("guide.title:" + index, section.title())
                        .setColor(source == null ? Palette.LOCK : Palette.accent(source.color())));
            }
            int line = 0;
            for (String text : section.lines()) {
                lines.add(new Label("guide.line:" + index + ":" + line++, text).setWrap(true));
            }
            index++;
        }
        return new Popover(ID, title, anchor, guide.mutated() ? MUTATED_WIDTH : WIDTH, content);
    }
}
