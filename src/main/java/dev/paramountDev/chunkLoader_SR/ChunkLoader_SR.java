package dev.paramountDev.chunkLoader_SR;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class ChunkLoader_SR extends JavaPlugin implements Listener {

    private Material loaderBlock;
    private String loaderName;
    private List<String> loaderLore;
    private int radius;
    private Color particleColor;
    private final Set<String> loaderLocations = new HashSet<>();
    private final Set<Location> activeLoaders = new HashSet<>();
    private final Map<String, BukkitTask> loaderTasks = new HashMap<>();
    private static ChunkLoader_SR instance;

    public static ChunkLoader_SR getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {

        instance = this;

        saveDefaultConfig();
        reloadFromConfig();
        registerRecipe();

        getLogger().info("ChunkLoader starting...");
        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("chunkloader") != null) {
            getCommand("chunkloader").setExecutor(new ChunkLoaderCommand(instance));
            getCommand("chunkloader").setTabCompleter(new ChunkLoaderCommand(instance));
        }

        for (String s : loaderLocations) {
            String[] parts = s.split(";");
            if (parts.length != 4) continue;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) continue;

            try {
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[3]);
                forceLoadChunks(world, x, z, true);
            } catch (NumberFormatException ex) {
                getLogger().warning("Invalid loader location: " + s);
            }
        }

        for (String s : loaderLocations) {
            String[] parts = s.split(";");
            if (parts.length != 4) continue;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) continue;

            try {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);

                forceLoadChunks(world, x, z, true);

                startCubeAnimation(this, new Location(world, x + 0.5, y + 0.5, z + 0.5));
            } catch (NumberFormatException ex) {
                getLogger().warning("Invalid loader location: " + s);
            }
        }
    }

    @Override
    public void onDisable() {
        saveLoaders();
        for (String s : loaderLocations) {
            String[] parts = s.split(";");
            if (parts.length != 4) continue;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) continue;
            int x = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[3]);
            forceLoadChunks(world, x, z, false);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (!isChunkLoaderBlock(e.getItemInHand())) return;

        Location loc = b.getLocation();
        String key = serialize(loc);

        if (loaderLocations.add(key)) {
            forceLoadChunks(loc.getWorld(), loc.getBlockX(), loc.getBlockZ(), true);

            for (String s : loaderLocations) {
                String[] parts = s.split(";");
                if (parts.length != 4) continue;
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;

                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    startCubeAnimation(this, new Location(world, x + 0.5, y + 0.5, z + 0.5));
                } catch (NumberFormatException ex) {
                    getLogger().warning("Invalid loader location: " + s);
                }
            }

            saveLoaders();
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != loaderBlock) return;

        Location loc = b.getLocation();
        String key = serialize(loc);

        if (loaderLocations.remove(key)) {
            forceLoadChunks(loc.getWorld(), loc.getBlockX(), loc.getBlockZ(), false);
            e.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), createChunkLoaderItem());
            stopCubeAnimation(loc);
            saveLoaders();
        }
    }

    private void registerRecipe() {
        ItemStack loaderItem = createChunkLoaderItem();
        NamespacedKey key = new NamespacedKey(this, "chunk_loader");

        Bukkit.removeRecipe(key);

        List<String> shape = getConfig().getStringList("recipe.shape");
        if (shape.size() != 3) {
            getLogger().warning("Recipe shape must have 3 rows! Использую стандартный.");
            shape = List.of("DID", "ISI", "DID");
        }

        ShapedRecipe recipe = new ShapedRecipe(key, loaderItem);
        recipe.shape(shape.get(0), shape.get(1), shape.get(2));

        if (getConfig().isConfigurationSection("recipe.ingredients")) {
            for (String keyChar : getConfig().getConfigurationSection("recipe.ingredients").getKeys(false)) {
                String materialName = getConfig().getString("recipe.ingredients." + keyChar);
                Material mat = Material.matchMaterial(materialName);
                if (mat != null) {
                    recipe.setIngredient(keyChar.charAt(0), mat);
                } else {
                    getLogger().warning("Неизвестный материал: " + materialName);
                }
            }
        }

        Bukkit.addRecipe(recipe);
        getLogger().info("Chunk Loader recipe зарегистрирован!");
    }


    private boolean isChunkLoaderBlock(ItemStack item) {
        if (item == null || item.getType() != loaderBlock) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && meta.getDisplayName().equals(loaderName);
    }

    public ItemStack createChunkLoaderItem() {
        ItemStack item = new ItemStack(loaderBlock);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(loaderName);
            meta.setLore(loaderLore);
            item.setItemMeta(meta);
        }
        return item;
    }

}