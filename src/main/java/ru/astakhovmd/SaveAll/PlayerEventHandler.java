package ru.astakhovmd.SaveAll;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;

public class PlayerEventHandler implements Listener {

    private final SaveAll instance;


    PlayerEventHandler(SaveAll plugin)
    {

        this.instance = plugin;
    }
    //when a player interacts with a specific part of entity...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event)
    {
        //treat it the same as interacting with an entity in general
        if (event.getRightClicked().getType() == EntityType.ARMOR_STAND)
        {
            this.onPlayerInteractEntity(event);
        }
    }
    //when a player interacts with an entity...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event)
    {

        Player player = event.getPlayer();
        //player.hasPermission("griefprevention.lava")
        SaveAll.sendMessage(player, TextMode.Err, "TEST 1!");
        return;
        /*
        Entity entity = event.getRightClicked();


        //don't allow interaction with item frames or armor stands in claimed areas without build permission
        if (entity.getType() == EntityType.ARMOR_STAND || entity instanceof Hanging)
        {
            if (instance.config_expropriate_from.contains(entity.getType()))
            {

                SaveAll.sendMessage(player, TextMode.Err, "IT WORKS!");
                event.setCancelled(true);
                return;

            }
        }





        //don't allow container access during pvp combat

        if ((entity instanceof StorageMinecart || entity instanceof PoweredMinecart))
        {
            if (instance.config_expropriate_from.contains(entity.getType()))
            {

                SaveAll.sendMessage(player, TextMode.Err, "IT WORKS!");
                event.setCancelled(true);
                return;

            }
        }

        //if the entity is a vehicle and we're preventing theft in claims
        if (entity instanceof Vehicle)
        {
            if (entity instanceof InventoryHolder && instance.config_expropriate_from.contains(entity.getType()))
            {

                    SaveAll.sendMessage(player, TextMode.Err, "IT WORKS!");
                    event.setCancelled(true);
                    return;

            }

        }

        */
    }

    //when a player interacts with the world
    @EventHandler(priority = EventPriority.LOWEST)
    void onPlayerInteract(PlayerInteractEvent event)
    {
        //SaveAll.sendMessage(null,TextMode.Err,"IT FUCKING WORKS!");
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR){
            //SaveAll.sendMessage(null,TextMode.Err,"-1");
            return;}
        if (action == Action.LEFT_CLICK_BLOCK){
            //SaveAll.sendMessage(null,TextMode.Err,"-1");
            return;}
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        Material clickedBlockType = null;
        //SaveAll.sendMessage(player,TextMode.Err,"0");
        if (clickedBlock != null)
        {
            clickedBlockType = clickedBlock.getType();
        }
        else
        {
            clickedBlockType = Material.AIR;
        }


        EquipmentSlot hand = event.getHand();
        ItemStack itemInHand = SaveAll.instance.getItemInHand(player, hand);
        Material materialInHand = itemInHand.getType();
        if (materialInHand != SaveAll.instance.config_Tool || hand != EquipmentSlot.HAND) return;
        if(!player.hasPermission("saveall.expropriate")) return;

        event.setCancelled(true);  //GriefPrevention exclusively reserves this tool

        //FEATURE: shovel and stick can be used from a distance away
        if (action == Action.RIGHT_CLICK_AIR)
        {
            //try to find a far away non-air block along line of sight
            clickedBlock = SaveAll.getTargetBlock(player, 100);
            clickedBlockType = clickedBlock.getType();
        }

        //if no block, stop here
        if (clickedBlock == null)
        {
            return;
        }
        if (clickedBlockType == Material.AIR)
        {
            return;
        }

        //SaveAll.sendMessage(player,TextMode.Err,"1");

        if (!isInventoryHolder( clickedBlock)){
            SaveAll.sendMessage(player,TextMode.Err,"Not inventory");
            return;
        }
        //Material finalClickedBlockType = clickedBlockType;
        //SaveAll.sendMessage(player,TextMode.Err,"S:"+SaveAll.instance.config_expropriate_from.size()+SaveAll.instance.config_expropriate_from.contains(clickedBlockType)+SaveAll.instance.config_expropriate_from.stream().anyMatch(material -> material.name().equals(finalClickedBlockType.name())));

        if (SaveAll.instance.config_expropriate_from.contains(clickedBlockType))
        {
            //SaveAll.instance.sendMessage(player, TextMode.Err, "Has inventory");
            //SaveAll.sendMessage(player,TextMode.Err,"IT FUCKING WORKS!");
            InventoryHolder ih = (InventoryHolder)clickedBlock.getState();
            Inventory i = ih.getInventory();
            //player.openInventory(i);
            MyPlayer actioner = SaveAll.instance.myPlayers.get(player.getUniqueId());
            if (actioner==null || actioner.target==null){
                SaveAll.sendMessage(player, TextMode.Err, "Expropriation target is not set!");
                return;
            }

            try {
                ArrayList<ItemStack> count = SaveAll.instance.expropriate(i,actioner.target);
                if (count.size()>0){
                    for (ItemStack item: count) {
                        SaveAll.sendMessage(player, TextMode.Instr, item.getType().name()+ " | " + item.getAmount());
                    }
                }else{
                    SaveAll.sendMessage(player, TextMode.Instr, "Nothing valuable");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            SaveAll.sendMessage(player, TextMode.Err, "Not in whitelist!: "+clickedBlockType);
        }

    }


    private boolean isInventoryHolder(Block clickedBlock)
    {

        Material cacheKey = clickedBlock.getType();
        Boolean cachedValue = SaveAll.instance.inventoryHolderCache.get(cacheKey);
        if (cachedValue != null)
        {
            return cachedValue.booleanValue();

        }
        else
        {
            boolean isHolder = clickedBlock.getState() instanceof InventoryHolder;
            SaveAll.instance.inventoryHolderCache.put(cacheKey, isHolder);
            return isHolder;
        }
    }
}
