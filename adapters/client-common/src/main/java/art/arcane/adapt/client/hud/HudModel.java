package art.arcane.adapt.client.hud;

import art.arcane.adapt.engine.notification.HudSlot;
import art.arcane.adapt.client.net.HudSink;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What the HUD shows: the session's {@link HudSink}, plus the catalog and player view, on the client thread. The model stays inactive (and the
 * HUD draws nothing) until the server sends something, and {@link #reset()} drops everything on disconnect or
 * withdrawal. Expiry runs on the injected monotonic clock.
 */
public final class HudModel implements HudSink {
    public static final int MAX_XP_LINES = 16;
    public static final int MAX_NOTICES = 16;
    public static final int MAX_STATUSES = 128;
    public static final long XP_FADE_MILLIS = 400L;
    public static final long NOTICE_FADE_MILLIS = 250L;
    static final IconRef UNKNOWN_SKILL_ICON = IconRef.item("minecraft:experience_bottle");
    static final String FEEDBACK_PURPOSE = "adapt:feedback";
    static final int FEEDBACK_PRIORITY = 100;
    static final long FEEDBACK_MILLIS = 3500L;
    private static final Comparator<HudStatusEntry> STATUS_ORDER = (HudStatusEntry left, HudStatusEntry right) -> {
        int bySkill = left.skillId.compareTo(right.skillId);
        return bySkill != 0 ? bySkill : left.purpose.compareTo(right.purpose);
    };
    private static final Comparator<HudNotice> NOTICE_ORDER = (HudNotice left, HudNotice right) -> {
        int byPriority = Integer.compare(right.priority, left.priority);
        return byPriority != 0 ? byPriority : Long.compare(right.shownAt, left.shownAt);
    };

    private final HudClock clock;
    private final Map<String, SkillView> skills = new HashMap<>();
    private final List<SkillView> skillOrder = new ArrayList<>();
    private final Map<String, HudSkillState> states = new HashMap<>();
    private final List<HudXpLine> xpLines = new ArrayList<>(MAX_XP_LINES);
    private final ArrayDeque<HudXpLine> spareLines = new ArrayDeque<>(MAX_XP_LINES);
    private final List<HudStatusEntry> statuses = new ArrayList<>(MAX_STATUSES);
    private final List<HudNotice> notices = new ArrayList<>(MAX_NOTICES);
    private final HudToastQueue toasts = new HudToastQueue();
    private boolean active;
    private int revision;
    private int statusRevision;
    private PlayerHeader header;
    private long knowledgeTotal;
    private long xpWindowMillis = 3000L;

    public HudModel(HudClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public HudClock clock() {
        return clock;
    }

    /** True once the server sent anything since the last {@link #reset()}. */
    public boolean active() {
        return active;
    }

    /** Changes whenever a status is added, updated or removed, so glyph layouts are rebuilt only then. */
    public int statusRevision() {
        return statusRevision;
    }

    /** Changes whenever the catalog or player view changes, so widgets can rebuild cached text. */
    public int revision() {
        return revision;
    }

    @Override
    public void publish(ServerFrame.HudPublish frame) {
        active = true;
        String text = frame.text();
        if (text.isBlank()) {
            clear(frame.purpose());
            return;
        }
        long now = clock.millis();
        HudNotice notice = notice(frame.purpose());
        if (notice == null) {
            if (notices.size() >= MAX_NOTICES) {
                notices.remove(notices.size() - 1);
            }
            notice = new HudNotice(frame.purpose());
            notices.add(notice);
            notice.shownAt = now;
        }
        notice.priority = frame.priority();
        notice.slot = frame.slots().isEmpty() ? HudSlot.CENTER : frame.slots().get(0);
        notice.text = text;
        notice.expiresAt = now + Math.max(1L, frame.ttlMillis());
        notices.sort(NOTICE_ORDER);
    }

    /** Removes the text segment or status with this purpose. */
    @Override
    public void clear(String purpose) {
        active = true;
        notices.removeIf((HudNotice notice) -> notice.purpose.equals(purpose));
        if (statuses.removeIf((HudStatusEntry status) -> status.purpose.equals(purpose))) {
            statusRevision++;
        }
    }

    /** Server feedback shown as a short notice, for when no Adapt screen is open to show it. */
    public void feedback(FeedbackKind kind, String text) {
        String color = switch (kind) {
            case SUCCESS -> "\u00a7a";
            case FAILURE -> "\u00a7c";
            case INFO -> "\u00a77";
        };
        publish(new ServerFrame.HudPublish(FEEDBACK_PURPOSE, FEEDBACK_PRIORITY, FEEDBACK_MILLIS, List.of(HudSlot.CENTER),
                color + text));
    }

    @Override
    public void clearAll() {
        active = true;
        notices.clear();
        statuses.clear();
        statusRevision++;
    }

    /**
     * Adds or updates the glyph with the frame's purpose in place. A finished cooldown still pulsing ready hands its slot
     * to a new glyph of the same adaptation, so one adaptation never shows its old cooldown beside its next state.
     */
    @Override
    public void status(ServerFrame.HudStatus frame) {
        active = true;
        long now = clock.millis();
        long duration = Math.max(0L, frame.durationMillis());
        long startsAt = now;
        if (duration > 0L) {
            long elapsed = clock.epochMillis() - frame.startedAtMillis();
            startsAt = now - Math.max(0L, Math.min(duration, elapsed));
            boolean timed = frame.glyph() == HudGlyph.COOLDOWN || frame.glyph() == HudGlyph.DURATION;
            if (timed && startsAt + duration <= now) {
                clear(frame.purpose());
                return;
            }
        }
        HudStatusEntry entry = null;
        HudStatusEntry finished = null;
        for (HudStatusEntry status : statuses) {
            if (status.purpose.equals(frame.purpose())) {
                entry = status;
                break;
            }
            if (finished == null && status.ready(now) && HudStatusEntry.sameOwner(status.purpose, frame.purpose())) {
                finished = status;
            }
        }
        if (entry == null && finished != null) {
            entry = finished;
            entry.takeOver(frame.purpose());
        }
        if (entry == null) {
            if (statuses.size() >= MAX_STATUSES) {
                statuses.remove(statuses.size() - 1);
            }
            entry = new HudStatusEntry(frame.purpose());
            statuses.add(entry);
        }
        entry.set(frame.glyph(), frame.skillId(), frame.icon(), frame.label(), frame.lit(), frame.stacks(), frame.maxStacks(),
                startsAt, duration, now);
        statuses.sort(STATUS_ORDER);
        statusRevision++;
    }

    @Override
    public void xp(ServerFrame.XpEarned frame) {
        active = true;
        long now = clock.millis();
        String skillId = frame.skillId();
        HudSkillState state = states.get(skillId);
        boolean known = state != null;
        if (state == null) {
            state = new HudSkillState(skillId);
            states.put(skillId, state);
        }
        boolean leveled = known && frame.level() > state.level;
        state.set(frame.level(), frame.progress(), frame.totalXp(), state.knowledge);
        if (leveled) {
            state.levelUpAt = now;
        }

        HudXpLine line = null;
        for (int index = 0; index < xpLines.size(); index++) {
            HudXpLine candidate = xpLines.get(index);
            if (candidate.skillId.equals(skillId) && now - candidate.updatedAt < xpWindowMillis) {
                line = candidate;
                xpLines.remove(index);
                break;
            }
        }
        if (line == null) {
            line = spareLines.isEmpty() ? new HudXpLine() : spareLines.pop();
            line.reset(skillId, now);
        }
        xpLines.add(0, line);
        while (xpLines.size() > MAX_XP_LINES) {
            spareLines.push(xpLines.remove(xpLines.size() - 1));
        }
        line.amount += frame.amount();
        line.updatedAt = now;
        if (leveled) {
            line.levelUpAt = now;
        }
        if (line.level != frame.level() || line.levelText.isEmpty()) {
            line.levelText = Integer.toString(frame.level());
        }
        line.level = frame.level();
        line.progress = Math.max(0.0, Math.min(1.0, frame.progress()));
        line.label = "+" + formatAmount(line.amount);
    }

    @Override
    public void toast(ServerFrame.Toast frame) {
        active = true;
        toasts.add(new HudToast(frame.kind(), frame.icon(), frame.title(), frame.body(), frame.skillId()), clock.millis());
    }

    public void player(PlayerView view) {
        active = true;
        revision++;
        header = view.header();
        long knowledge = 0L;
        boolean firstView = states.isEmpty();
        for (SkillLineView line : view.skills()) {
            HudSkillState state = states.get(line.skillId());
            if (state == null) {
                state = new HudSkillState(line.skillId());
                states.put(line.skillId(), state);
            } else if (!firstView && line.level() > state.level) {
                state.levelUpAt = clock.millis();
            }
            state.set(line.level(), line.progress(), line.xp(), line.knowledge());
            knowledge += line.knowledge();
        }
        knowledgeTotal = knowledge;
    }

    public void catalog(CatalogView catalog) {
        active = true;
        revision++;
        skills.clear();
        skillOrder.clear();
        for (SkillView skill : catalog.skills()) {
            skills.put(skill.id(), skill);
            skillOrder.add(skill);
        }
    }

    /** Drops everything; the HUD draws nothing until the server sends again. */
    @Override
    public void reset() {
        active = false;
        revision++;
        skills.clear();
        skillOrder.clear();
        states.clear();
        for (HudXpLine line : xpLines) {
            spareLines.push(line);
        }
        xpLines.clear();
        statuses.clear();
        statusRevision++;
        notices.clear();
        toasts.clear();
        header = null;
        knowledgeTotal = 0L;
    }

    /** Drops what has run out. Called once per frame before drawing. */
    public void expire(long now) {
        for (int index = xpLines.size() - 1; index >= 0; index--) {
            HudXpLine line = xpLines.get(index);
            if (now - line.updatedAt >= xpWindowMillis + XP_FADE_MILLIS) {
                spareLines.push(xpLines.remove(index));
            }
        }
        for (int index = statuses.size() - 1; index >= 0; index--) {
            if (statuses.get(index).expired(now)) {
                statuses.remove(index);
                statusRevision++;
            }
        }
        for (int index = notices.size() - 1; index >= 0; index--) {
            if (notices.get(index).expiresAt + NOTICE_FADE_MILLIS <= now) {
                notices.remove(index);
            }
        }
        toasts.update(now);
    }

    /** How long a skill's row stays after its last award and keeps merging awards; set from the skills widget. */
    public void xpWindow(long millis) {
        xpWindowMillis = Math.max(500L, millis);
    }

    public long xpWindow() {
        return xpWindowMillis;
    }

    public SkillView skill(String skillId) {
        return skills.get(skillId);
    }

    /** Catalog skills in catalog order. */
    public List<SkillView> catalogSkills() {
        return skillOrder;
    }

    public HudSkillState skillState(String skillId) {
        return states.get(skillId);
    }

    public PlayerHeader header() {
        return header;
    }

    public long knowledgeTotal() {
        return knowledgeTotal;
    }

    /** Newest first. */
    public List<HudXpLine> xpLines() {
        return xpLines;
    }

    /** Grouped by skill, then by purpose, so glyphs keep their places while they update. */
    public List<HudStatusEntry> statuses() {
        return statuses;
    }

    /** Highest priority first, newest first within a priority. */
    public List<HudNotice> notices() {
        return notices;
    }

    public HudToastQueue toasts() {
        return toasts;
    }

    /** The skill's plain name, or a readable form of its id before the catalog arrives. */
    public String skillName(String skillId) {
        SkillView skill = skills.get(skillId);
        if (skill != null) {
            return skill.name();
        }
        String readable = skillId.replace('_', ' ');
        return readable.isEmpty() ? readable : readable.substring(0, 1).toUpperCase(Locale.ROOT) + readable.substring(1);
    }

    /** The skill's icon, or a stand-in so a row never draws without one while the catalog lacks the skill. */
    public IconRef skillIcon(SkillView skill) {
        return skill == null ? UNKNOWN_SKILL_ICON : UiIcons.skill(skill);
    }

    /** 0xRRGGBB of the skill, or white. */
    public int skillColor(String skillId) {
        SkillView skill = skills.get(skillId);
        return skill == null ? 0xFFFFFF : skill.color() & 0xFFFFFF;
    }

    static String formatAmount(double amount) {
        if (amount >= 10.0 || Math.abs(amount - Math.rint(amount)) < 0.05) {
            return Long.toString(Math.round(amount));
        }
        return String.format(Locale.ROOT, "%.1f", amount);
    }

    private HudNotice notice(String purpose) {
        for (HudNotice notice : notices) {
            if (notice.purpose.equals(purpose)) {
                return notice;
            }
        }
        return null;
    }
}
