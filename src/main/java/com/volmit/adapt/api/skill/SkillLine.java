package com.volmit.adapt.api.skill;

import com.volmit.adapt.api.notification.SoundNotification;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Form;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

public enum SkillLine {

    DISCOVERY(C.DARK_BLUE, BarColor.BLUE, BarStyle.SEGMENTED_20, S.ROCK, 500),
    HUNTER(C.DARK_RED, BarColor.RED, BarStyle.SEGMENTED_20, S.PUNCH, 100),
    AGILITY(C.GREEN, BarColor.GREEN, BarStyle.SEGMENTED_20,S.FAST, 100),
    CRAFTING(C.YELLOW, BarColor.YELLOW, BarStyle.SEGMENTED_20,S.FAST, 100),
    BUILDING(C.GOLD, BarColor.YELLOW, BarStyle.SEGMENTED_20,S.FAST, 100),
    UNARMED(C.GREEN,BarColor.GREEN, BarStyle.SEGMENTED_20, S.PUNCH, 100),
    DEFENSE(C.WHITE,BarColor.WHITE, BarStyle.SEGMENTED_20, S.PUNCH, 100),
    MELEE(C.GREEN,BarColor.RED, BarStyle.SEGMENTED_20, S.SWIPE, 100),
    RANGED(C.GREEN, BarColor.GREEN, BarStyle.SEGMENTED_20,S.ARROW, 100),
    EXCAVATION(C.RED,BarColor.RED, BarStyle.SEGMENTED_20, S.ROCK, 100),
    MINING(C.DARK_GRAY,BarColor.WHITE, BarStyle.SEGMENTED_20, S.ROCK, 100),
    LUMBERING(C.DARK_GREEN,BarColor.GREEN, BarStyle.SEGMENTED_20, S.ROCK, 100),
    AQUANAUTICS(C.AQUA, BarColor.BLUE, BarStyle.SEGMENTED_20,S.WATERY, 100),
    AERONAUTICS(C.BLUE,BarColor.BLUE, BarStyle.SEGMENTED_20,S.AERO, 100),
    STEALTH(C.DARK_GRAY,BarColor.WHITE, BarStyle.SEGMENTED_20, S.CREEPY, 100);

    private C color;
    private String dname;
    private BarColor barColor;
    private BarStyle barStyle;
    private String cname;
    @Getter
    private double minxp;
    @Getter
    private SoundNotification sound;

    public static class S
    {
        public static final SoundNotification DING = SoundNotification.builder()
                .sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP)
                .volume(0.25f)
                .pitch(1.35f)
                .predelay(150)
                .build();

        public static final SoundNotification PUNCH = SoundNotification.builder()
                .sound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK)
                .volume(0.35f)
                .pitch(1.35f)
                .build();

        public static final SoundNotification SWIPE = SoundNotification.builder()
                .sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP)
                .volume(0.35f)
                .pitch(1.35f)
                .build();
        public static final SoundNotification ARROW = SoundNotification.builder()
                .sound(Sound.ENTITY_ARROW_HIT_PLAYER)
                .volume(0.35f)
                .pitch(1.35f)
                .build();

        public static final SoundNotification ROCK = SoundNotification.builder()
                .sound(Sound.BLOCK_BASALT_HIT)
                .volume(0.25f)
                .pitch(0.85f)
                .build();
        public static final SoundNotification WOOD = SoundNotification.builder()
                .sound(Sound.ITEM_AXE_STRIP)
                .volume(0.25f)
                .pitch(0.85f)
                .build();

        public static final SoundNotification FAST = SoundNotification.builder()
                .sound(Sound.ITEM_ARMOR_EQUIP_LEATHER)
                .volume(0.35f)
                .pitch(1.23f)
                .build();

        public static final SoundNotification AERO = SoundNotification.builder()
                .sound(Sound.ITEM_ARMOR_EQUIP_ELYTRA)
                .volume(0.35f)
                .pitch(1.25f)
                .build();

        public static final SoundNotification CREEPY = SoundNotification.builder()
                .sound(Sound.BLOCK_PORTAL_TRIGGER)
                .volume(0.35f)
                .pitch(1.35f)
                .build();

        public static final SoundNotification WATERY = SoundNotification.builder()
                .sound(Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED)
                .volume(0.35f)
                .pitch(1.35f)
                .build();
    }

    private SkillLine(C color,  BarColor barColor, BarStyle barStyle, SoundNotification n,double minxp)
    {
        this.barColor = barColor;
        this.barStyle = barStyle;
        this.color = color;
        this.sound = n;
        this.minxp = minxp;
        this.dname = Form.capitalizeWords(name().toLowerCase().replaceAll("\\Q_\\E", " "));
        this.cname = name().toLowerCase().replaceAll("\\Q_\\E", "-");
    }

    public BossBar newBossBar()
    {
        return Bukkit.createBossBar(getDisplayName(), barColor, barStyle);
    }

    public PlayerSkillLine of(AdaptPlayer p)
    {
        return p.getSkillLine(getCodeName());
    }

    public C getColor()
    {
        return color;
    }

    public String getDisplayName()
    {
        return C.RESET + "" + C.BOLD + "" + getColor() + dname + C.RESET;
    }

    public String getCodeName()
    {
        return cname;
    }
}
