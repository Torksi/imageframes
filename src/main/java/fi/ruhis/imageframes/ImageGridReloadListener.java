package fi.ruhis.imageframes;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.awt.image.BufferedImage;
import java.util.*;

public class ImageGridReloadListener implements Listener {

    private final Imageframes plugin;
    private final Set<String> pendingGrids = new HashSet<>();

    private final Set<String> finishedGrids = new HashSet<>();

    public ImageGridReloadListener(Imageframes plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::processPendingGrids, 100L, 100L);
    }

    public void addPendingGrid(String gridId) {
        pendingGrids.add(gridId);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        FileConfiguration config = plugin.getConfig();
        if (!config.contains("images")) return;
        pendingGrids.addAll(config.getConfigurationSection("images").getKeys(false));
    }

    private void processPendingGrids() {
        if (pendingGrids.isEmpty()) {
            return;
        }

        Set<String> gridsToProcess = new HashSet<>(pendingGrids);
        Iterator<String> iterator = gridsToProcess.iterator();
        while (iterator.hasNext()) {
            String gridId = iterator.next();
            if (finishedGrids.contains(gridId)) {
                iterator.remove();
                continue;
            }

            if (tryReapplyGrid(gridId)) {
                pendingGrids.remove(gridId);
            }
        }
    }

    private boolean tryReapplyGrid(String gridId) {
        FileConfiguration config = plugin.getConfig();
        if (!config.contains("images." + gridId)) {
            return true;
        }

        String imageUrl = config.getString("images." + gridId + ".url");
        int rows = config.getInt("images." + gridId + ".rows");
        int cols = config.getInt("images." + gridId + ".cols");
        List<String> frameLocations = config.getStringList("images." + gridId + ".frames");
        if (frameLocations.size() != rows * cols) {
            plugin.getLogger().warning("Grid " + gridId + " has an invalid number of frames.");
            return false;
        }

        boolean gridComplete = true;
        List<List<ItemFrame>> grid = new ArrayList<>();
        int index = 0;
        for (int r = 0; r < rows; r++) {
            List<ItemFrame> rowList = new ArrayList<>();
            for (int c = 0; c < cols; c++) {
                String locStr = frameLocations.get(index++); // world,x,y,z
                String[] parts = locStr.split(",");
                if (parts.length != 4) {
                    gridComplete = false;
                    break;
                }

                World world = Bukkit.getWorld(parts[0]);
                if (world == null) {
                    gridComplete = false;
                    break;
                }

                int x, y, z;
                try {
                    x = Integer.parseInt(parts[1]);
                    y = Integer.parseInt(parts[2]);
                    z = Integer.parseInt(parts[3]);
                } catch (NumberFormatException e) {
                    gridComplete = false;
                    break;
                }

                Chunk chunk = world.getChunkAt(x, z);
                if (!chunk.isLoaded()) {
                    gridComplete = false;
                    break;
                }

                Location loc = new Location(world, x + 0.5, y + 0.5, z + 0.5);
                ItemFrame frame = null;
                double radius = 1.5;

                for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
                    if (e instanceof ItemFrame) {
                        Location frameLoc = e.getLocation();
                        if (frameLoc.getBlockX() == x && frameLoc.getBlockY() == y && frameLoc.getBlockZ() == z) {
                            frame = (ItemFrame) e;
                            break;
                        }
                    }
                }
                if (frame == null) {
                    gridComplete = false;
                    break;
                }
                rowList.add(frame);
            }

            if (!gridComplete) break;
            grid.add(rowList);
        }

        if (!gridComplete || grid.size() != rows) {
            return false;
        }

        BufferedImage fullImage = ImageCommandExecutor.downloadImageSync(imageUrl);
        if (fullImage == null) {
            return false;
        }

        Imageframes.processAndApplyImage(imageUrl, fullImage, grid);
        plugin.getLogger().info("Reapplied image grid " + gridId + " from URL: " + imageUrl);
        finishedGrids.add(gridId);

        plugin.saveConfig();
        return true;
    }
}
