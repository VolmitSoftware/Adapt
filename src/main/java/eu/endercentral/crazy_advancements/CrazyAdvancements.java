package eu.endercentral.crazy_advancements;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import eu.endercentral.crazy_advancements.AdvancementDisplay.AdvancementFrame;
import eu.endercentral.crazy_advancements.manager.AdvancementManager;
import net.minecraft.network.protocol.game.PacketPlayOutAdvancements;
import net.minecraft.network.protocol.game.PacketPlayOutSelectAdvancementTab;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Warning;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_17_R1.command.ProxiedNativeCommandSender;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class CrazyAdvancements extends JavaPlugin implements Listener {

    public static UUID CHAT_MESSAGE_UUID = new UUID(0, 0);

    private static CrazyAdvancements instance;

    private AdvancementManager fileAdvancementManager;
    private static AdvancementPacketReceiver packetReciever;

    private static final ArrayList<Player> initiatedPlayers = new ArrayList<>();
    private static final ArrayList<AdvancementManager> managers = new ArrayList<>();
    private static boolean announceAdvancementMessages = true;
    private static final HashMap<String, NameKey> openedTabs = new HashMap<>();


    private static boolean useUUID;

    public CrazyAdvancements() {
        if(instance == null) {
            instance = this;
        }
    }

    @Override
    public void onLoad() {
        instance = this;
        fileAdvancementManager = new AdvancementManager();
    }

    @Override
    public void onEnable() {
        packetReciever = new AdvancementPacketReceiver();

        //Registering Players
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {

            @Override
            public void run() {
                String path = CrazyAdvancements.getInstance().getDataFolder().getAbsolutePath() + File.separator + "advancements" + File.separator + "main" + File.separator;
                File saveLocation = new File(path);
                loadAdvancements(saveLocation);

                for(Player player : Bukkit.getOnlinePlayers()) {
                    fileAdvancementManager.addPlayer(player);
                    packetReciever.initPlayer(player);
                    initiatedPlayers.add(player);
                }
            }
        }, 5);
        //Registering Events
        Bukkit.getPluginManager().registerEvents(this, this);

        reloadConfig();
        FileConfiguration config = getConfig();
        config.addDefault("useUUID", true);
        saveConfig();
        useUUID = config.getBoolean("useUUID");
    }

    private void loadAdvancements(File location) {
        location.mkdirs();
        File[] files = location.listFiles();
        Arrays.sort(files);
        for(File file : files) {
            if(file.isDirectory()) {
                loadAdvancements(file);
            } else if(file.getName().endsWith(".json")) {
                try {
                    FileReader os = new FileReader(file);

                    JsonParser parser = new JsonParser();
                    JsonElement element = parser.parse(os);
                    os.close();

                    Advancement add = Advancement.fromJSON(element);
                    fileAdvancementManager.addAdvancement(add);
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onDisable() {
        for(AdvancementManager manager : managers) {
            for(Advancement advancement : manager.getAdvancements()) {
                manager.removeAdvancement(advancement);
            }
        }
        PacketPlayOutAdvancements packet = new PacketPlayOutAdvancements(true, new ArrayList<>(), new HashSet<>(), new HashMap<>());
        for(Player p : Bukkit.getOnlinePlayers()) {
            packetReciever.close(p, packetReciever.getHandlers().get(p.getName()));
            ((CraftPlayer) p).getHandle().b.sendPacket(packet);
        }
    }

    /**
     * Creates a new instance of an advancement manager
     *
     * @param players
     *     All players that should be in the new manager from the start, can be changed at any time
     * @return the generated advancement manager
     * @deprecated Use the AdvancementManager constructor instead of this method
     */
    @Deprecated(since = "1.13.10")
    public static AdvancementManager getNewAdvancementManager(Player... players) {
        return AdvancementManager.getNewAdvancementManager(players);
    }

    /**
     * Clears the active tab
     *
     * @param player
     *     The player whose Tab should be cleared
     */
    public static void clearActiveTab(Player player) {
        setActiveTab(player, null, true);
    }

    /**
     * Sets the active tab
     *
     * @param player
     *     The player whose Tab should be changed
     * @param rootAdvancement
     *     The name of the tab to change to
     */
    public static void setActiveTab(Player player, String rootAdvancement) {
        setActiveTab(player, new NameKey(rootAdvancement));
    }

    /**
     * Sets the active tab
     *
     * @param player
     *     The player whose Tab should be changed
     * @param rootAdvancement
     *     The name of the tab to change to
     */
    public static void setActiveTab(Player player, @Nullable NameKey rootAdvancement) {
        setActiveTab(player, rootAdvancement, true);
    }

    static void setActiveTab(Player player, NameKey rootAdvancement, boolean update) {
        if(update) {
            PacketPlayOutSelectAdvancementTab packet = new PacketPlayOutSelectAdvancementTab(rootAdvancement == null ? null : rootAdvancement.getMinecraftKey());
            ((CraftPlayer) player).getHandle().b.sendPacket(packet);
        }
        openedTabs.put(player.getName(), rootAdvancement);
    }

    /**
     * @param player
     *     Player to check
     * @return The active Tab
     */
    public static NameKey getActiveTab(Player player) {
        return openedTabs.get(player.getName());
    }


    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {

            @Override
            public void run() {
                fileAdvancementManager.addPlayer(player);
                initiatedPlayers.add(player);
            }
        }, 5);
        packetReciever.initPlayer(player);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        packetReciever.close(e.getPlayer(), packetReciever.getHandlers().get(e.getPlayer().getName()));
        initiatedPlayers.remove(e.getPlayer());
    }

    @Warning(reason = "Unsafe")
    public static ArrayList<Player> getInitiatedPlayers() {
        return initiatedPlayers;
    }

    public static CrazyAdvancements getInstance() {
        return instance;
    }

    /**
     * @return <b>true</b> if advancement messages should be shown by default<br><b>false</b> if all advancement
     * messages will be hidden
     */
    public static boolean isAnnounceAdvancementMessages() {
        return announceAdvancementMessages;
    }

    /**
     * Changes if advancement messages should be shown by default
     */
    public static void setAnnounceAdvancementMessages(boolean announceAdvancementMessages) {
        CrazyAdvancements.announceAdvancementMessages = announceAdvancementMessages;
    }

    /**
     * @return <b>true</b> if Player Progress is saved by their UUID<br><b>false</b> if Player Progress is saved by
     * their Name (not recommended)<br><b>Saving and Loading Progress via UUID will might not work as expected with this
     * Setting!!<b>
     */
    public static boolean isUseUUID() {
        return useUUID;
    }

    private final String noPermission = "�cI'm sorry but you do not have permission to perform this command. Please contact the server administrator if you believe that this is in error.";
    private final String commandIncompatible = "�cThis Command is incompatible with your Arguments!";
    private final List<String> selectors = Arrays.asList("@a", "@p", "@s", "@r");

    private boolean startsWithSelector(String arg) {
        for(String selector : selectors) {
            if(arg.startsWith(selector)) return true;
        }
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if(cmd.getName().equalsIgnoreCase("showtoast")) {
            if(sender.hasPermission("crazyadvancements.command.*") || sender.hasPermission("crazyadvancements.command.showtoast")) {
                if(args.length >= 3) {


                    try {
                        if(startsWithSelector(args[0])) {
                            String argsString = args[1];
                            for(int i = 2; i < args.length; i++) {
                                argsString += " " + args[i];
                            }
                            boolean opBefore = sender.isOp();
                            sender.setOp(true);
                            Bukkit.dispatchCommand(sender, "minecraft:execute as " + args[0] + " at @s run " + label + " self " + argsString);
                            sender.setOp(opBefore);
                            return true;
                        }
                        Player player = args[0].equalsIgnoreCase("self") ? (sender instanceof Player) ? (Player) sender : (sender instanceof ProxiedNativeCommandSender) ? (Player) ((ProxiedNativeCommandSender) sender).getCallee() : null : Bukkit.getPlayer(args[0]);

                        if(player != null && player.isOnline()) {
                            Material mat = getMaterial(args[1]);

                            if(mat != null && mat.isItem()) {
                                String message = args[2];
                                if(args.length > 3) {
                                    for(int i = 3; i < args.length; i++) {
                                        message += " " + args[i];
                                    }
                                }

                                AdvancementDisplay display = new AdvancementDisplay(mat, message, "", AdvancementFrame.TASK, true, true, AdvancementVisibility.ALWAYS);
                                Advancement advancement = new Advancement(null, new NameKey("toast", "message"), display);
                                advancement.displayToast(player);

                                sender.sendMessage("�aSuccessfully displayed Toast to �b" + player.getName() + "�a!");
                            } else {
                                sender.sendMessage("�c'" + args[1] + "' isn't a valid Item Material");
                            }

                        } else {
                            sender.sendMessage("�cCan't find Player '�e" + args[0] + "�c'");
                        }
                    } catch(Exception ex) {
                        sender.sendMessage(commandIncompatible);
                    }


                } else {
                    sender.sendMessage("�cUsage: �r" + cmd.getUsage());
                }
            } else {
                sender.sendMessage(noPermission);
            }
            return true;
        }

        if(cmd.getName().equalsIgnoreCase("cagrant") || cmd.getName().equalsIgnoreCase("carevoke")) {
            boolean grant = cmd.getName().equalsIgnoreCase("cagrant");
            if(sender.hasPermission("crazyadvancements.command.*") || sender.hasPermission("crazyadvancements.command.grantrevoke")) {
                if(args.length >= 3) {

                    try {
                        if(startsWithSelector(args[0])) {
                            String argsString = args[1];
                            for(int i = 2; i < args.length; i++) {
                                argsString += " " + args[i];
                            }
                            boolean opBefore = sender.isOp();
                            sender.setOp(true);
                            Bukkit.dispatchCommand(sender, "minecraft:execute as " + args[0] + " at @s run " + label + " self " + argsString);
                            sender.setOp(opBefore);
                            return true;
                        }
                        Player player = args[0].equalsIgnoreCase("self") ? (sender instanceof Player) ? (Player) sender : (sender instanceof ProxiedNativeCommandSender) ? (Player) ((ProxiedNativeCommandSender) sender).getCallee() : null : Bukkit.getPlayer(args[0]);

                        if(player != null && player.isOnline()) {
                            AdvancementManager manager = args[1].equalsIgnoreCase("file") ? fileAdvancementManager : AdvancementManager.getAccessibleManager(args[1]);

                            if(manager != null) {

                                if(manager.getPlayers().contains(player)) {
                                    Advancement advancement = manager.getAdvancement(new NameKey(args[2]));

                                    if(advancement != null) {

                                        if(args.length >= 4) {

                                            String[] convertedCriteria = Arrays.copyOfRange(args, 3, args.length);

                                            if(grant) {
                                                if(!advancement.getProgress(player).isDone())
                                                    manager.grantCriteria(player, advancement, convertedCriteria);
                                            } else {
                                                manager.revokeCriteria(player, advancement, convertedCriteria);
                                            }

                                            String criteriaString = "�c" + convertedCriteria[0];
                                            if(convertedCriteria.length > 1) {
                                                for(String criteria : Arrays.copyOfRange(convertedCriteria, 1, convertedCriteria.length - 1)) {
                                                    criteriaString += "�a, �c" + criteria;
                                                }
                                                criteriaString += " �aand �c" + convertedCriteria[convertedCriteria.length - 1];
                                            }

                                            sender.sendMessage("�aSuccessfully " + (grant ? "granted" : "revoked") + " Criteria " + criteriaString + " �afor '�e" + advancement.getName() + "�a' " + (grant ? "to" : "from") + " �b" + player.getName());

                                        } else {
                                            if(grant) {
                                                if(!advancement.getProgress(player).isDone())
                                                    manager.grantAdvancement(player, advancement);
                                            } else {
                                                manager.revokeAdvancement(player, advancement);
                                            }

                                            sender.sendMessage("�aSuccessfully " + (grant ? "granted" : "revoked") + " Advancement '�e" + advancement.getName() + "�a' " + (grant ? "to" : "from") + " �b" + player.getName());
                                        }

                                    } else {
                                        sender.sendMessage("�cAdvancement with Name '�e" + args[2] + "�c' does not exist in '�e" + args[1] + "�c'");
                                    }

                                } else {
                                    sender.sendMessage("�c'�e" + args[1] + "�c' does not contain Player '�e" + args[0] + "�c'");
                                }
                            } else {
                                sender.sendMessage("�cManager with Name '�e" + args[1] + "�c' does not exist");
                            }
                        } else {
                            sender.sendMessage("�cCan't find Player '�e" + args[0] + "�c'");
                        }

                    } catch(Exception ex) {
                        sender.sendMessage(commandIncompatible);
                    }

                } else {
                    sender.sendMessage("�cUsage: �r" + cmd.getUsage());
                }
            } else {
                sender.sendMessage(noPermission);
            }
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        ArrayList<String> tab = new ArrayList<>();

        if(cmd.getName().equalsIgnoreCase("showtoast")) {

            if(args.length == 1) {
                for(String selector : selectors) {
                    if(selector.toLowerCase().startsWith(args[0].toLowerCase())) {
                        tab.add(selector);
                    }
                }
                for(Player player : Bukkit.getOnlinePlayers()) {
                    if(player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        tab.add(player.getName());
                    }
                }
            } else if(args.length == 2) {
                for(Material mat : Material.values()) {
                    if(mat.isItem() && mat.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                        tab.add(mat.name().toLowerCase());
                    }
                }
            }

        }

        if(cmd.getName().equalsIgnoreCase("cagrant") || cmd.getName().equalsIgnoreCase("carevoke")) {

            if(args.length == 1) {
                for(String selector : selectors) {
                    if(selector.toLowerCase().startsWith(args[0].toLowerCase())) {
                        tab.add(selector);
                    }
                }
                for(Player player : Bukkit.getOnlinePlayers()) {
                    if(player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        tab.add(player.getName());
                    }
                }
            } else if(args.length == 2) {
                if("file".startsWith(args[1])) {
                    tab.add("file");
                }
                for(AdvancementManager manager : AdvancementManager.getAccessibleManagers()) {
                    if(manager.getName().startsWith(args[1].toLowerCase())) {
                        tab.add(manager.getName());
                    }
                }
            } else if(args.length == 3) {
                AdvancementManager manager = AdvancementManager.getAccessibleManager(args[1]);
                if(manager != null) {
                    for(Advancement advancement : manager.getAdvancements()) {
                        if(advancement.getName().toString().startsWith(args[2].toLowerCase()) || advancement.getName().getKey().startsWith(args[2].toLowerCase())) {
                            tab.add(advancement.getName().toString());
                        }
                    }
                }
            } else if(args.length >= 4) {
                AdvancementManager manager = AdvancementManager.getAccessibleManager(args[1]);
                if(manager != null) {
                    Advancement advancement = manager.getAdvancement(new NameKey(args[2]));
                    if(advancement != null) {
                        for(String criterion : advancement.getSavedCriteria().keySet()) {
                            if(criterion.toLowerCase().startsWith(args[args.length - 1].toLowerCase())) {
                                tab.add(criterion);
                            }
                        }
                    }
                }
            }

        }

        return tab;
    }

    private Material getMaterial(String input) {
        for(Material mat : Material.values()) {
            if(mat.name().equalsIgnoreCase(input)) {
                return mat;
            }
        }
        return null;
    }


}