package com.phantasm.briefing.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.phantasm.briefing.data.ContentImagePath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ContentTextureCache {
    private static final long MAX_FILE_SIZE = 8L * 1024L * 1024L;
    private static final int MAX_DIMENSION = 4096;
    private static final Map<String, LoadResult> CACHE = new HashMap<>();

    private ContentTextureCache() {
    }

    public static LoadResult load(String imageName) {
        String normalized = ContentImagePath.normalize(imageName);
        if (normalized.isBlank()) {
            return new LoadResult(null, Failure.INVALID_PATH);
        }
        LoadResult cached = CACHE.get(normalized);
        if (cached != null) {
            return cached;
        }
        LoadResult loaded = loadTexture(normalized);
        if (loaded.texture() != null) {
            CACHE.put(normalized, loaded);
        }
        return loaded;
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (LoadResult result : CACHE.values()) {
            if (result.texture() != null) {
                minecraft.getTextureManager().release(result.texture().location());
            }
        }
        CACHE.clear();
    }

    private static LoadResult loadTexture(String normalized) {
        Path imageRoot = FMLPaths.CONFIGDIR.get().resolve("phantasmbriefing")
                .resolve("manual_images").normalize();
        Path imagePath = imageRoot.resolve(normalized).normalize();
        if (!imagePath.getParent().equals(imageRoot)) {
            return new LoadResult(null, Failure.INVALID_PATH);
        }
        try {
            if (!Files.isRegularFile(imagePath)) {
                return new LoadResult(null, Failure.MISSING);
            }
            if (Files.size(imagePath) > MAX_FILE_SIZE) {
                return new LoadResult(null, Failure.FILE_TOO_LARGE);
            }
            ImageDimensions dimensions = readDimensions(imagePath);
            if (dimensions.width() > MAX_DIMENSION || dimensions.height() > MAX_DIMENSION) {
                return new LoadResult(null, Failure.DIMENSIONS_TOO_LARGE);
            }
            try (InputStream input = Files.newInputStream(imagePath)) {
                NativeImage image = NativeImage.read(input);
                if (image.getWidth() != dimensions.width() || image.getHeight() != dimensions.height()) {
                    image.close();
                    return new LoadResult(null, Failure.CORRUPT);
                }
                try {
                    DynamicTexture dynamicTexture = new DynamicTexture(image);
                    String textureName = "phantasmbriefing/manual/" + UUID.nameUUIDFromBytes(
                            normalized.getBytes(StandardCharsets.UTF_8));
                    ResourceLocation location = Minecraft.getInstance().getTextureManager().register(
                            textureName,
                            dynamicTexture
                    );
                    return new LoadResult(
                            new TextureEntry(location, image.getWidth(), image.getHeight()),
                            Failure.NONE
                    );
                } catch (RuntimeException exception) {
                    image.close();
                    throw exception;
                }
            }
        } catch (IOException | RuntimeException exception) {
            return new LoadResult(null, Failure.CORRUPT);
        }
    }

    private static ImageDimensions readDimensions(Path imagePath) throws IOException {
        byte[] header;
        try (InputStream input = Files.newInputStream(imagePath)) {
            header = input.readNBytes(24);
        }
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (header.length != 24) {
            throw new IOException("Invalid PNG header");
        }
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) {
                throw new IOException("Invalid PNG signature");
            }
        }
        if (header[12] != 'I' || header[13] != 'H' || header[14] != 'D' || header[15] != 'R') {
            throw new IOException("Missing PNG IHDR");
        }
        ByteBuffer dimensions = ByteBuffer.wrap(header, 16, 8);
        int width = dimensions.getInt();
        int height = dimensions.getInt();
        if (width < 1 || height < 1) {
            throw new IOException("Invalid PNG dimensions");
        }
        return new ImageDimensions(width, height);
    }

    public record TextureEntry(ResourceLocation location, int width, int height) {
    }

    public record LoadResult(@Nullable TextureEntry texture, Failure failure) {
    }

    private record ImageDimensions(int width, int height) {
    }

    public enum Failure {
        NONE,
        MISSING,
        INVALID_PATH,
        FILE_TOO_LARGE,
        DIMENSIONS_TOO_LARGE,
        CORRUPT
    }
}
