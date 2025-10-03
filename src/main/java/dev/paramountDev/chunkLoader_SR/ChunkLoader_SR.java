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

    private void startCubeAnimation(JavaPlugin plugin, Location center) {
        String key = serialize(center);
        activeLoaders.add(center);

        BukkitTask task = new BukkitRunnable() {
            final Particle.DustOptions fillDust = new Particle.DustOptions(particleColor, 1.0f);
            final double size = 1.0;
            final int steps = 40;
            int tick = 0;

            @Override
            public void run() {
                if (!activeLoaders.contains(center)) {
                    cancel();
                    return;
                }

                World world = center.getWorld();
                if (!world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) return;

                if (tick > steps) {
                    tick = 0;
                    return;
                }

                double progress = (double) tick / steps;

                double minX = center.getX() - size;
                double maxX = center.getX() + size;
                double minY = center.getY();
                double maxY = center.getY() + 2 * size;
                double minZ = center.getZ() - size;
                double maxZ = center.getZ() + size;

                fillCube(world, minX, maxX, minY, maxY, minZ, maxZ, progress, fillDust);

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1);

        loaderTasks.put(key, task);
    }

    private void fillCube(World world,
                          double minX, double maxX,
                          double minY, double maxY,
                          double minZ, double maxZ,
                          double progress,
                          Particle.DustOptions dust) {
        double currentY = minY + (maxY - minY) * progress;

        for (double y = minY; y <= currentY; y += 0.4) {
            for (double x = minX; x <= maxX; x += 0.4) {
                for (double z = minZ; z <= maxZ; z += 0.4) {
                    boolean isSurface = (x <= minX + 0.01 || x >= maxX - 0.01 ||
                            z <= minZ + 0.01 || z >= maxZ - 0.01 ||
                            y <= minY + 0.01 || y >= currentY - 0.01);

                    if (isSurface) {
                        world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
                    }
                }
            }
        }
    }


    private void stopCubeAnimation(Location center) {
        String key = serialize(center);

        activeLoaders.remove(center);

        BukkitTask task = loaderTasks.remove(key);
        if (task != null) {
            task.cancel();
        }
    }




    private void forceLoadChunks(World world, int blockX, int blockZ, boolean load) {
        int cx = blockX >> 4;
        int cz = blockZ >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Chunk chunk = world.getChunkAt(cx + dx, cz + dz);
                chunk.setForceLoaded(load);
                if (load) world.loadChunk(chunk);
            }
        }
    }

    private String serialize(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    private void reloadFromConfig() {
        loaderBlock = Material.matchMaterial(getConfig().getString("loader-block", "LODESTONE"));
        if (loaderBlock == null) loaderBlock = Material.LODESTONE;

        loaderName = ChatColor.translateAlternateColorCodes('&', getConfig().getString("loader-name", "&bChunk Loader"));
        loaderLore = getConfig().getStringList("loader-lore");

        radius = Math.max(0, getConfig().getInt("radius", 3));

        loaderLocations.clear();
        loaderLocations.addAll(getConfig().getStringList("loaders"));


        int r = getConfig().getInt("particle-color.r", 255);
        int g = getConfig().getInt("particle-color.g", 255);
        int b = getConfig().getInt("particle-color.b", 0);
        particleColor = Color.fromRGB(r, g, b);
    }

    private void saveLoaders() {
        getConfig().set("loader-block", loaderBlock.name());
        getConfig().set("loader-name", loaderName);
        getConfig().set("loader-lore", loaderLore);
        getConfig().set("radius", radius);
        getConfig().set("loaders", loaderLocations.stream().toList());
        saveConfig();
    }
}