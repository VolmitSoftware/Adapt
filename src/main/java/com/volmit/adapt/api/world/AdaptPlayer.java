package com.volmit.adapt.api.world;

import com.google.gson.Gson;
import com.volmit.adapt.api.notification.SoundNotification;
import com.volmit.adapt.api.notification.TitleNotification;
import com.volmit.adapt.api.skill.SkillLine;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.*;
import lombok.Data;
import lombok.SneakyThrows;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.concurrent.TimeUnit;

@Data
public class AdaptPlayer extends TickedObject {
    private final Player player;
    private final PlayerData data;
    private ChronoLatch savelatch;
    private ChronoLatch barlatch;
    private ChronoLatch updatelatch;
    private Notifier not;
    private KMap<String, BossBar> bars;

    public AdaptPlayer(Player p) {
        super("players", p.getUniqueId().toString(), 50);
        this.player = p;
        bars = new KMap<>();
        data = loadPlayerData();
        updatelatch = new ChronoLatch(1000);
        barlatch = new ChronoLatch(250);
        savelatch = new ChronoLatch(60000);
        not = new Notifier(p);
    }

    public boolean isBusy()
    {
        return not.isBusy();
    }

    public PlayerSkillLine getSkillLine(String l)
    {
        return getData().getSkillLine(l);
    }

    @SneakyThrows
    private void save()
    {
        IO.writeAll(new File("data/players/" + player.getUniqueId() + ".json"),  new JSONObject(new Gson().toJson(data)).toString(4));
    }

    @EventHandler
    public void on(PlayerInteractEvent e)
    {
        if(e.getPlayer().equals(player))
        {
            if(e.getClickedBlock() != null)
            {
                if(getData().getSeenBlocks().addIfMissing(e.getClickedBlock().getBlockData().toString()))
                {
                    SkillLine.DISCOVERY.of(this).giveXP(125);
                }
            }
        }
    }

    @EventHandler
    public void on(PlayerInteractAtEntityEvent e)
    {
        if(e.getPlayer().equals(player))
        {
            EntityType mat =e.getRightClicked().getType();

            if(getData().getSeenMobs().addIfMissing(mat))
            {
                SkillLine.DISCOVERY.of(this).giveXP(125);
            }
        }
    }

    @EventHandler
    public void on(BlockBreakEvent e)
    {
        if(!e.getPlayer().equals(player))
        {
            return;
        }

        Material mat = e.getBlock().getType();

        if(getData().getSeenBlocks().addIfMissing(e.getBlock().getBlockData().toString()))
        {
            SkillLine.DISCOVERY.of(this).giveXP(125);
        }

        double d = 1;
        d *= (e.getBlock().getDrops().size() + 0.65);
        d *= Math.max(0.1, e.getBlock().getType().getHardness()) * 2;

        ItemStack tool = e.getPlayer().getInventory().getItemInMainHand();
        if(tool != null)
        {
            String t = tool.getType().name();

            if(t.contains("_PICKAXE"))
            {
                if(e.getBlock().getType().name().endsWith("_ORE"))
                {
                    d *= 4.5;

                    if(e.getBlock().getType().equals(Material.COAL_ORE))
                    {
                        d *= 1.25;
                    }

                    if(e.getBlock().getType().equals(Material.DIAMOND_ORE))
                    {
                        d *= 31.25;
                    }

                    if(e.getBlock().getType().equals(Material.EMERALD_ORE))
                    {
                        d *= 60.25;
                    }

                    if(e.getBlock().getType().equals(Material.GOLD_ORE))
                    {
                        d *= 11.35;
                    }

                    if(e.getBlock().getType().equals(Material.IRON_ORE))
                    {
                        d *= 5.125;
                    }

                    if(e.getBlock().getType().equals(Material.LAPIS_ORE))
                    {
                        d *= 15.125;
                    }

                    if(e.getBlock().getType().equals(Material.REDSTONE_ORE))
                    {
                        d *= 7.125;
                    }
                }

                SkillLine.MINING.of(this).giveXP(d * 0.625);
            }

            else if(t.contains("_AXE"))
            {
                SkillLine.LUMBERING.of(this).giveXP(d * 0.625);
            }

            else if(t.contains("_SHOVEL"))
            {
                SkillLine.EXCAVATION.of(this).giveXP(d * 0.625);
            }
        }

        else
        {
            SkillLine.EXCAVATION.of(this).giveXP(d * 0.625);
        }

        if(M.r(Math.min(d / 30D, 0.1)))
        {
            dropXP(e.getBlock().getLocation(), (int) (d / 70D));
        }
    }

    public void dropXP(Location l, int maxvalue)
    {
        ExperienceOrb o = (ExperienceOrb) l.getWorld().spawnEntity(l, EntityType.EXPERIENCE_ORB);
        o.setExperience(RNG.r.i(1, Math.max(1, maxvalue)));
    }

    @EventHandler
    public void on(EntityPickupItemEvent e)
    {
        if(e.getEntity().equals(player))
        {
            Material mat = e.getItem().getItemStack().getType();

            if(getData().getSeenItems().addIfMissing(mat))
            {
                SkillLine.DISCOVERY.of(this).giveXP(50);

                if(M.r(0.1))
                {
                    dropXP(player.getLocation(), 1);
                }
            }
        }
    }

    @EventHandler
    public void on(BlockPlaceEvent e)
    {
        if(!e.getPlayer().equals(player))
        {
            return;
        }

        double d = 1;
        d *= (e.getBlock().getDrops().size() + 0.65);
        d *= Math.max(0.1, e.getBlock().getType().getHardness()) * 2;
        SkillLine.BUILDING.of(this).giveXP(d * 0.625);

        if(M.r(Math.min(d / 30D, 0.1)))
        {
            dropXP(e.getBlock().getLocation(), (int) (d / 70D));
        }

        if(getData().getSeenBlocks().addIfMissing(e.getBlockPlaced().getBlockData().toString()))
        {
            SkillLine.DISCOVERY.of(this).giveXP(125);
        }
    }

    @EventHandler
    public void on(EntityDeathEvent e)
    {
        if(e.getEntity().getKiller() != null && e.getEntity().getKiller().equals(player))
        {
            SkillLine.HUNTER.of(this).giveXP(e.getEntity().getLastDamage() + 100);
            SkillLine.HUNTER.of(this).update(this, SkillLine.HUNTER.name());

            if(M.r(0.25))
            {
                dropXP(e.getEntity().getLocation(), 2);
            }

            if(M.r(0.5))
            {
                dropXP(e.getEntity().getLocation(), 1);
            }
        }
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e)
    {
        if(e.getEntity().equals(player))
        {
            SkillLine.DEFENSE.of(this).giveXP(e.getDamage() * 7);

            if(M.r(0.04))
            {
                dropXP(e.getEntity().getLocation(), 1);
            }

            EntityType mat =e.getDamager().getType();

            if(getData().getSeenMobs().addIfMissing(mat))
            {
                SkillLine.DISCOVERY.of(this).giveXP(125);
            }
        }

        if(e.getDamager() != null && e.getDamager() instanceof Player && ((Player)e.getDamager()).equals(player))
        {
            EntityType mat =e.getEntity().getType();

            if(getData().getSeenMobs().addIfMissing(mat))
            {
                SkillLine.DISCOVERY.of(this).giveXP(125);
            }

            if(player.getInventory().getItemInMainHand() == null || player.getInventory().getItemInMainHand().getType().equals(Material.AIR))
            {
                SkillLine.UNARMED.of(this).giveXP(e.getDamage() * 7);

                if(M.r(0.15))
                {
                    dropXP(e.getEntity().getLocation(), 1);
                }
            }

            else
            {
                SkillLine.MELEE.of(this).giveXP(e.getDamage() * 7);

                if(M.r(0.15))
                {
                    dropXP(e.getEntity().getLocation(), 1);
                }
            }
        }

        if(e.getDamager() != null && e.getDamager() instanceof Projectile && ((Projectile)e.getDamager()).getShooter() != null && ((Projectile)e.getDamager()).getShooter().equals(player))
        {
            if(player.getInventory().getItemInMainHand() != null && !player.getInventory().getItemInMainHand().getType().equals(Material.AIR))
            {
                SkillLine.RANGED.of(this).giveXP(e.getDamage() + (e.getEntity().getLocation().distance(player.getLocation()) * 1.6));

                if(M.r(0.15))
                {
                    dropXP(player.getLocation(), 1);
                }
            }
        }
    }

    @EventHandler
    public void on(PlayerExpChangeEvent e)
    {
        if(e.getAmount() > 0 && e.getPlayer().equals(player))
        {
            updateLastSkill();

            if(getData().getLast().equals("none"))
            {
                return;
            }

            getSkillLine(getData().getLast()).giveXP(64 * e.getAmount());

            for(String i : bars.k())
            {
                getSkillLine(i).giveXP(27 * e.getAmount());
            }
        }
    }


    public void updateLastSkill()
    {
        long m = 0;
        String sk = "none";

        for(String i : getData().getSkillLines().keySet())
        {
            if(getSkillLine(i).getLast() > m)
            {
                m = getSkillLine(i).getLast();
                sk = i;
            }
        }

        getData().setLast(sk);
    }

    @EventHandler
    public void on(PlayerMoveEvent e)
    {
        if(!e.getPlayer().equals(player))
        {
            return;
        }

        Block b =player.getTargetBlockExact(4, FluidCollisionMode.NEVER);

        if(b != null)
        {
            if(getData().getSeenBlocks().addIfMissing(b.getBlockData().toString()))
            {
                SkillLine.DISCOVERY.of(this).giveXP(125);
                dropXP(b.getLocation(), 3);
            }
        }

        if(e.getFrom().getWorld().equals(e.getTo().getWorld())) {
            if(getData().getSeenBiomes().addIfMissing(e.getTo().getBlock().getBiome()))
            {
                SkillLine.DISCOVERY.of(this).giveXP(750);
            }

            double d = e.getFrom().distance(e.getTo()) / 7D;
            if(d < 0.01)
            {
                return;
            }

            if (e.getPlayer().isSprinting()) {
                d *= 1.225;
            }

            if (e.getPlayer().isSneaking()) {
                d *= 1.225;
            }

            if (e.getPlayer().isFlying()) {
                d *= 1.333;
            }

            if (e.getPlayer().isGliding()) {
                d *= 1.333;
            }

            if (e.getPlayer().isSwimming()) {
                d *= 1.225;
            }

            if (e.getPlayer().getRemainingAir() < e.getPlayer().getMaximumAir()) {
                d *= 1.025;
                d += (e.getPlayer().getMaximumAir() - e.getPlayer().getRemainingAir()) / 80D;
            }

            if (e.getPlayer().isRiptiding())
            {
                d *= 1.125;
            }

            if(e.getPlayer().isSneaking())
            {
                SkillLine.STEALTH.of(this).giveXP(d * 0.75);
            }

            if(e.getPlayer().isFlying() || e.getPlayer().isGliding())
            {
                SkillLine.AERONAUTICS.of(this).giveXP(d * 0.75);
            }

            if(e.getPlayer().isRiptiding())
            {
                SkillLine.AQUANAUTICS.of(this).giveXP(d * 0.25);
            }

            if(e.getPlayer().isSwimming() || e.getPlayer().getRemainingAir() < e.getPlayer().getMaximumAir())
            {
                SkillLine.AQUANAUTICS.of(this).giveXP(d * 0.75);
            }

            if(e.getPlayer().isSprinting())
            {
                SkillLine.AGILITY.of(this).giveXP(d);
            }
        }
    }

    public void notifyWisdom(String line)
    {
        not.queue(SoundNotification.builder().sound(Sound.UI_TOAST_CHALLENGE_COMPLETE)
                .pitch(0.1f).build());
        not.queue(TitleNotification.builder()
                .title(SkillLine.valueOf(line.toUpperCase()).getDisplayName() + " " + C.UNDERLINE + "MASTERED")
                .subtitle(C.LIGHT_PURPLE + "" + C.ITALIC + "Wisdom Gained")
                .in(125)
                .stay(1550)
                .out(1800)
                .build());
    }

    public void notifyLevelUp(int level, int change, String line)
    {
        int pp = 0;
        for(int i = level-change; i < level; i++)
        {
            pp += 1 + (i / 13);
            getSkillLine(line).boost(i / 382D, (int) (TimeUnit.HOURS.toMillis(i / 15) + (TimeUnit.MINUTES.toMillis(i * 3))));
        }

        getSkillLine(line).giveKnowledge(pp);

        if(level > 5) {
            if(level > 75)
            {
                not.queue(SoundNotification.builder().sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER)
                        .pitch(2F - (level / 50F)).volume(1f * (level / 150F)).build());
                not.queue(SoundNotification.builder().sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER)
                        .predelay(750)
                        .pitch((level / 50F)).volume(1f * (level / 150F)).build());
            }

            int p = pp;
            J.a(() -> {
                MultiBurst.burst.lazy(() -> {
                    if(level > 25)
                    {
                        not.queue(SoundNotification.builder().sound(Sound.ITEM_TRIDENT_THUNDER).pitch(2F - (level / 50F)).volume(1f * (level / 150F)).build());
                    }

                    if(level > 50)
                    {
                        not.queue(SoundNotification.builder().sound(Sound.BLOCK_END_PORTAL_SPAWN).pitch(2F - (level / 50F)).volume(1f * (level / 250F)).build());
                    }

                    not.queue(SoundNotification.builder().sound(Sound.BLOCK_END_PORTAL_FRAME_FILL).pitch(2F - (level / 50F)).volume(1.5f).build());
                    not.queue(TitleNotification.builder()
                            .title(SkillLine.valueOf(line.toUpperCase()).getDisplayName() + " " + C.UNDERLINE + "" + level)
                            .subtitle(C.GRAY + "+ " + p + " " + SkillLine.valueOf(line.toUpperCase()).getDisplayName() + " Knowledge (" + getSkillLine(line).getKnowledge() + ")")
                            .in(125)
                            .stay(1550)
                            .out(1800)
                            .build());


                });
            }, level > 70 ? 20 : 0);
        }
    }

    public void notifyEarn(double earned, String skill)
    {
        String xp = Form.f((long)Math.floor(earned));

        not.queue(SkillLine.valueOf(skill.toUpperCase()).getSound().withXP(earned),
                TitleNotification.builder()
                .title("")
        .subtitle(C.GRAY + "+" + C.UNDERLINE + SkillLine.valueOf(skill.toUpperCase()).getColor() + xp + " " + C.RESET + C.GRAY + "XP " + SkillLine.valueOf(skill.toUpperCase()).getDisplayName())
                .in(100)
                .stay(50 + (long) (Math.min(600, earned / 1.7)))
                .out((long) (450 + Math.min(1200, earned / 1.5)))
        .build());
    }

    @Override
    public void unregister()
    {
        super.unregister();
        save();
    }

    private PlayerData loadPlayerData() {
        File f = new File("data/players/" + player.getUniqueId() + ".json");

        if(f.exists())
        {
            try
            {
                return new Gson().fromJson(IO.readAll(f), PlayerData.class);
            }

            catch (Throwable e)
            {

            }
        }

        return new PlayerData();
    }

    @Override
    public void onTick() {
        if(updatelatch.flip())
        {
            getData().update(this);
        }

        if(savelatch.flip())
        {
            save();
        }

        if(barlatch.flip())
        {
            tickBars();
        }
    }

    public void tickBars()
    {
        for(String i : getData().getSkillLines().k())
        {
            SkillLine s = SkillLine.valueOf(i.toUpperCase());
            PlayerSkillLine ps = getData().getSkillLine(i);

            if(ps.hasEarnedWithin(3000))
            {
                if(!bars.containsKey(i))
                {
                    BossBar bb = s.newBossBar();
                    bars.put(i, bb);
                    bb.addPlayer(getPlayer());
                }

                bars.get(i).setProgress(ps.getLevelProgress());
                bars.get(i).setTitle(s.getDisplayName() + C.RESET + " " + ps.getLevel() + (ps.getMultiplier() != 1D ? (" (" +(ps.getMultiplier()-1D < 0 ? "" : "+") + Form.pc(ps.getMultiplier()-1D, 0) + ")") : ""));
            }

            else
            {
                if(bars.containsKey(i))
                {
                    BossBar b = bars.remove(i);
                    b.removeAll();
                    b.setVisible(false);
                }
            }
        }
    }
}
