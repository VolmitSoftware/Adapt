package art.arcane.adapt.client.hud;

import art.arcane.adapt.api.notification.HudSlot;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudModelTest {
    private final HudFakes.Clock clock = new HudFakes.Clock();
    private final HudModel model = new HudModel(clock);

    private static ServerFrame.XpEarned xp(String skill, double amount, int level, double progress) {
        return new ServerFrame.XpEarned(skill, amount, 100.0, level, progress);
    }

    private static ServerFrame.HudStatus status(String purpose, long startedAt, long duration, int stacks) {
        return status(purpose, startedAt, duration, stacks, 0);
    }

    private static ServerFrame.HudStatus status(String purpose, long startedAt, long duration, int stacks, int maxStacks) {
        return glyph(purpose, HudGlyph.COOLDOWN, "", startedAt, duration, stacks, maxStacks, false);
    }

    private static ServerFrame.HudStatus glyph(String purpose, HudGlyph kind, String skill, long startedAt, long duration,
                                               int stacks, int maxStacks, boolean lit) {
        return new ServerFrame.HudStatus(purpose, kind, skill, IconRef.item("minecraft:stone"), purpose, startedAt, duration,
                stacks, maxStacks, lit);
    }

    private static ServerFrame.HudPublish notice(String purpose, int priority, long ttl, String text) {
        return new ServerFrame.HudPublish(purpose, priority, ttl, List.of(HudSlot.RIGHT), text);
    }

    private void catalog() {
        model.catalog(new CatalogView(1L, "en_us", List.of(
                new SkillView("agility", "Agility", "", IconRef.item("minecraft:feather"), 0x00FF00, true,
                        List.of(), List.of(), List.of()))));
    }

    @Test
    void aSkillMissingFromTheCatalogStillHasAnIcon() {
        assertSame(HudModel.UNKNOWN_SKILL_ICON, model.skillIcon(model.skill("unknown")));
        assertFalse(model.skillIcon(null).itemId().isBlank());
    }

    @Test
    void aKnownSkillIconCarriesItsConventionSprite() {
        catalog();
        IconRef icon = model.skillIcon(model.skill("agility"));
        assertEquals("minecraft:feather", icon.itemId());
        assertEquals("adapt:textures/gui/skill/agility.png", icon.spriteId());
    }

    @Test
    void staysInactiveUntilTheServerSendsSomethingAndResetDropsEverything() {
        assertFalse(model.active());
        catalog();
        model.xp(xp("agility", 5, 3, 0.5));
        model.status(status("adapt:status:a", clock.epoch, 0L, 0));
        model.publish(notice("adapt:notice", 10, 1000L, "hi"));
        model.toast(new ServerFrame.Toast(ToastKind.GENERIC, IconRef.item("minecraft:stone"), "t", "b", ""));
        model.player(new PlayerView(new PlayerHeader(1, 0.5, 1, 2, 3, true), List.of(), Map.of(), List.of(), PlayerAccess.NONE));
        assertTrue(model.active());

        model.reset();
        assertFalse(model.active());
        assertTrue(model.xpLines().isEmpty());
        assertTrue(model.statuses().isEmpty());
        assertTrue(model.notices().isEmpty());
        assertTrue(model.toasts().shown().isEmpty());
        assertEquals(0, model.toasts().queuedCount());
        assertNull(model.header());
        assertNull(model.skill("agility"));
        assertNull(model.skillState("agility"));
    }

    @Test
    void noticesExpireAfterTheirTtlAndFade() {
        model.publish(notice("adapt:notice", 10, 2000L, "hello"));
        model.expire(clock.millis);
        assertEquals(1, model.notices().size());
        assertEquals(HudSlot.RIGHT, model.notices().get(0).slot());
        clock.advance(2000L);
        model.expire(clock.millis);
        assertEquals(1, model.notices().size());
        clock.advance(HudModel.NOTICE_FADE_MILLIS);
        model.expire(clock.millis);
        assertTrue(model.notices().isEmpty());
    }

    @Test
    void republishingRefreshesTheTtlAndReplacesTheText() {
        model.publish(notice("adapt:status:x", 10, 2000L, "one"));
        clock.advance(1500L);
        model.publish(notice("adapt:status:x", 10, 2000L, "two"));
        clock.advance(1500L);
        model.expire(clock.millis);
        assertEquals(1, model.notices().size());
        assertEquals("two", model.notices().get(0).text());
    }

    @Test
    void noticesAreOrderedByPriorityThenNewest() {
        model.publish(notice("low", 1, 5000L, "low"));
        clock.advance(10L);
        model.publish(notice("high", 50, 5000L, "high"));
        clock.advance(10L);
        model.publish(notice("low2", 1, 5000L, "low2"));
        List<String> order = new ArrayList<>();
        model.notices().forEach((HudNotice notice) -> order.add(notice.purpose()));
        assertEquals(List.of("high", "low2", "low"), order);
    }

    @Test
    void blankTextAndClearRemoveASegment() {
        model.publish(notice("a", 1, 5000L, "a"));
        model.publish(notice("a", 1, 5000L, " "));
        assertTrue(model.notices().isEmpty());
        model.publish(notice("b", 1, 5000L, "b"));
        model.status(status("b", clock.epoch, 5000L, 0));
        model.clear("b");
        assertTrue(model.notices().isEmpty());
        assertTrue(model.statuses().isEmpty());
    }

    @Test
    void aCooldownPulsesReadyThenLeaves() {
        model.status(status("cool", clock.epoch - 1000L, 4000L, 0));
        HudStatusEntry entry = model.statuses().get(0);
        assertEquals(3000L, entry.remaining(clock.millis));
        assertEquals(0.75, entry.fractionLeft(clock.millis), 1e-9);
        assertFalse(entry.ready(clock.millis));
        clock.advance(3000L);
        model.expire(clock.millis);
        assertEquals(1, model.statuses().size());
        assertTrue(entry.ready(clock.millis));
        assertEquals(0.0, entry.readyProgress(clock.millis), 1e-9);
        clock.advance(HudStatusEntry.READY_MILLIS / 2);
        assertEquals(0.5, entry.readyProgress(clock.millis), 0.01);
        clock.advance(HudStatusEntry.READY_MILLIS / 2);
        model.expire(clock.millis);
        assertTrue(model.statuses().isEmpty());
    }

    @Test
    void aNewStateOfAnAdaptationTakesTheSlotOfItsFinishedCooldown() {
        model.status(glyph("adapt:status:architect-foundation.cooldown", HudGlyph.COOLDOWN, "architect", clock.epoch, 5000L,
                0, 0, false));
        HudStatusEntry entry = model.statuses().get(0);
        long shown = entry.shownAt();
        clock.advance(5000L + HudStatusEntry.READY_MILLIS / 3);
        model.expire(clock.millis);
        assertTrue(entry.ready(clock.millis));
        model.status(glyph("adapt:status:architect-foundation.charges", HudGlyph.CHARGES, "architect", 0L, 0L, 4, 4, false));
        assertEquals(1, model.statuses().size());
        assertSame(entry, model.statuses().get(0));
        assertEquals("adapt:status:architect-foundation.charges", entry.purpose());
        assertEquals(HudGlyph.CHARGES, entry.glyph());
        assertEquals(shown, entry.shownAt());
        assertEquals(Long.MIN_VALUE, entry.stacksChangedAt());
        clock.advance(HudStatusEntry.READY_MILLIS);
        model.expire(clock.millis);
        assertEquals(1, model.statuses().size());
        model.clear("adapt:status:architect-foundation.charges");
        assertTrue(model.statuses().isEmpty());
    }

    @Test
    void aLiveCooldownKeepsItsGlyphBesideAnotherStateAndOtherAdaptationsKeepTheirs() {
        model.status(glyph("adapt:status:agility-roll-landing.cooldown", HudGlyph.COOLDOWN, "agility", clock.epoch, 2000L,
                0, 0, false));
        model.status(glyph("adapt:status:agility-roll.cooldown", HudGlyph.COOLDOWN, "agility", clock.epoch, 1000L, 0, 0, false));
        clock.advance(1000L);
        model.status(glyph("adapt:status:agility-roll-landing.duration", HudGlyph.DURATION, "agility", clock.epoch, 500L,
                0, 0, false));
        assertEquals(3, model.statuses().size());
    }

    @Test
    void remarkingALiveCooldownUpdatesItsGlyphInPlace() {
        model.status(glyph("adapt:status:axe-chop.cooldown", HudGlyph.COOLDOWN, "axe", clock.epoch, 4000L, 0, 0, false));
        HudStatusEntry entry = model.statuses().get(0);
        long shown = entry.shownAt();
        clock.advance(1500L);
        model.status(glyph("adapt:status:axe-chop.cooldown", HudGlyph.COOLDOWN, "axe", clock.epoch, 4000L, 0, 0, false));
        assertEquals(1, model.statuses().size());
        assertSame(entry, model.statuses().get(0));
        assertEquals(shown, entry.shownAt());
        assertEquals(4000L, entry.remaining(clock.millis));
    }

    @Test
    void aRunningEffectLeavesWithoutAPulse() {
        model.status(glyph("buff", HudGlyph.DURATION, "", clock.epoch, 2000L, 0, 0, false));
        clock.advance(2000L);
        model.expire(clock.millis);
        assertTrue(model.statuses().isEmpty());
    }

    @Test
    void chargesAndTogglesStayUntilTheServerClearsThem() {
        model.status(glyph("pack", HudGlyph.CHARGES, "tragoul", clock.epoch, 2500L, 1, 3, false));
        model.status(glyph("stance", HudGlyph.TOGGLE, "stealth", 0L, 0L, 0, 0, true));
        clock.advance(600_000L);
        model.expire(clock.millis);
        assertEquals(2, model.statuses().size());
        model.clear("pack");
        model.clear("stance");
        assertTrue(model.statuses().isEmpty());
    }

    @Test
    void pipsRememberWhichWayTheyLastMovedAndWhenTheyFilled() {
        model.status(glyph("pack", HudGlyph.CHARGES, "tragoul", 0L, 0L, 1, 3, false));
        HudStatusEntry pack = model.statuses().get(0);
        assertEquals(Long.MIN_VALUE, pack.stacksChangedAt());
        assertEquals(Long.MIN_VALUE, pack.fullAt());
        clock.advance(100L);
        model.status(glyph("pack", HudGlyph.CHARGES, "tragoul", 0L, 0L, 2, 3, false));
        assertEquals(1, pack.previousStacks());
        assertEquals(clock.millis, pack.stacksChangedAt());
        assertFalse(pack.full());
        clock.advance(100L);
        model.status(glyph("pack", HudGlyph.CHARGES, "tragoul", 0L, 0L, 3, 3, false));
        assertTrue(pack.full());
        assertEquals(clock.millis, pack.fullAt());
        long filled = clock.millis;
        clock.advance(100L);
        model.status(glyph("pack", HudGlyph.CHARGES, "tragoul", 0L, 0L, 3, 3, false));
        assertEquals(filled, pack.fullAt());
        assertEquals(filled, pack.stacksChangedAt());
        clock.advance(100L);
        model.status(glyph("pack", HudGlyph.CHARGES, "tragoul", 0L, 0L, 2, 3, false));
        assertEquals(3, pack.previousStacks());
        assertFalse(pack.full());
        assertEquals(filled, pack.fullAt());
    }

    @Test
    void aGlyphKeepsItsFirstAppearanceAcrossUpdates() {
        model.status(glyph("pack", HudGlyph.CHARGES, "tragoul", 0L, 0L, 1, 3, false));
        long shown = clock.millis;
        clock.advance(500L);
        model.status(glyph("pack", HudGlyph.CHARGES, "tragoul", 0L, 0L, 2, 3, false));
        assertEquals(shown, model.statuses().get(0).shownAt());
        assertEquals(clock.millis, model.statuses().get(0).receivedAt());
    }

    @Test
    void anExpiredRechargeKeepsTheCharges() {
        model.status(glyph("jet", HudGlyph.CHARGES, "seaborne", clock.epoch - 10_000L, 2500L, 2, 4, false));
        assertEquals(1, model.statuses().size());
        assertEquals(0L, model.statuses().get(0).remaining(clock.millis));
    }

    @Test
    void statusTimeIsClampedWhenClocksDisagree() {
        model.status(status("future", clock.epoch + 60_000L, 5000L, 0));
        assertEquals(5000L, model.statuses().get(0).remaining(clock.millis));
        model.status(status("past", clock.epoch - 60_000L, 5000L, 0));
        assertEquals(1, model.statuses().size());
        assertEquals("future", model.statuses().get(0).purpose());
    }

    @Test
    void glyphsGroupBySkillAndKeepTheirPlaceWhileUpdating() {
        model.status(glyph("z", HudGlyph.COOLDOWN, "agility", clock.epoch, 9000L, 0, 0, false));
        model.status(glyph("b", HudGlyph.COOLDOWN, "tragoul", clock.epoch, 2000L, 0, 0, false));
        model.status(glyph("a", HudGlyph.CHARGES, "tragoul", 0L, 0L, 1, 3, false));
        model.status(glyph("y", HudGlyph.COOLDOWN, "agility", clock.epoch, 1000L, 0, 0, false));
        List<String> order = new ArrayList<>();
        model.statuses().forEach((HudStatusEntry entry) -> order.add(entry.purpose()));
        assertEquals(List.of("y", "z", "a", "b"), order);
        model.status(glyph("a", HudGlyph.CHARGES, "tragoul", 0L, 0L, 2, 3, false));
        assertEquals(4, model.statuses().size());
        assertEquals("a", model.statuses().get(2).purpose());
    }

    @Test
    void xpForTheSameSkillMergesIntoOneLine() {
        catalog();
        model.xp(xp("agility", 5, 3, 0.2));
        clock.advance(500L);
        model.xp(xp("agility", 7, 3, 0.3));
        assertEquals(1, model.xpLines().size());
        HudXpLine line = model.xpLines().get(0);
        assertEquals(12.0, line.amount());
        assertEquals("+12", line.label());
        assertEquals(0.3, line.progress());
        assertEquals("3", line.levelText());
    }

    @Test
    void xpAfterTheLineRanOutStartsANewLine() {
        model.xpWindow(4000L);
        model.xp(xp("agility", 5, 3, 0.2));
        clock.advance(4000L);
        model.xp(xp("agility", 1.5, 3, 0.25));
        assertEquals(2, model.xpLines().size());
        assertEquals("+1.5", model.xpLines().get(0).label());
        clock.advance(4000L + HudModel.XP_FADE_MILLIS);
        model.expire(clock.millis);
        assertTrue(model.xpLines().isEmpty());
    }

    @Test
    void aSkillKeepsOneLineWhileItKeepsEarning() {
        model.xp(xp("agility", 5, 3, 0.2));
        long started = clock.millis;
        clock.advance(1000L);
        model.xp(xp("swords", 5, 3, 0.2));
        clock.advance(1000L);
        model.xp(xp("agility", 5, 3, 0.2));
        assertEquals(2, model.xpLines().size());
        HudXpLine agility = model.xpLines().get(0);
        assertEquals("agility", agility.skillId());
        assertEquals(started, agility.createdAt());
        assertEquals(clock.millis, agility.updatedAt());
    }

    @Test
    void levelUpMarksTheLineAndTheSkill() {
        model.xp(xp("agility", 5, 3, 0.9));
        assertEquals(Long.MIN_VALUE, model.xpLines().get(0).levelUpAt());
        clock.advance(100L);
        model.xp(xp("agility", 9, 4, 0.05));
        assertEquals(clock.millis, model.xpLines().get(0).levelUpAt());
        assertEquals(clock.millis, model.skillState("agility").levelUpAt());
        assertEquals("4", model.skillState("agility").levelText());
    }

    @Test
    void feedKeepsAtMostSixteenLinesAndReusesThem() {
        for (int index = 0; index < 40; index++) {
            model.xp(xp("skill" + index, 1, 1, 0.1));
        }
        assertEquals(HudModel.MAX_XP_LINES, model.xpLines().size());
        assertEquals("skill39", model.xpLines().get(0).skillId());
    }

    @Test
    void playerViewFillsSkillsAndMarksLevelUps() {
        model.player(new PlayerView(new PlayerHeader(7, 0.3, 4, 10, 25, true), List.of(
                new SkillLineView("a", 10, 2, 0.1, 5, 3),
                new SkillLineView("b", 900, 9, 0.5, 5, 4),
                new SkillLineView("c", 50, 5, 0.9, 5, 0)), Map.of(), List.of(), PlayerAccess.NONE));
        assertEquals(7L, model.knowledgeTotal());
        assertEquals(9, model.skillState("b").level());
        assertEquals(Long.MIN_VALUE, model.skillState("b").levelUpAt());
        int revision = model.revision();
        model.player(new PlayerView(new PlayerHeader(7, 0.3, 4, 10, 25, true), List.of(
                new SkillLineView("b", 1000, 10, 0.0, 5, 4)), Map.of(), List.of(), PlayerAccess.NONE));
        assertNotEquals(revision, model.revision());
        assertEquals(clock.millis, model.skillState("b").levelUpAt());
    }

    @Test
    void skillNamesComeFromTheCatalog() {
        assertEquals("Wild magic", model.skillName("wild_magic"));
        catalog();
        assertEquals("Agility", model.skillName("agility"));
        assertEquals(0x00FF00, model.skillColor("agility"));
        assertEquals(0xFFFFFF, model.skillColor("missing"));
    }

    @Test
    void toastsQueueBehindTheVisibleLimit() {
        model.toasts().configure(5000L, 2, Double.MAX_VALUE, false);
        for (int index = 0; index < 4; index++) {
            model.toast(new ServerFrame.Toast(ToastKind.GENERIC, IconRef.item("minecraft:stone"), "t" + index, "", ""));
        }
        model.expire(clock.millis);
        assertEquals(2, model.toasts().shown().size());
        assertEquals(2, model.toasts().queuedCount());
        assertEquals("t0", model.toasts().shown().get(0).title());

        HudToast first = model.toasts().shown().get(0);
        clock.advance(model.toasts().duration(first));
        model.expire(clock.millis);
        assertEquals(2, model.toasts().shown().size());
        assertTrue(model.toasts().leaving(first, clock.millis));
        assertEquals(2, model.toasts().queuedCount());

        clock.advance(HudToastQueue.EXIT_MILLIS);
        model.expire(clock.millis);
        assertEquals(2, model.toasts().shown().size());
        assertEquals("t2", model.toasts().shown().get(0).title());
        assertEquals(0, model.toasts().queuedCount());
    }

    @Test
    void toastOffsetsEaseForwardOnceTheCardAheadHasGone() {
        model.toasts().configure(2000L, 3, Double.MAX_VALUE, false);
        model.toast(new ServerFrame.Toast(ToastKind.GENERIC, IconRef.item("minecraft:stone"), "a", "", ""));
        model.expire(clock.millis);
        clock.advance(900L);
        model.toast(new ServerFrame.Toast(ToastKind.CHALLENGE, IconRef.item("minecraft:stone"), "b", "", ""));
        model.expire(clock.millis);
        HudToast second = model.toasts().shown().get(1);
        double place = ToastWidget.CARD_HEIGHT + HudToastQueue.GAP;
        assertEquals(place, second.offset());
        clock.advance(1090L);
        model.expire(clock.millis);
        assertEquals(place, second.offset());
        clock.advance(16L);
        model.expire(clock.millis);
        assertSame(second, model.toasts().shown().get(0));
        assertTrue(second.offset() < place && second.offset() > place * 0.5, Double.toString(second.offset()));
        clock.advance(300L);
        model.expire(clock.millis);
        assertEquals(0.0, second.offset(), 0.5);
    }

    @Test
    void toastQueueDropsTheOldestBeyondItsCap() {
        model.toasts().configure(5000L, 1, Double.MAX_VALUE, false);
        for (int index = 0; index < HudToastQueue.MAX_QUEUED + 5; index++) {
            model.toast(new ServerFrame.Toast(ToastKind.GENERIC, IconRef.item("minecraft:stone"), "t" + index, "", ""));
        }
        assertEquals(HudToastQueue.MAX_QUEUED, model.toasts().queuedCount());
        model.expire(clock.millis);
        assertEquals("t5", model.toasts().shown().get(0).title());
    }

    @Test
    void clearAllDropsNoticesAndStatusesButKeepsTheViews() {
        catalog();
        model.xp(xp("agility", 5, 3, 0.5));
        model.status(status("adapt:status:a", clock.epoch, 0L, 0));
        model.publish(notice("adapt:notice", 10, 1000L, "hi"));

        model.clearAll();

        assertTrue(model.active());
        assertTrue(model.notices().isEmpty());
        assertTrue(model.statuses().isEmpty());
        assertEquals(1, model.xpLines().size());
        assertEquals("Agility", model.skillName("agility"));
    }

    @Test
    void feedbackShowsAsACentreNotice() {
        model.feedback(FeedbackKind.FAILURE, "Not enough knowledge");
        assertTrue(model.active());
        HudNotice notice = model.notices().get(0);
        assertEquals(HudModel.FEEDBACK_PURPOSE, notice.purpose());
        assertEquals(HudSlot.CENTER, notice.slot());
        assertTrue(notice.text().endsWith("Not enough knowledge"));
    }
}
