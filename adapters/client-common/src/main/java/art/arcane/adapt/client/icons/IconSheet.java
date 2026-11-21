package art.arcane.adapt.client.icons;

import art.arcane.adapt.client.AdaptClient;
import art.arcane.adapt.client.AdaptKey;
import art.arcane.adapt.client.panel.PanelPreview;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.screen.PreviewSamples;
import art.arcane.adapt.client.ui.UiSpriteLookup;
import art.arcane.adapt.client.ui.UiSprites;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiMotion;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Development sheet of every icon the client can draw art for, and where each one comes from. With
 * {@code -Dadapt.client.preview=icons} the "Open skills" key opens it over the connected server's catalog, or the sample
 * catalog when not connected.
 */
public final class IconSheet {
    public static final String ICONS = "icons";
    private static final IconRef TOAST_PLACEHOLDER = IconRef.item("minecraft:paper");

    private IconSheet() {
    }

    public static void installIfRequested(AdaptClient client) {
        if (!ICONS.equals(System.getProperty(PanelPreview.PROPERTY))) {
            return;
        }
        client.bindKey(AdaptKey.OPEN_SKILLS, () -> {
            CatalogView catalog = client.net().state().catalog();
            client.host().open(new IconSheetScreen(entries(catalog == null ? PreviewSamples.catalog() : catalog), catalog == null,
                    UiTheme.DEFAULT, UiMotion.system()));
        });
    }

    /** Skills, then adaptations, each by id, then the toast kinds. */
    public static List<Entry> entries(CatalogView catalog) {
        List<SkillView> skills = new ArrayList<>(catalog.skills());
        skills.sort(Comparator.comparing(SkillView::id));
        List<Entry> entries = new ArrayList<>();
        List<AdaptationView> adaptations = new ArrayList<>();
        for (SkillView skill : skills) {
            entries.add(new Entry("skill", skill.id(), skill.name(), UiIcons.skill(skill)));
            adaptations.addAll(skill.adaptations());
        }
        adaptations.sort(Comparator.comparing(AdaptationView::id));
        for (AdaptationView adaptation : adaptations) {
            entries.add(new Entry("adaptation", adaptation.id(), adaptation.name(), UiIcons.adaptation(adaptation)));
        }
        for (ToastKind kind : ToastKind.values()) {
            String name = kind.name().toLowerCase(Locale.ROOT);
            entries.add(new Entry("toast", name, name.replace('_', ' '), UiIcons.toast(kind, TOAST_PLACEHOLDER)));
        }
        return entries;
    }

    /** Where {@code entry}'s icon comes from with the resource packs loaded now. */
    public static Source source(UiSprites sprites, Entry entry) {
        UiSpriteLookup lookup = sprites.lookup(entry.icon().spriteId());
        if (lookup.usable()) {
            return new Source(Kind.SPRITE, lookup.texture().width() + "x" + lookup.texture().height());
        }
        return lookup.problem() != null ? new Source(Kind.INVALID, lookup.problem()) : new Source(Kind.ITEM, entry.icon().itemId());
    }

    /** One row: {@code icon} carries the sprite id it would be drawn from. */
    public record Entry(String kind, String id, String name, IconRef icon) {
    }

    public record Source(Kind kind, String detail) {
    }

    public enum Kind {
        SPRITE,
        ITEM,
        INVALID
    }
}
