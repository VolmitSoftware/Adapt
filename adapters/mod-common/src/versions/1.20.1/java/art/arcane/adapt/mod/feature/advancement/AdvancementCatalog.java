package art.arcane.adapt.mod.feature.advancement;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.ModText;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementNode;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AdvancementCatalog {
    static final AdvancementCatalog EMPTY = new AdvancementCatalog(Collections.emptyMap());
    private static final String TEXTURE_PREFIX = "textures/";
    private static final String TEXTURE_SUFFIX = ".png";

    private final Map<Id, Entry> entries;

    private AdvancementCatalog(Map<Id, Entry> entries) {
        this.entries = entries;
    }

    static AdvancementCatalog build(List<AdvancementNode> roots) {
        Map<Id, Entry> entries = new LinkedHashMap<>();
        for (AdvancementNode root : roots) {
            add(entries, root, null);
        }
        return new AdvancementCatalog(Collections.unmodifiableMap(entries));
    }

    private static void add(Map<Id, Entry> entries, AdvancementNode node, Entry parent) {
        ResourceLocation id = ModRefs.identifier(node.key());
        List<String> criteria = criteria(node.maxProgress());
        Advancement.Builder builder = Advancement.Builder.advancement();
        if (parent != null) {
            builder.parent(parent.placed().advancement());
        }
        ResourceLocation background = parent == null ? texture(node.background()) : null;
        builder.display(new DisplayInfo(icon(node.icon()), ModText.component(node.title()), ModText.component(node.description()), background,
                type(node.frame()), false, false, false));
        for (String criterion : criteria) {
            AdvancementCriteria.impossible(builder, criterion);
        }
        builder.requirements(allOf(criteria));
        Advancement advancement = builder.build(id);
        Entry entry = new Entry(node, parent, new PlacedAdvancement(advancement, node.x(), node.y()), criteria);
        entries.put(node.key(), entry);
        for (AdvancementNode child : node.children()) {
            add(entries, child, entry);
        }
    }

    static List<String> criteria(int count) {
        List<String> names = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            names.add(Integer.toString(index));
        }
        return names;
    }

    /** Every criterion in its own requirement group, so all of them are needed. */
    static String[][] allOf(List<String> criteria) {
        String[][] requirements = new String[criteria.size()][];
        for (int index = 0; index < requirements.length; index++) {
            requirements[index] = new String[] {criteria.get(index)};
        }
        return requirements;
    }

    static ItemStack icon(ItemRef icon) {
        ItemStack stack = icon == null ? ItemStack.EMPTY : ModRefs.item(icon);
        if (stack == null || stack.isEmpty()) {
            return new ItemStack(Items.PAPER);
        }
        return stack.copy();
    }

    static FrameType type(AdvancementFrame frame) {
        return switch (frame) {
            case TASK -> FrameType.TASK;
            case GOAL -> FrameType.GOAL;
            case CHALLENGE -> FrameType.CHALLENGE;
        };
    }

    /** The full texture path these clients bind a tab background from, e.g. {@code textures/gui/...png}. */
    static ResourceLocation texture(Id background) {
        if (background == null) {
            return ModRefs.identifier(Id.minecraft("textures/gui/advancements/backgrounds/stone.png"));
        }
        String path = background.path();
        if (!path.startsWith(TEXTURE_PREFIX)) {
            path = TEXTURE_PREFIX + path;
        }
        if (!path.endsWith(TEXTURE_SUFFIX)) {
            path = path + TEXTURE_SUFFIX;
        }
        return ModRefs.identifier(background.withPath(path));
    }

    static AdvancementProgress progress(Advancement advancement, List<String> criteria, int done) {
        AdvancementProgress progress = new AdvancementProgress();
        progress.update(advancement.getCriteria(), advancement.getRequirements());
        int limit = Math.min(done, criteria.size());
        for (int index = 0; index < limit; index++) {
            progress.grantProgress(criteria.get(index));
        }
        return progress;
    }

    Entry get(Id key) {
        return entries.get(key);
    }

    boolean contains(Id key) {
        return entries.containsKey(key);
    }

    Iterable<Entry> entries() {
        return entries.values();
    }

    record Entry(AdvancementNode node, Entry parent, PlacedAdvancement placed, List<String> criteria) {
        Id key() {
            return node.key();
        }

        ResourceLocation id() {
            return placed.advancement().getId();
        }

        int max() {
            return node.maxProgress();
        }

        AdvancementVisibility visibility() {
            return node.visibility();
        }

        AdvancementProgress progress(int done) {
            return AdvancementCatalog.progress(placed.advancement(), criteria, done);
        }
    }
}
