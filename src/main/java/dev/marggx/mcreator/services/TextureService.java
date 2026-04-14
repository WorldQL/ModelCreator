package dev.marggx.mcreator.services;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.server.core.asset.AssetModule;
import dev.marggx.mcreator.data.blockymodel.Blockymodel;
import dev.marggx.mcreator.data.blockymodel.BlockymodelBase;
import dev.marggx.mcreator.data.blockymodel.BlockymodelShapeTextureLayout;
import dev.marggx.mcreator.data.extras.BaseModel;
import dev.marggx.mcreator.data.extras.Model;
import dev.marggx.mcreator.utils.Logger;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TextureService {

    private static final TextureService INSTANCE = new TextureService();
    private static final Logger LOGGER = Logger.get();
    public static TextureService get() {
        return INSTANCE;
    }

    public void combineFiles(List<File> files, File output) throws Exception {
        List<BufferedImage> images = new ArrayList<>();

        int maxWidth = 0;
        int totalHeight = 0;

        for (File file : files) {
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                throw new RuntimeException("Failed to read: " + file);
            }

            images.add(img);

            if (img.getWidth() > maxWidth) {
                maxWidth = img.getWidth();
            }

            totalHeight += img.getHeight();
        }

        BufferedImage combined = new BufferedImage(
                maxWidth,
                totalHeight,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g = combined.createGraphics();

        g.setBackground(new java.awt.Color(0,0,0,0));
        g.clearRect(0, 0, maxWidth, totalHeight);

        int y = 0;
        for (BufferedImage img : images) {
            g.drawImage(img, 0, y, null);
            y += img.getHeight();
        }

        g.dispose();

        ImageIO.write(combined, "PNG", output);
    }

    public BufferedImage combineImages(BufferedImage img1, BufferedImage img2) {
        int maxWidth = Math.max(img1.getWidth(), img2.getWidth());
        int totalHeight = img1.getHeight() + img2.getHeight();

        BufferedImage combined = new BufferedImage(
                maxWidth,
                totalHeight,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g = combined.createGraphics();
        this.setRenderHints(g);

        g.setBackground(new java.awt.Color(0,0,0,0));
        g.clearRect(0, 0, maxWidth, totalHeight);

        g.drawImage(img1, 0, 0, null);
        g.drawImage(img2, 0, img1.getHeight(), null);

        g.dispose();

        return combined;
    }

    public boolean saveTexture(BaseModel model) {
        try {
            Path outputPath = getTexturePathForPack(model.pack());
            if (outputPath == null) return false;
            outputPath = outputPath.resolve("VFX/Merged");
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }
            outputPath = outputPath.resolve(model.name() + ".png");
            ImageIO.write(model.texture(), "PNG", outputPath.toFile());
        } catch (IOException e) {
            LOGGER.severe("Failed to save texture to path '%s'", e.getMessage());
            return false;
        }
        return true;
    }

    public BufferedImage getTexture(String path) throws Exception {
        Path texturePath = getTexturePathFromAnyPack(path);
        if (texturePath == null) return null;
        return getTexture(texturePath);
    }

    private BufferedImage getTexture(Path path) throws Exception {
        if (path == null) return null;
        return ImageIO.read(Files.newInputStream(path));
    }

    private Path getTexturePathFromAnyPack(@Nonnull String name) {
        for (AssetPack pack : AssetModule.get().getAssetPacks()) {
            Path texturePathForPack = this.getTexturePathForPack(pack);
            Path texturePath = texturePathForPack.resolve(name);
            if (Files.exists(texturePath)) return texturePath;
        }

        return null;
    }

    private Path getTexturePathForPack(@Nonnull AssetPack pack) {
        return pack.getRoot().resolve("Common");
    }

    public Path getTexturePathForPack(@Nonnull String name) {
        for (AssetPack pack : AssetModule.get().getAssetPacks()) {
            if (!pack.getName().equals(name)) continue;
            Path texturePathForPack = this.getTexturePathForPack(pack);
            if (Files.isDirectory(texturePathForPack)) return texturePathForPack;
            try {
                Files.createDirectories(texturePathForPack);
            } catch (Exception e) {
                LOGGER.severe("Failed to create directory for texture in pack '%s'", name);
                return null;
            }
        }

        return null;
    }

    public BufferedImage scaleImage(BufferedImage img, double scale) {
        if (scale == 1.0) return img;
        int w = (int) MathUtil.fastRound(img.getWidth() * scale);
        int h = (int) MathUtil.fastRound(img.getHeight() * scale);
        BufferedImage scaled = new BufferedImage(w, h,
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = scaled.createGraphics();
        this.setRenderHints(g);
        g.setBackground(new java.awt.Color(0,0,0,0));
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return scaled;
    }

    private void setRenderHints(Graphics2D g) {
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        );
        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF
        );
        g.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_SPEED
        );
        g.setRenderingHint(
                RenderingHints.KEY_DITHERING,
                RenderingHints.VALUE_DITHER_DISABLE
        );
        g.setRenderingHint(
                RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED
        );
    }

    public void handleTexture(Model model, BaseModel base) {
        String cacheKey = model.texturePath();
        int textureCache = base.getFromTextureCache(cacheKey);
        if (textureCache != -1) {
            TextureService.get().rearrangeTextureLayout(model.blockymodel(), textureCache);
            return;
        }

        BufferedImage texture;
        try {
            texture = TextureService.get().getTexture(model.texturePath());
        } catch (Exception e) {
            LOGGER.severe("Failed to load texture for model with id '%s' and texture path '%s'", model.id(), model.texturePath());
            return;
        }

        int textureHeight = base.texture() != null ? base.texture().getHeight() : 0;
        base.extendTexture(texture);

        base.addToTextureCache(cacheKey, textureHeight);
        TextureService.get().rearrangeTextureLayout(model.blockymodel(), textureHeight);
    }

    public void handleTexture(Blockymodel model, String texturePath, BaseModel base) {
        int textureCache = base.getFromTextureCache(texturePath);
        if (textureCache != -1) {
            TextureService.get().rearrangeTextureLayout(model, textureCache);
            return;
        }

        BufferedImage texture;
        try {
            texture = TextureService.get().getTexture(texturePath);
        } catch (Exception e) {
            LOGGER.severe("Failed to load texture path '%s'", texturePath);
            return;
        }

        int textureHeight = base.texture() != null ? base.texture().getHeight() : 0;
        base.extendTexture(texture);

        base.addToTextureCache(texturePath, textureHeight);
        TextureService.get().rearrangeTextureLayout(model, textureHeight);
    }

    public void rearrangeTextureLayout(BlockymodelBase blockymodel, int textureHeight) {
        Blockymodel[] nodes = blockymodel.getNodes();

        for (Blockymodel node : nodes) {
            rearrangeTextureLayout(node, textureHeight);
        }
    }

    private void rearrangeTextureLayout(Blockymodel model, int textureHeight) {
        if (model.shape != null) {
            if (model.shape.textureLayout != null) {
                for (BlockymodelShapeTextureLayout layout : model.shape.textureLayout.values()) {
                    if (layout.offset == null) continue;
                    layout.offset.add(0, textureHeight);
                }
            }
        }

        if (model.children == null) return;
        for (Blockymodel child : model.children) {
            rearrangeTextureLayout(child, textureHeight);
        }
    }
}
