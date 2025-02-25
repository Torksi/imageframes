package fi.ruhis.imageframes;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Queue;
import java.util.*;

public class Imageframes extends JavaPlugin {
    private static ImageGridReloadListener reloadListener;

    public static ItemFrame getTargetItemFrame(Player player, double maxDistance) {
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();
        RayTraceResult result = player.getWorld().rayTraceEntities(eyeLocation, direction, maxDistance, 0.5,
                entity -> entity instanceof ItemFrame);

        if (result != null && result.getHitEntity() instanceof ItemFrame) {
            return (ItemFrame) result.getHitEntity();
        }
        return null;
    }

    public static List<List<ItemFrame>> getTargetItemFrameGrid(Player player) {
        ItemFrame targetFrame = getTargetItemFrame(player, 10);
        if (targetFrame == null) {
            player.sendMessage(ChatColor.RED + "You must be looking at an item frame.");
            return Collections.emptyList();
        }

        BlockFace targetFace = targetFrame.getAttachedFace();
        List<ItemFrame> nearbyFrames = new ArrayList<>();

        for (Entity e : player.getNearbyEntities(10, 10, 10)) {
            if (!(e instanceof ItemFrame frame)) continue;
            if (frame.getAttachedFace() != targetFace) continue;
            if (targetFace == BlockFace.NORTH || targetFace == BlockFace.SOUTH) {
                if (frame.getLocation().getBlockZ() == targetFrame.getLocation().getBlockZ())
                    nearbyFrames.add(frame);
            } else if (targetFace == BlockFace.EAST || targetFace == BlockFace.WEST) {
                if (frame.getLocation().getBlockX() == targetFrame.getLocation().getBlockX())
                    nearbyFrames.add(frame);
            } else {
                nearbyFrames.add(frame);
            }
        }

        Set<ItemFrame> groupSet = new HashSet<>();
        Queue<ItemFrame> queue = new LinkedList<>();
        queue.add(targetFrame);
        groupSet.add(targetFrame);

        while (!queue.isEmpty()) {
            ItemFrame current = queue.poll();
            int currX = current.getLocation().getBlockX();
            int currY = current.getLocation().getBlockY();
            int currZ = current.getLocation().getBlockZ();

            for (ItemFrame frame : nearbyFrames) {
                if (groupSet.contains(frame)) continue;
                int fx = frame.getLocation().getBlockX();
                int fy = frame.getLocation().getBlockY();
                int fz = frame.getLocation().getBlockZ();
                boolean adjacent = false;

                if (targetFace == BlockFace.NORTH || targetFace == BlockFace.SOUTH) {
                    if (fz == currZ) {
                        if ((Math.abs(fx - currX) == 1 && fy == currY) || (fx == currX && Math.abs(fy - currY) == 1)) {
                            adjacent = true;
                        }
                    }
                } else if (targetFace == BlockFace.EAST || targetFace == BlockFace.WEST) {
                    if (fx == currX) {
                        if ((Math.abs(fz - currZ) == 1 && fy == currY) || (fz == currZ && Math.abs(fy - currY) == 1)) {
                            adjacent = true;
                        }
                    }
                }
                if (adjacent) {
                    groupSet.add(frame);
                    queue.add(frame);
                }
            }
        }

        List<ItemFrame> groupList = new ArrayList<>(groupSet);
        groupList.sort(Comparator.comparingInt(f -> f.getLocation().getBlockY()));
        Map<Integer, List<ItemFrame>> rowsMap = new TreeMap<>();

        for (ItemFrame frame : groupList) {
            int y = frame.getLocation().getBlockY();
            rowsMap.computeIfAbsent(y, k -> new ArrayList<>()).add(frame);
        }

        List<List<ItemFrame>> grid = new ArrayList<>();

        for (List<ItemFrame> row : rowsMap.values()) {
            if (targetFace == BlockFace.SOUTH) {
                row.sort(Comparator.comparingInt(f -> f.getLocation().getBlockX()));
                Collections.reverse(row);
            } else if (targetFace == BlockFace.NORTH) {
                row.sort(Comparator.comparingInt(f -> f.getLocation().getBlockX()));
            } else if (targetFace == BlockFace.EAST) {
                row.sort(Comparator.comparingInt(f -> f.getLocation().getBlockZ()));
            } else if (targetFace == BlockFace.WEST) {
                row.sort(Comparator.comparingInt(f -> f.getLocation().getBlockZ()));
                Collections.reverse(row);
            }
            grid.add(row);
        }
        return grid;
    }

    public static MapView createMapView(World world, BufferedImage imageSlice) {
        MapView mapView = Bukkit.createMap(world);
        for (org.bukkit.map.MapRenderer renderer : mapView.getRenderers()) {
            mapView.removeRenderer(renderer);
        }
        mapView.addRenderer(new ImageMapRenderer(imageSlice));
        return mapView;
    }

    public static void processAndApplyImage(String imageUrl, BufferedImage fullImage, List<List<ItemFrame>> grid) {
        if (grid == null || grid.isEmpty()) {
            Bukkit.getLogger().warning("Grid is empty; nothing to process for image: " + imageUrl);
            return;
        }

        int rows = grid.size();
        int cols = grid.get(0).size();

        for (List<ItemFrame> row : grid) {
            if (row.size() != cols) {
                Bukkit.getLogger().warning("Inconsistent grid dimensions for image: " + imageUrl);
                return;
            }
        }

        BufferedImage resized = new BufferedImage(cols * 128, rows * 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resized.createGraphics();
        g2.drawImage(fullImage, 0, 0, cols * 128, rows * 128, null);
        g2.dispose();

        World world = grid.get(0).get(0).getWorld();

        for (int row = 0; row < rows; row++) {
            int imageRow = rows - 1 - row;
            List<ItemFrame> frameRow = grid.get(row);
            for (int col = 0; col < cols; col++) {
                BufferedImage subImage = resized.getSubimage(col * 128, imageRow * 128, 128, 128);
                MapView mapView = createMapView(world, subImage);

                ItemStack mapItem = new ItemStack(org.bukkit.Material.FILLED_MAP, 1);
                MapMeta meta = (MapMeta) mapItem.getItemMeta();
                meta.setMapView(mapView);
                mapItem.setItemMeta(meta);

                ItemFrame frame = frameRow.get(col);
                frame.setItem(mapItem);
                frame.setVisible(false);
            }
        }
    }

    @Override
    public void onEnable() {
        PluginCommand command = this.getCommand("image");
        if (command != null) {
            command.setExecutor(new ImageCommandExecutor(this));
        }
        reloadListener = new ImageGridReloadListener(this);
        Bukkit.getPluginManager().registerEvents(reloadListener, this);
        Bukkit.getScheduler().runTaskLater(this, this::loadPersistedImages, 20L);
    }

    private void loadPersistedImages() {
        FileConfiguration config = getConfig();
        if (!config.contains("images")) {
            return;
        }

        for (String gridId : config.getConfigurationSection("images").getKeys(false)) {
            String imageUrl = config.getString("images." + gridId + ".url");
            int rows = config.getInt("images." + gridId + ".rows");
            int cols = config.getInt("images." + gridId + ".cols");
            List<String> frameLocations = config.getStringList("images." + gridId + ".frames");
            if (frameLocations.size() != rows * cols) {
                getLogger().warning("Grid " + gridId + " has an invalid number of frames.");
                continue;
            }

            boolean gridComplete = true;
            int index = 0;
            for (int r = 0; r < rows; r++) {
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

                    if (!world.getChunkAt(x, z).isLoaded()) {
                        gridComplete = false;
                        break;
                    }
                }
                if (!gridComplete) break;
            }
            if (!gridComplete) {
                getLogger().info("Grid " + gridId + " is incomplete at startup. Adding to pending queue.");
                reloadListener.addPendingGrid(gridId);
                continue;
            }

            BufferedImage fullImage = ImageCommandExecutor.downloadImageSync(imageUrl);
            if (fullImage == null) {
                getLogger().warning("Failed to re-download image for grid " + gridId + ": " + imageUrl);
                continue;
            }

            List<List<ItemFrame>> grid = new ArrayList<>();
            index = 0;
            boolean success = true;
            for (int r = 0; r < rows; r++) {
                List<ItemFrame> rowList = new ArrayList<>();
                for (int c = 0; c < cols; c++) {
                    String locStr = frameLocations.get(index++);
                    String[] parts = locStr.split(",");
                    World world = Bukkit.getWorld(parts[0]);
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);

                    ItemFrame frame = findItemFrame(world, x, y, z);
                    if (frame == null) {
                        success = false;
                        break;
                    }
                    rowList.add(frame);
                }
                if (!success) break;
                grid.add(rowList);
            }

            if (!success || grid.size() != rows) {
                reloadListener.addPendingGrid(gridId);
                continue;
            }

            processAndApplyImage(imageUrl, fullImage, grid);
            getLogger().info("Reapplied image grid " + gridId + " from URL: " + imageUrl);
            saveConfig();
        }
    }

    public void saveImageGrid(String gridId, String imageUrl, List<List<ItemFrame>> grid) {
        FileConfiguration config = getConfig();
        int rows = grid.size();
        int cols = grid.get(0).size();
        config.set("images." + gridId + ".url", imageUrl);
        config.set("images." + gridId + ".rows", rows);
        config.set("images." + gridId + ".cols", cols);

        List<String> frameLocations = new ArrayList<>();
        for (List<ItemFrame> row : grid) {
            for (ItemFrame frame : row) {
                String worldName = frame.getWorld().getName();
                int x = frame.getLocation().getBlockX();
                int y = frame.getLocation().getBlockY();
                int z = frame.getLocation().getBlockZ();
                frameLocations.add(worldName + "," + x + "," + y + "," + z);
            }
        }

        config.set("images." + gridId + ".frames", frameLocations);
        saveConfig();
    }

    private ItemFrame findItemFrame(World world, int x, int y, int z) {
        org.bukkit.Location loc = new org.bukkit.Location(world, x + 0.5, y + 0.5, z + 0.5);
        double radius = 1.5;
        for (org.bukkit.entity.Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (e instanceof ItemFrame) {
                org.bukkit.Location frameLoc = e.getLocation();
                if (frameLoc.getBlockX() == x && frameLoc.getBlockY() == y && frameLoc.getBlockZ() == z) {
                    return (ItemFrame) e;
                }
            }
        }
        return null;
    }
}
