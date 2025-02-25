package fi.ruhis.imageframes;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ImageCommandExecutor implements CommandExecutor {
    private final Imageframes plugin;

    public ImageCommandExecutor(Imageframes plugin) {
        this.plugin = plugin;
    }

    public static BufferedImage downloadImage(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            try (InputStream is = url.openStream()) {
                return ImageIO.read(is);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static BufferedImage downloadImageSync(String imageUrl) {
        return downloadImage(imageUrl);
    }

    private void removeImage(Player player) {
        List<List<ItemFrame>> grid = Imageframes.getTargetItemFrameGrid(player);
        if (grid.isEmpty()) {
            player.sendMessage(ChatColor.RED + "You aren't looking at a grid of item frames!");
            return;
        }

        for (List<ItemFrame> row : grid) {
            for (ItemFrame frame : row) {
                frame.setItem(null);
                frame.setVisible(true);
            }
        }
        player.sendMessage(ChatColor.GREEN + "Image removed from the targeted item frames.");

        List<String> gridCoordinates = new ArrayList<>();
        for (List<ItemFrame> row : grid) {
            for (ItemFrame frame : row) {
                World world = frame.getWorld();
                int x = frame.getLocation().getBlockX();
                int y = frame.getLocation().getBlockY();
                int z = frame.getLocation().getBlockZ();
                gridCoordinates.add(world.getName() + "," + x + "," + y + "," + z);
            }
        }

        FileConfiguration config = plugin.getConfig();
        if (!config.contains("images")) {
            return;
        }

        for (String gridId : config.getConfigurationSection("images").getKeys(false)) {
            List<String> savedCoords = config.getStringList("images." + gridId + ".frames");
            if (savedCoords.size() != gridCoordinates.size()) continue;
            if (savedCoords.equals(gridCoordinates)) {
                config.set("images." + gridId, null);
                plugin.saveConfig();
                player.sendMessage(ChatColor.GREEN + "Image removed.");
                return;
            }
        }
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("remove")) {
            removeImage(player);
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /image <image URL>");
            return true;
        }

        String imageUrl = args[0];
        List<List<ItemFrame>> grid = Imageframes.getTargetItemFrameGrid(player);
        if (grid.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No item frames found in a rectangular grid nearby!");
            return true;
        }

        int rows = grid.size();
        int cols = grid.get(0).size();
        player.sendMessage(ChatColor.GREEN + "Found grid of " + cols + " x " + rows + " frames. Downloading image...");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            BufferedImage fullImage = downloadImage(imageUrl);
            if (fullImage == null) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(ChatColor.RED + "Error downloading or processing image.")
                );
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Imageframes.processAndApplyImage(imageUrl, fullImage, grid);
                String gridId = UUID.randomUUID().toString();
                plugin.saveImageGrid(gridId, imageUrl, grid);
                player.sendMessage(ChatColor.GREEN + "Image applied and saved.");
            });
        });

        return true;
    }
}


