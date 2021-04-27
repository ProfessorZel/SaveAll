package ru.astakhovmd.SaveAll;


import com.mojang.authlib.GameProfile;
import net.minecraft.server.v1_16_R3.EntityPlayer;
import net.minecraft.server.v1_16_R3.MinecraftServer;
import net.minecraft.server.v1_16_R3.PlayerInteractManager;
import net.minecraft.server.v1_16_R3.WorldServer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockIterator;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SaveAll extends JavaPlugin {

    public static SaveAll instance;
    private String[] messages;
    public HashMap<UUID,MyPlayer> myPlayers = new HashMap<>();
    //for logging to the console and log file
    private static Logger log;


    protected final static String dataLayerFolderPath = "plugins" + File.separator + "SaveAll";
    final static String playerDataFolderPath = dataLayerFolderPath + File.separator + "PlayerData";
    final static String configFilePath = dataLayerFolderPath + File.separator + "config.yml";
    final static String messagesFilePath = dataLayerFolderPath + File.separator + "messages.yml";
    public int config_LongHandLen;
    public Material config_Tool;
    public Set<Material> config_expropriate;
    public Set<Material> config_expropriate_from;

    //determines whether a block type is an inventory holder.  uses a caching strategy to save cpu time
    public final ConcurrentHashMap<Material, Boolean> inventoryHolderCache = new ConcurrentHashMap<>();

    public void onEnable()
    {

        instance = this;
        log = instance.getLogger();

        AddLogEntry("StartUp!");
        this.loadConfig();
        this.loadPlayerCache();

        PluginManager pluginManager = this.getServer().getPluginManager();

        PlayerEventHandler playerEventHandler = new PlayerEventHandler(this);
        pluginManager.registerEvents(playerEventHandler, this);
    }

    private void loadPlayerCache() {

        //myPlayers.put(UUID.fromString("test"),null);
    }


    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player player = null;
        if (sender instanceof Player)
        {
            player = (Player) sender;
        }


        if (cmd.getName().equalsIgnoreCase("expropriate"))
        {
            //requires exactly one parameter, the other player's name
            if (args.length != 1) return false;

            OfflinePlayer tested = resolvePlayerByName(args[0]);
            if (tested==null){
                SaveAll.sendMessage(player,TextMode.Err,"Player not found...1");
                return true;
            }

            Player target = loadPlayer(tested);
            //tested.getUniqueId();
            if (target==null){
                SaveAll.sendMessage(player,TextMode.Err,"Player not found...2");
                return true;
            }
                target.loadData();
                UUID uuid = target.getUniqueId();
                //SaveAll.instance.myPlayers.put(uuid, new MyPlayer());
                //ItemStack item = inventory.getItem(0);


                try {
                    expropriate(target.getInventory(),uuid,true);
                } catch (IOException e) {
                    e.printStackTrace();
                    SaveAll.sendMessage(player,TextMode.Err,"Save error...");
                    //return true;
                }
                try {
                    expropriate(target.getEnderChest(),uuid);
                } catch (IOException e) {
                    e.printStackTrace();
                    SaveAll.sendMessage(player,TextMode.Err,"Save error...");
                    //return true;
                }
                target.saveData();
                if (player!=null){
                    MyPlayer cashed = new MyPlayer();
                    long now = Calendar.getInstance().getTimeInMillis();
                    cashed.lastExpropriation = now;
                    cashed.target = uuid;
                    SaveAll.instance.myPlayers.put(player.getUniqueId(), cashed);
                }
                /*try{
                    GriefPrevention plugin = (GriefPrevention) Bukkit.getPluginManager().getPlugin("griefprevention");
                    Vector<me.ryanhamshire.GriefPrevention.Claim> claims = plugin.dataStore.getPlayerData(target.getUniqueId()).getClaims();
                    for (me.ryanhamshire.GriefPrevention.Claim claim:claims) {
                        claim.

                    }
                }catch (Exception e){
                    e.printStackTrace();
                }*/
                //.getSize();

            sendMessage(player,TextMode.Success,"Saved!");
            return true;
        }
        else if (cmd.getName().equalsIgnoreCase("receiveinv"))
        {
            //requires exactly one parameter, the other player's name
            if (args.length != 1) return false;

            OfflinePlayer target = resolvePlayerByName(args[0]);
            if (target!=null){


                Player target_player = target.getPlayer();
                try {
                    restore(player,target_player.getUniqueId());
                } catch (IOException e) {
                    e.printStackTrace();
                    SaveAll.sendMessage(player,TextMode.Err,"No data...");
                }
                //.getSize();
            }else{
                SaveAll.sendMessage(player,TextMode.Err,"Player not found...");
            }
            sendMessage(player,TextMode.Success,"Restored!");
            return true;
        }else if (cmd.getName().equalsIgnoreCase("restoreinv"))
        {
            //requires exactly one parameter, the other player's name
            if (args.length != 1) return false;

            OfflinePlayer target = resolvePlayerByName(args[0]);
            if (target!=null){


                Player target_player = target.getPlayer();
                try {
                    restore(player,target_player.getUniqueId());
                } catch (IOException e) {
                    e.printStackTrace();
                    SaveAll.sendMessage(player,TextMode.Err,"No data...");
                }
                //.getSize();
            }else{
                SaveAll.sendMessage(player,TextMode.Err,"Player not found...");
            }
            sendMessage(player,TextMode.Success,"Restored!");
            return true;
        }

        return false;//super.onCommand(sender, command, label, args);
    }

    public void add_to_list(ArrayList<ItemStack> list,ItemStack item){
        for (ItemStack stack:list){
            if (stack.isSimilar(item)){
                stack.setAmount(stack.getAmount()+item.getAmount());
                return;
            }
        }
        list.add(item.clone());
    }
    public boolean blacklist(ItemStack item){
        return config_expropriate.contains(item.getType());
    }
    public ArrayList<ItemStack> expropriate(Inventory inventory, UUID uuid) throws IOException {
        return this.expropriate(inventory, uuid,false);
    }

    public ArrayList<ItemStack> expropriate(Inventory inventory, UUID uuid, boolean overwrite) throws IOException {
        //YamlConfiguration c = new YamlConfiguration();
        YamlConfiguration c = new YamlConfiguration();
        ArrayList<ItemStack> count = new ArrayList<>();
        ArrayList<ItemStack> expropriated = null;
        if (!overwrite){
            c = YamlConfiguration.loadConfiguration(new File(playerDataFolderPath, uuid.toString()+".yml"));
            expropriated = (ArrayList<ItemStack>) c.get("inventory.content");
        }
        if (expropriated==null) expropriated = new ArrayList<>();


        ItemStack[] inv = inventory.getContents();
        inv =  Arrays.stream(inv).filter(Objects::nonNull).toArray(ItemStack[]::new);



        for (ItemStack stack:inv){
            if(stack.getItemMeta() instanceof BlockStateMeta){
                //sendMessage(p,TextMode.Instr,"it is shulker (step1)");
                BlockStateMeta im = (BlockStateMeta)stack.getItemMeta();
                if(im.getBlockState() instanceof ShulkerBox){
                    //sendMessage(p,TextMode.Instr,"it is shulker (step2)");
                    ShulkerBox shulker = (ShulkerBox) im.getBlockState();
                    ItemStack[] shulker_inv = shulker.getInventory().getContents();
                    shulker_inv =  Arrays.stream(shulker_inv).filter(itemStack -> itemStack!=null&&!itemStack.getType().isAir()).toArray(ItemStack[]::new);
                    for (ItemStack shulker_stack:shulker_inv){
                        if (blacklist(shulker_stack))
                        {
                            add_to_list(expropriated,shulker_stack);
                            add_to_list(count,shulker_stack);

                            shulker_stack.setAmount(0);
                        }
                    }
                    shulker_inv =  Arrays.stream(shulker_inv).filter(itemStack -> itemStack!=null&&!itemStack.getType().isAir()&&itemStack.getAmount()>0).toArray(ItemStack[]::new);
                    shulker.getInventory().setContents(shulker_inv);
                    im.setBlockState(shulker);
                    stack.setItemMeta(im);
                }
            }else if (blacklist(stack))
            {
                add_to_list(expropriated,stack);
                add_to_list(count,stack);
                stack.setAmount(0);

            }
        }
        //ItemStack[] armor_inv = user_inventory.getContents();
        c.set("inventory.content", (expropriated.toArray()));
        c.save(new File(playerDataFolderPath, uuid.toString()+".yml"));

        //p.updateInventory();
        return  count;
    }

    public void restore(Player target, UUID uuid) throws IOException {
        YamlConfiguration c = YamlConfiguration.loadConfiguration(new File(playerDataFolderPath, uuid.toString()+".yml"));
        ArrayList<ItemStack> items = (ArrayList<ItemStack>) c.get("inventory.content");
        if (items==null) return;
        //sendMessage(p,TextMode.Info,"!=null");
        ArrayList<ItemStack> contents = new ArrayList<>();
        for (ItemStack stack: items) {
            if (stack.getAmount() > stack.getMaxStackSize()) {
                int n = stack.getAmount() / stack.getMaxStackSize();
                int rest = stack.getAmount() % stack.getMaxStackSize();
                //sendMessage(p,TextMode.Info,"n"+n+"r"+rest);
                stack.setAmount(stack.getMaxStackSize());
                for (int i = 0; i < n; i++) {
                    contents.add(stack.clone());
                }
                if (rest>0){
                    stack.setAmount(rest);
                    contents.add(stack.clone());
                }
            }else{
                contents.add(stack.clone());
            }

        }
        ArrayList<ItemStack> shulkers = new ArrayList<>();

        ItemStack shulker = new ItemStack(Material.PINK_SHULKER_BOX);
        BlockStateMeta blockStateMeta = (BlockStateMeta) shulker.getItemMeta();
        ShulkerBox shulkerBox = (ShulkerBox) blockStateMeta.getBlockState();
        int size = shulkerBox.getInventory().getSize();
        ItemStack[] content_one = new ItemStack[size];
        while (contents.size()>0){
            for (int i = 0; i < size; i++) {
                content_one[i] = contents.remove(0);
                if (contents.size()==0) break;
            }
            shulkerBox.getInventory().setContents(content_one);
            blockStateMeta.setBlockState(shulkerBox);
            shulker.setItemMeta(blockStateMeta);
            shulkers.add(shulker.clone());
        }


        //sendMessage(p,TextMode.Info,">for");
        ItemStack[] content = shulkers.toArray(new ItemStack[0]);

        if (content!=null){
            //sendMessage(p,TextMode.Info,"!=null");
            target.getInventory().addItem(content);
            target.updateInventory();
        }
        //return true;
    }
    //sends a color-coded message to a player
    public static void sendMessage(Player player, ChatColor color, Messages messageID,  String... args)
    {
        String message = SaveAll.instance.getMessage(messageID, args);
        sendMessage(player, color, message);
    }
    synchronized public String getMessage(Messages messageID, String... args)
    {
        String message = messages[messageID.ordinal()];

        for (int i = 0; i < args.length; i++)
        {
            String param = args[i];
            message = message.replace("{" + i + "}", param);
        }

        return message;
    }
    //sends a color-coded message to a player
    public static void sendMessage(Player player, ChatColor color, String message)
    {
        if (message == null || message.length() == 0) return;

        if (player == null)
        {
            SaveAll.AddLogEntry(color + message);
        }
        else
        {
            player.sendMessage(color + message);
        }
    }



    static Block getTargetBlock(Player player, int maxDistance) throws IllegalStateException
    {
        Location eye = player.getEyeLocation();
        Material eyeMaterial = eye.getBlock().getType();
        boolean passThroughWater = (eyeMaterial == Material.WATER);
        BlockIterator iterator = new BlockIterator(player.getLocation(), player.getEyeHeight(), maxDistance);
        Block result = player.getLocation().getBlock().getRelative(BlockFace.UP);
        while (iterator.hasNext())
        {
            result = iterator.next();
            Material type = result.getType();
            if (type != Material.AIR &&
                    (!passThroughWater || type != Material.WATER) &&
                    type != Material.GRASS &&
                    type != Material.SNOW) return result;
        }

        return result;
    }
    public ItemStack getItemInHand(Player player, EquipmentSlot hand)
    {
        if (hand == EquipmentSlot.OFF_HAND) return player.getInventory().getItemInOffHand();
        return player.getInventory().getItemInMainHand();
    }
    private void loadMessages()
    {
        Messages[] messageIDs = Messages.values();
        this.messages = new String[Messages.values().length];

        HashMap<String, CustomizableMessage> defaults = new HashMap<>();

        //initialize defaults
        this.addDefault(defaults, Messages.RespectingClaims, "Now respecting claims.", null);
        this.addDefault(defaults, Messages.IgnoringClaims, "Now ignoring claims.", null);
        this.addDefault(defaults, Messages.SuccessfulAbandon, "Claims abandoned.  You now have {0} available claim blocks.", "0: remaining blocks");
        this.addDefault(defaults, Messages.RestoreNatureActivate, "Ready to restore some nature!  Right click to restore nature, and use /BasicClaims to stop.", null);
        this.addDefault(defaults, Messages.RestoreNatureAggressiveActivate, "Aggressive mode activated.  Do NOT use this underneath anything you want to keep!  Right click to aggressively restore nature, and use /BasicClaims to stop.", null);
        this.addDefault(defaults, Messages.FillModeActive, "Fill mode activated with radius {0}.  Right click an area to fill.", "0: fill radius");
        this.addDefault(defaults, Messages.TransferClaimPermission, "That command requires the administrative claims permission.", null);
        this.addDefault(defaults, Messages.TransferClaimMissing, "There's no claim here.  Stand in the administrative claim you want to transfer.", null);
       //load the config file
        FileConfiguration config = YamlConfiguration.loadConfiguration(new File(messagesFilePath));

        //for each message ID
        for (Messages messageID : messageIDs)
        {
            //get default for this message
            CustomizableMessage messageData = defaults.get(messageID.name());

            //if default is missing, log an error and use some fake data for now so that the plugin can run
            if (messageData == null)
            {
                SaveAll.AddLogEntry("Missing message for " + messageID.name() + ".  Please contact the developer.");
                messageData = new CustomizableMessage(messageID, "Missing message!  ID: " + messageID.name() + ".  Please contact a server admin.", null);
            }

            //read the message from the file, use default if necessary
            this.messages[messageID.ordinal()] = config.getString("ru.astakhovmd.SaveAll.SaveAll.Messages." + messageID.name() + ".Text", messageData.text);
            config.set("ru.astakhovmd.SaveAll.SaveAll.Messages." + messageID.name() + ".Text", this.messages[messageID.ordinal()]);



            if (messageData.notes != null)
            {
                messageData.notes = config.getString("ru.astakhovmd.SaveAll.SaveAll.Messages." + messageID.name() + ".Notes", messageData.notes);
                config.set("ru.astakhovmd.SaveAll.SaveAll.Messages." + messageID.name() + ".Notes", messageData.notes);
            }
        }

        //save any changes
        try
        {
            config.options().header("Use a YAML editor like NotepadPlusPlus to edit this file.  \nAfter editing, back up your changes before reloading the server in case you made a syntax error.  \nUse dollar signs ($) for formatting codes, which are documented here: http://minecraft.gamepedia.com/Formatting_codes");
            config.save(SaveAll.messagesFilePath);
        }
        catch (IOException exception)
        {
            SaveAll.AddLogEntry("Unable to write to the configuration file at \"" + SaveAll.messagesFilePath + "\"");
        }

        defaults.clear();
        System.gc();
    }
    private void addDefault(HashMap<String, CustomizableMessage> defaults,
                            Messages id, String text, String notes)
    {
        CustomizableMessage message = new CustomizableMessage(id, text, notes);
        defaults.put(id.name(), message);
    }
    private void loadConfig()
    {
        //load the config if it exists
        FileConfiguration config = YamlConfiguration.loadConfiguration(new File(configFilePath));
        FileConfiguration outConfig = new YamlConfiguration();
        outConfig.options().header("Default values are perfect for most servers.  If you want to customize and have a question, look for the answer here first: http://dev.bukkit.org/bukkit-plugins/grief-prevention/pages/setup-and-configuration/");




        //default for claim investigation tool
        String CutToolMaterialName = Material.STICK.name();

        /* investigation tool from config
        CutToolMaterialName = config.getString("SaveAll.CutTool", CutToolMaterialName);

        //validate investigation tool
        this.config_CutTool = Material.getMaterial(CutToolMaterialName);
        if (this.config_CutTool == null)
        {
            SaveAll.AddLogEntry("ERROR: Material " + CutToolMaterialName + " not found.  Defaulting to the stick.  Please update your config.yml.");
            this.config_CutTool = Material.STICK;
        }
        */
        //default for claim creation/modification tool
        Material DefaultTool = Material.GOLDEN_SWORD;

        String ToolMaterialName = DefaultTool.name();
        //get modification tool from config
        ToolMaterialName = config.getString("SaveAll.Tool", ToolMaterialName);
        //validate modification tool
        this.config_Tool = Material.getMaterial(ToolMaterialName);
        if (this.config_Tool == null)
        {
            SaveAll.AddLogEntry("ERROR: Material " + ToolMaterialName + " not found.  Defaulting to the golden shovel.  Please update your config.yml.");
            this.config_Tool = DefaultTool;
        }

        //default blocks to expropriate
        this.config_expropriate = EnumSet.noneOf(Material.class);
        //this.config_siege_blocks.add(Material.);
        this.config_expropriate.add(Material.GOLD_INGOT);
        this.config_expropriate.add(Material.GOLD_NUGGET);
        this.config_expropriate.add(Material.GOLD_ORE);
        this.config_expropriate.add(Material.GOLD_BLOCK);
        this.config_expropriate.add(Material.NETHER_GOLD_ORE);
        this.config_expropriate.add(Material.DIAMOND);
        this.config_expropriate.add(Material.DIAMOND_ORE);
        this.config_expropriate.add(Material.DIAMOND_BLOCK);
        this.config_expropriate.add(Material.NETHERITE_SCRAP);
        this.config_expropriate.add(Material.NETHERITE_INGOT);
        this.config_expropriate.add(Material.NETHERITE_BLOCK);
        List<String> BlocksBlackList;

        //try to load the list from the config file
        if (config.isList("SaveAll.Expropriate"))
        {
            BlocksBlackList = config.getStringList("SaveAll.Expropriate");

            //load materials
            this.config_expropriate = parseMaterialListFromConfig(BlocksBlackList);
        }
        //if it fails, use default siege block list instead
        else
        {
            BlocksBlackList = this.config_expropriate.stream().map(Material::name).collect(Collectors.toList());
        }

        //default blocks to expropriate
        this.config_expropriate_from = EnumSet.noneOf(Material.class);
        //this.config_siege_blocks.add(Material.);
        this.config_expropriate_from.add(Material.CHEST);
        this.config_expropriate_from.add(Material.TRAPPED_CHEST);
        this.config_expropriate_from.add(Material.BARREL);
        this.config_expropriate_from.add(Material.HOPPER);
        this.config_expropriate_from.add(Material.FURNACE);

        this.config_expropriate_from.add(Material.CHEST_MINECART);
        this.config_expropriate_from.add(Material.HOPPER_MINECART);
        this.config_expropriate_from.add(Material.FURNACE_MINECART);

        this.config_expropriate_from.add(Material.SHULKER_BOX);
        this.config_expropriate_from.add(Material.PINK_SHULKER_BOX);
        this.config_expropriate_from.add(Material.BLACK_SHULKER_BOX);
        this.config_expropriate_from.add(Material.BLUE_SHULKER_BOX);
        this.config_expropriate_from.add(Material.BROWN_SHULKER_BOX);
        this.config_expropriate_from.add(Material.CYAN_SHULKER_BOX);
        this.config_expropriate_from.add(Material.GREEN_SHULKER_BOX);
        this.config_expropriate_from.add(Material.LIGHT_BLUE_SHULKER_BOX);
        this.config_expropriate_from.add(Material.GRAY_SHULKER_BOX);
        this.config_expropriate_from.add(Material.LIGHT_GRAY_SHULKER_BOX);
        this.config_expropriate_from.add(Material.LIME_SHULKER_BOX);
        this.config_expropriate_from.add(Material.MAGENTA_SHULKER_BOX);
        this.config_expropriate_from.add(Material.ORANGE_SHULKER_BOX);
        this.config_expropriate_from.add(Material.YELLOW_SHULKER_BOX);
        this.config_expropriate_from.add(Material.PURPLE_SHULKER_BOX);
        this.config_expropriate_from.add(Material.RED_SHULKER_BOX);
        List<String> BlocksFromList;

        //try to load the list from the config file
        if (config.isList("SaveAll.From"))
        {
            BlocksFromList = config.getStringList("SaveAll.From");

            //load materials
            this.config_expropriate_from = parseMaterialListFromConfig(BlocksFromList);
        }
        //if it fails, use default siege block list instead
        else
        {
            BlocksFromList = this.config_expropriate_from.stream().map(Material::name).collect(Collectors.toList());
        }



        outConfig.set("SaveAll.Tool", this.config_Tool.name());
        outConfig.set("SaveAll.LongHandLen", this.config_LongHandLen);
        outConfig.set("SaveAll.Expropriate", BlocksBlackList);
        outConfig.set("SaveAll.From", BlocksFromList);
        try
        {
            outConfig.save(SaveAll.configFilePath);
        }
        catch (IOException exception)
        {
            AddLogEntry("Unable to write to the configuration file at \"" + SaveAll.configFilePath + "\"");
        }


    }
    public static synchronized void AddLogEntry(String entry,  boolean excludeFromServerLogs)
    {
        if (!excludeFromServerLogs) log.info(entry);
    }

    public static synchronized void AddLogEntry(String entry)
    {
        AddLogEntry(entry, false);
    }
    public OfflinePlayer resolvePlayerByName(String name)
    {
        //try online players first
        Player targetPlayer = this.getServer().getPlayerExact(name);
        if (targetPlayer != null) return targetPlayer;

        //MyPlayer actioner = SaveAll.instance.myPlayers.get(player.getUniqueId());

        return this.getServer().getOfflinePlayer(name);
    }

    private Set<Material> parseMaterialListFromConfig(List<String> stringsToParse)
    {
        Set<Material> materials = EnumSet.noneOf(Material.class);

        //for each string in the list
        for (int i = 0; i < stringsToParse.size(); i++)
        {
            String string = stringsToParse.get(i);

            //defensive coding
            if (string == null) continue;

            //try to parse the string value into a material
            Material material = Material.getMaterial(string.toUpperCase());

            //null value returned indicates an error parsing the string from the config file
            if (material == null)
            {
                //check if string has failed validity before
                if (!string.contains("can't"))
                {
                    //update string, which will go out to config file to help user find the error entry
                    stringsToParse.set(i, string + "     <-- can't understand this entry, see BukkitDev documentation");

                    //warn about invalid material in log
                    //SaveAll.AddLogEntry(String.format("ERROR: Invalid material %s.  Please update your config.yml.", string));
                }
                SaveAll.AddLogEntry(String.format("ERROR: Invalid material %s.  Please update your config.yml.", string));

            }

            //otherwise material is valid, add it
            else
            {
                materials.add(material);
            }
        }

        return materials;
    }


    public static EntityPlayer getHandle(final Player player) {
        if (player instanceof CraftPlayer) {
            return ((CraftPlayer) player).getHandle();
        }

        Server server = player.getServer();
        EntityPlayer nmsPlayer = null;

        if (server instanceof CraftServer) {
            nmsPlayer = ((CraftServer) server).getHandle().getPlayer(player.getName());
        }

        if (nmsPlayer == null) {
            // Could use reflection to examine fields, but it's honestly not worth the bother.
            throw new RuntimeException("Unable to fetch EntityPlayer from provided Player implementation");
        }

        return nmsPlayer;
    }


    public Player loadPlayer( final OfflinePlayer offline) {
        // Ensure player has data
        if (!offline.hasPlayedBefore()) {
            return null;
        }


        // Create a profile and entity to load the player data
        // See net.minecraft.server.PlayerList#attemptLogin
        GameProfile profile = new GameProfile(offline.getUniqueId(),
                offline.getName() != null ? offline.getName() : offline.getUniqueId().toString());

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        WorldServer worldServer = server.getWorldServer(WorldServer.OVERWORLD);

        if (worldServer == null) {
            return null;
        }

        EntityPlayer entity = new EntityPlayer(server, worldServer, profile, new PlayerInteractManager(worldServer));



        // Get the bukkit entity
        Player target = entity.getBukkitEntity();
        if (target != null) {
            // Load data
            target.loadData();
        }
        // Return the entity
        return target;
    }
}
