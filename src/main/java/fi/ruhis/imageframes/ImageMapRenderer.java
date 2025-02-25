package fi.ruhis.imageframes;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageMapRenderer extends MapRenderer {

    private final BufferedImage image;
    private boolean rendered = false;

    public ImageMapRenderer(BufferedImage image) {
        this.image = image;
    }

    @Override
    public void render(MapView mapView, MapCanvas canvas, Player player) {
        if (rendered) return;

        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                if (alpha < 128) continue;
                Color color = new Color(argb, true);
                canvas.setPixelColor(
                        x, y,
                        color
                );
            }
        }
        rendered = true;
    }
}

