package org.foxesworld.kalitech.core;

import org.foxesworld.kalitech.core.io.ByteParser;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ICOParser — современный, максимально совместимый парсер .ico для десктоп-иконок.
 *
 * <p>Цели:
 * <ul>
 *   <li>Работает и с File/Path, и с classpath resources (InputStream)</li>
 *   <li>Поддерживает ICO с PNG-кадрами и BMP(DIB)-кадрами</li>
 *   <li>Умеет выбрать "лучшие" иконки для окна (16/24/32/48/64/128/256)</li>
 *   <li>LRU-кэш декодированных кадров (опционально)</li>
 *   <li>Минимум легаси: убраны лишние/мертвые части, API собран в одну точку</li>
 * </ul>
 */
public class ICOParser extends ByteParser<List<BufferedImage>> {

    private static final int MAX_ICO_SIZE_BYTES = 32 * 1024 * 1024; // safety cap
    private static final int DEFAULT_CACHE_SIZE = 32;
    private static final int MAX_ENTRIES = 2048;

    // PNG signature
    private static final byte[] PNG_SIG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    /**
     * LRU cache by IconKey.
     * Thread-safe via synchronizedMap wrapper.
     */
    private final Map<IconKey, BufferedImage> imageCache;

    /**
     * Parsed ICO bytes (kept to lazily decode frames).
     */
    private byte[] icoData;

    /**
     * Directory entries (metadata).
     */
    private List<IconDirEntry> entries;

    public ICOParser() {
        this(DEFAULT_CACHE_SIZE);
    }

    /**
     * @param cacheSize 0 => disable cache; >0 => LRU cache size
     */
    public ICOParser(int cacheSize) {
        if (cacheSize < 0) throw new IllegalArgumentException("cacheSize < 0");
        if (cacheSize == 0) {
            this.imageCache = Collections.emptyMap();
        } else {
            this.imageCache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<IconKey, BufferedImage> eldest) {
                    return size() > cacheSize;
                }
            });
        }
    }

    // ------------------------------------------------------------
    // Public "modern" API
    // ------------------------------------------------------------

    /**
     * Parse ICO from InputStream.
     */
    public List<BufferedImage> parse(InputStream in) throws IOException {
        if (in == null) throw new FileNotFoundException("ICO InputStream is null");
        return parse(readAllBytesLimited(in, MAX_ICO_SIZE_BYTES));
    }

    /**
     * Load best window icons for engines like jME (AppSettings.setIcons(BufferedImage[])).
     *
     * <p>Resolver order:
     * <ol>
     *   <li>ref as file path</li>
     *   <li>assetsDir + ref (if assetsDir != null)</li>
     *   <li>classpath resource (ref and /ref)</li>
     *   <li>fallback candidates (common icon locations)</li>
     * </ol>
     */
    public BufferedImage[] loadAppIcons(String ref, Path assetsDir, ClassLoader loader) throws IOException {
        ClassLoader cl = (loader != null) ? loader : Thread.currentThread().getContextClassLoader();

        String[] candidates = buildCandidates(ref);

        IOException last = null;
        for (String c : candidates) {
            if (c == null || c.isBlank()) continue;

            try (InputStream in = openAny(c, assetsDir, cl)) {
                if (in == null) continue;

                String lower = c.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".ico")) {
                    List<BufferedImage> icons = parse(in);
                    BufferedImage[] picked = pickBestIcons(icons);
                    if (picked.length > 0) return picked;

                    BufferedImage best = getBestIcon(icons);
                    if (best != null) return new BufferedImage[]{best};
                } else {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null) return makeMultiSizeIcons(img);
                }
            } catch (IOException e) {
                last = e;
            }
        }

        if (last != null) throw last;
        throw new FileNotFoundException("Icon not found: " + Arrays.toString(candidates));
    }

    /**
     * Compatibility helper for old usage:
     * icoParser.getBestIcon(icoParser.parse(stream))
     */
    public BufferedImage getBestIcon(List<BufferedImage> icons) {
        if (icons == null || icons.isEmpty()) return null;
        // Prefer typical "app icon" size first
        BufferedImage best = getBestMatchingIcon(icons, 256, 256);
        if (best != null) return best;
        best = getBestMatchingIcon(icons, 128, 128);
        if (best != null) return best;
        return getHighestQualityIcon(icons);
    }

    /**
     * Pick best icons for typical OS sizes (for AppSettings.setIcons()).
     */
    public BufferedImage[] pickBestIcons(List<BufferedImage> icons) {
        if (icons == null || icons.isEmpty()) return new BufferedImage[0];

        int[] sizes = new int[]{16, 24, 32, 48, 64, 128, 256};

        LinkedHashMap<String, BufferedImage> uniq = new LinkedHashMap<>();
        for (int s : sizes) {
            BufferedImage best = getBestMatchingIcon(icons, s, s);
            if (best == null) continue;
            uniq.putIfAbsent(best.getWidth() + "x" + best.getHeight(), best);
        }

        return uniq.values().toArray(BufferedImage[]::new);
    }

    /**
     * Clears decoded image cache.
     */
    public void clearCache() {
        if (!imageCache.isEmpty()) imageCache.clear();
    }

    /**
     * Meta info for already parsed ICO.
     */
    public Set<Dimension> getAvailableSizes() {
        if (entries == null) return Collections.emptySet();
        Set<Dimension> sizes = new HashSet<>();
        for (IconDirEntry e : entries) sizes.add(new Dimension(e.width, e.height));
        return sizes;
    }

    public List<IconInfo> getIconInfo() {
        if (entries == null) return Collections.emptyList();

        List<IconInfo> out = new ArrayList<>(entries.size());
        for (IconDirEntry e : entries) {
            boolean png = false;
            if (icoData != null) {
                try {
                    png = isPng(icoData, e.imageOffset, e.bytesInRes);
                } catch (Exception ignored) {
                }
            }
            out.add(new IconInfo(
                    e.width, e.height, e.bitCount, e.colorCount, png ? "PNG" : "BMP", e.bytesInRes
            ));
        }
        return out;
    }

    // ------------------------------------------------------------
    // ByteParser implementation
    // ------------------------------------------------------------

    @Override
    protected List<BufferedImage> parseBytes(byte[] data) throws IOException {
        if (data == null || data.length < 6) throw new IOException("Invalid ICO: too small");
        this.icoData = data;

        final int reserved = readLEU16(data, 0);
        final int type = readLEU16(data, 2);
        final int count = readLEU16(data, 4);

        if (reserved != 0 || (type != 1 && type != 2) || count <= 0 || count > MAX_ENTRIES) {
            throw new IOException("Invalid ICO header: reserved=" + reserved + " type=" + type + " count=" + count);
        }

        int dirOffset = 6;
        int dirSize = 16 * count;
        if (dirOffset + dirSize > data.length) {
            throw new IOException("Invalid ICO: directory exceeds file size");
        }

        List<IconDirEntry> tmp = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int off = dirOffset + i * 16;

            int w = data[off] & 0xFF;
            int h = data[off + 1] & 0xFF;
            int colorCount = data[off + 2] & 0xFF;
            int reserved2 = data[off + 3] & 0xFF;
            int planes = readLEU16(data, off + 4);
            int bitCount = readLEU16(data, off + 6);
            int bytesInRes = readLEI32(data, off + 8);
            int imageOffset = readLEI32(data, off + 12);

            int width = (w == 0) ? 256 : w;
            int height = (h == 0) ? 256 : h;

            if (bytesInRes <= 0 || imageOffset < 0 || imageOffset + bytesInRes > data.length) {
                throw new IOException("Invalid ICO entry " + i + ": off=" + imageOffset + " size=" + bytesInRes);
            }

            tmp.add(new IconDirEntry(width, height, colorCount, planes, bitCount, bytesInRes, imageOffset, reserved2));
        }

        this.entries = tmp;

        // Decode all frames eagerly (common expectation), but uses cache.
        List<BufferedImage> images = new ArrayList<>(count);
        for (IconDirEntry e : entries) {
            BufferedImage img = loadIconImage(e);
            if (img != null) images.add(img);
        }

        return images;
    }

    // ------------------------------------------------------------
    // Decoding internals
    // ------------------------------------------------------------

    private BufferedImage loadIconImage(IconDirEntry e) throws IOException {
        if (icoData == null) throw new IllegalStateException("No ICO data loaded");

        IconKey key = new IconKey(e.imageOffset, e.bytesInRes, e.width, e.height, e.bitCount);
        if (!imageCache.isEmpty()) {
            BufferedImage cached = imageCache.get(key);
            if (cached != null) return cached;
        }

        BufferedImage decoded;
        if (isPng(icoData, e.imageOffset, e.bytesInRes)) {
            decoded = decodePngSlice(icoData, e.imageOffset, e.bytesInRes);
        } else {
            decoded = decodeDibSlice(icoData, e.imageOffset, e.bytesInRes, e);
        }

        if (decoded != null && !imageCache.isEmpty()) imageCache.put(key, decoded);
        return decoded;
    }

    private static BufferedImage decodePngSlice(byte[] data, int off, int len) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(data, off, len)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new IOException("PNG decode failed (ImageIO returned null)");
            return ensureArgb(img);
        }
    }

    /**
     * ICO stores DIB (BMP without the 14-byte BITMAPFILEHEADER).
     * We support common bit depths: 1, 4, 8, 24, 32.
     */
    private static BufferedImage decodeDibSlice(byte[] data, int off, int len, IconDirEntry entry) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data, off, len))) {

            // BITMAPINFOHEADER (or variants)
            int headerSize = readLEInt(dis);
            if (headerSize < 12) throw new IOException("DIB header too small: " + headerSize);

            int width = readLEInt(dis);
            int heightWithMask = readLEInt(dis);
            int height = heightWithMask / 2;

            // Sometimes ICO has quirks; trust directory entry if it looks sane
            if (width <= 0 || width > 4096) width = entry.width;
            if (height <= 0 || height > 4096) height = entry.height;

            int planes = readLEShort(dis);
            int bitCount = readLEShort(dis);

            // Skip remaining header bytes to pixel data position
            int alreadyRead = 4 /*headerSize*/ + 4 /*w*/ + 4 /*h*/ + 2 /*planes*/ + 2 /*bpp*/;
            int toSkip = headerSize - alreadyRead;
            if (toSkip > 0) dis.skipBytes(toSkip);

            switch (bitCount) {
                case 1:
                    return decode1Bit(dis, width, height);
                case 4:
                    return decode4Bit(dis, width, height);
                case 8:
                    return decode8Bit(dis, width, height);
                case 24:
                    return decode24(dis, width, height);
                case 32:
                    return decode32(dis, width, height);
                default:
                    throw new IOException("Unsupported ICO DIB bit depth: " + bitCount);
            }
        }
    }

    private static BufferedImage decode1Bit(DataInputStream dis, int width, int height) throws IOException {
        // palette: 2 entries (BGRA each)
        IndexColorModel cm = readPalette(dis, 2, 1);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY, cm);

        int rowSize = strideBytes(width, 1);
        byte[] row = new byte[rowSize];

        for (int y = height - 1; y >= 0; y--) {
            dis.readFully(row, 0, rowSize);
            img.getRaster().setDataElements(0, y, width, 1, row);
        }

        skipAndMask(dis, width, height);
        return ensureArgbIfMasked(img);
    }

    private static BufferedImage decode4Bit(DataInputStream dis, int width, int height) throws IOException {
        IndexColorModel cm = readPalette(dis, 16, 4);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, cm);

        int rowSize = strideBytes(width, 4);
        byte[] row = new byte[rowSize];

        for (int y = height - 1; y >= 0; y--) {
            dis.readFully(row, 0, rowSize);
            img.getRaster().setDataElements(0, y, width, 1, row);
        }

        skipAndMask(dis, width, height);
        return ensureArgbIfMasked(img);
    }

    private static BufferedImage decode8Bit(DataInputStream dis, int width, int height) throws IOException {
        IndexColorModel cm = readPalette(dis, 256, 8);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, cm);

        int rowSize = strideBytes(width, 8);
        byte[] row = new byte[rowSize];
        byte[] pixels = new byte[width * height];

        for (int y = height - 1; y >= 0; y--) {
            dis.readFully(row, 0, rowSize);
            System.arraycopy(row, 0, pixels, y * width, width);
        }

        img.getRaster().setDataElements(0, 0, width, height, pixels);

        skipAndMask(dis, width, height);
        return ensureArgbIfMasked(img);
    }

    private static BufferedImage decode24(DataInputStream dis, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        int rowSize = strideBytes(width, 24);
        byte[] row = new byte[rowSize];

        for (int y = height - 1; y >= 0; y--) {
            dis.readFully(row, 0, rowSize);
            int p = 0;
            for (int x = 0; x < width; x++) {
                int b = row[p] & 0xFF;
                int g = row[p + 1] & 0xFF;
                int r = row[p + 2] & 0xFF;
                p += 3;
                img.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }

        // apply AND mask for transparency
        applyAndMask(dis, img, width, height);
        return img;
    }

    private static BufferedImage decode32(DataInputStream dis, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        int rowSize = strideBytes(width, 32);
        byte[] row = new byte[rowSize];

        boolean anyAlpha = false;

        for (int y = height - 1; y >= 0; y--) {
            dis.readFully(row, 0, rowSize);
            int p = 0;
            for (int x = 0; x < width; x++) {
                int b = row[p] & 0xFF;
                int g = row[p + 1] & 0xFF;
                int r = row[p + 2] & 0xFF;
                int a = row[p + 3] & 0xFF;
                p += 4;
                if (a != 0xFF) anyAlpha = true;
                img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        // Some ICOs have 32-bit but alpha all 0/255, AND mask still relevant.
        if (!anyAlpha) {
            try {
                applyAndMask(dis, img, width, height);
            } catch (IOException ignored) {
                // tolerate truncated masks
            }
        } else {
            // If mask exists, skip it (don’t apply to avoid double transparency)
            skipAndMask(dis, width, height);
        }

        return img;
    }

    private static IndexColorModel readPalette(DataInputStream dis, int colors, int bits) throws IOException {
        byte[] r = new byte[colors];
        byte[] g = new byte[colors];
        byte[] b = new byte[colors];
        byte[] a = new byte[colors];

        for (int i = 0; i < colors; i++) {
            int bb = dis.readUnsignedByte();
            int gg = dis.readUnsignedByte();
            int rr = dis.readUnsignedByte();
            int aa = dis.readUnsignedByte(); // reserved/alpha (usually 0)
            b[i] = (byte) bb;
            g[i] = (byte) gg;
            r[i] = (byte) rr;
            a[i] = (byte) (aa == 0 ? 0xFF : aa);
        }

        // Use palette alpha if it exists, otherwise opaque
        return new IndexColorModel(bits, colors, r, g, b, a);
    }

    private static void applyAndMask(DataInputStream dis, BufferedImage img, int width, int height) throws IOException {
        int maskRowSize = strideMaskBytes(width);
        byte[] maskRow = new byte[maskRowSize];

        for (int y = height - 1; y >= 0; y--) {
            dis.readFully(maskRow, 0, maskRowSize);
            for (int x = 0; x < width; x++) {
                int byteIndex = x >>> 3;
                int bitIndex = 7 - (x & 7);
                boolean transparent = ((maskRow[byteIndex] >>> bitIndex) & 1) == 1;

                if (transparent) {
                    int rgb = img.getRGB(x, y) & 0x00FFFFFF;
                    img.setRGB(x, y, rgb); // alpha 0
                }
            }
        }
    }

    private static void skipAndMask(DataInputStream dis, int width, int height) throws IOException {
        int maskRowSize = strideMaskBytes(width);
        long toSkip = (long) maskRowSize * (long) height;
        while (toSkip > 0) {
            long skipped = dis.skip(toSkip);
            if (skipped <= 0) break;
            toSkip -= skipped;
        }
    }

    /**
     * If image type isn't ARGB, convert (for consistent icon handling in modern pipelines).
     */
    private static BufferedImage ensureArgb(BufferedImage img) {
        if (img == null) return null;
        if (img.getType() == BufferedImage.TYPE_INT_ARGB) return img;
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(img, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * If we decoded indexed/binary and mask exists, it's safer for modern usage to ensure ARGB.
     * We don't apply the mask (already skipped), but conversion keeps downstream consistent.
     */
    private static BufferedImage ensureArgbIfMasked(BufferedImage img) {
        // For indexed/binary icons: still return as-is (OS can take indexed),
        // but ARGB is generally the safest for setIcons().
        return ensureArgb(img);
    }

    private static int strideBytes(int width, int bitsPerPixel) {
        // rows padded to 4-byte boundary
        return ((width * bitsPerPixel + 31) / 32) * 4;
    }

    private static int strideMaskBytes(int width) {
        // 1 bit per pixel mask, padded to 4-byte boundary
        return ((width + 31) / 32) * 4;
    }

    // ------------------------------------------------------------
    // Selection utilities
    // ------------------------------------------------------------

    public BufferedImage getIconExactSize(List<BufferedImage> icons, int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("width/height < 0");
        if (icons == null || icons.isEmpty()) return null;
        for (BufferedImage icon : icons) {
            if (icon.getWidth() == width && icon.getHeight() == height) return icon;
        }
        return null;
    }

    public BufferedImage getBestMatchingIcon(List<BufferedImage> icons, int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("width/height < 0");
        if (icons == null || icons.isEmpty()) return null;

        BufferedImage exact = getIconExactSize(icons, width, height);
        if (exact != null) return exact;

        BufferedImage best = null;
        long bestScore = Long.MAX_VALUE;

        for (BufferedImage icon : icons) {
            int iw = icon.getWidth();
            int ih = icon.getHeight();

            long score;
            if (iw >= width && ih >= height) {
                // downscale is preferred: minimize excess area
                score = (long) (iw - width) * (long) (ih - height);
            } else {
                // upscale is penalized heavily
                score = 1_000_000L + (long) (width - iw) * (long) (height - ih);
            }

            // tiny tie-breaker: prefer higher effective depth
            score = score * 64L - getEffectiveBitDepth(icon);

            if (score < bestScore) {
                bestScore = score;
                best = icon;
            }
        }

        return best;
    }

    public BufferedImage getHighestQualityIcon(List<BufferedImage> icons) {
        if (icons == null || icons.isEmpty()) return null;
        BufferedImage best = null;
        long bestScore = Long.MIN_VALUE;

        for (BufferedImage icon : icons) {
            long area = (long) icon.getWidth() * (long) icon.getHeight();
            int depth = getEffectiveBitDepth(icon);
            long score = area * (long) Math.max(1, depth);

            if (score > bestScore) {
                bestScore = score;
                best = icon;
            }
        }

        return best;
    }

    public BufferedImage getLargestIcon(List<BufferedImage> icons) {
        if (icons == null || icons.isEmpty()) return null;
        BufferedImage best = null;
        long bestArea = -1;
        for (BufferedImage icon : icons) {
            long area = (long) icon.getWidth() * (long) icon.getHeight();
            if (area > bestArea) {
                bestArea = area;
                best = icon;
            }
        }
        return best;
    }

    private static int getEffectiveBitDepth(BufferedImage image) {
        ColorModel cm = image.getColorModel();
        if (cm instanceof IndexColorModel icm) {
            int mapSize = icm.getMapSize();
            if (mapSize <= 2) return 1;
            if (mapSize <= 16) return 4;
            return 8;
        }
        return cm.hasAlpha() ? 32 : 24;
    }

    // ------------------------------------------------------------
    // Resource/file helpers
    // ------------------------------------------------------------

    private static String[] buildCandidates(String ref) {
        // Dedup preserving order
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (ref != null && !ref.isBlank()) set.add(ref);

        // common locations
        set.add("theme/icon/engineLogo.ico");
        set.add("theme/icon/engineLogo.png");
        set.add("theme/icon/icon.ico");
        set.add("theme/icon/icon.png");
        set.add("Icons/icon.ico");
        set.add("Icons/icon.png");
        set.add("Textures/icon.ico");
        set.add("Textures/icon.png");

        return set.toArray(String[]::new);
    }

    /**
     * Open as: file path -> assetsDir+ref -> classpath.
     */
    private static InputStream openAny(String ref, Path assetsDir, ClassLoader cl) throws IOException {
        // 1) file path
        try {
            Path p = Path.of(ref);
            if (Files.isRegularFile(p)) return Files.newInputStream(p);
        } catch (Exception ignored) {}

        // 2) assetsDir + ref
        if (assetsDir != null) {
            try {
                Path p = assetsDir.resolve(ref).normalize();
                if (Files.isRegularFile(p)) return Files.newInputStream(p);
            } catch (Exception ignored) {}
        }

        // 3) classpath
        String r = ref.startsWith("/") ? ref.substring(1) : ref;

        InputStream in = cl.getResourceAsStream(r);
        if (in != null) return in;

        in = cl.getResourceAsStream("/" + r);
        return in;
    }

    /**
     * If we loaded a single PNG, build a multi-size set so OS chooses optimal icon.
     */
    private static BufferedImage[] makeMultiSizeIcons(BufferedImage src) {
        src = ensureArgb(src);

        int[] sizes = new int[]{16, 24, 32, 48, 64, 128, 256};
        List<BufferedImage> out = new ArrayList<>(sizes.length);

        for (int s : sizes) {
            if (src.getWidth() == s && src.getHeight() == s) {
                out.add(src);
                continue;
            }
            BufferedImage scaled = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            try {
                // keep simple: modern JDKs do decent scaling by default; no hardcoding hints
                g.drawImage(src, 0, 0, s, s, null);
            } finally {
                g.dispose();
            }
            out.add(scaled);
        }
        return out.toArray(BufferedImage[]::new);
    }

    private static byte[] readAllBytesLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(64 * 1024, maxBytes));
        byte[] buf = new byte[16 * 1024];
        int n;
        int total = 0;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) throw new IOException("Stream too large: >" + maxBytes + " bytes");
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    // ------------------------------------------------------------
    // Low-level endian helpers
    // ------------------------------------------------------------

    private static int readLEU16(byte[] data, int off) {
        return (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8);
    }

    private static int readLEI32(byte[] data, int off) {
        return (data[off] & 0xFF)
                | ((data[off + 1] & 0xFF) << 8)
                | ((data[off + 2] & 0xFF) << 16)
                | ((data[off + 3] & 0xFF) << 24);
    }

    private static boolean isPng(byte[] data, int off, int len) {
        if (len < 8) return false;
        for (int i = 0; i < 8; i++) {
            if (data[off + i] != PNG_SIG[i]) return false;
        }
        return true;
    }

    private static int readLEShort(DataInputStream dis) throws IOException {
        int b1 = dis.readUnsignedByte();
        int b2 = dis.readUnsignedByte();
        return (b2 << 8) | b1;
    }

    private static int readLEInt(DataInputStream dis) throws IOException {
        int b1 = dis.readUnsignedByte();
        int b2 = dis.readUnsignedByte();
        int b3 = dis.readUnsignedByte();
        int b4 = dis.readUnsignedByte();
        return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
    }

    // ------------------------------------------------------------
    // Records
    // ------------------------------------------------------------

    private record IconDirEntry(
            int width,
            int height,
            int colorCount,
            int planes,
            int bitCount,
            int bytesInRes,
            int imageOffset,
            int reserved
    ) {}

    public record IconInfo(
            int width,
            int height,
            int bitDepth,
            int colors,
            String format,
            int dataSize
    ) {}

    /**
     * Cache key must not only be (w,h,bpp), because ICO can contain multiple frames
     * with same size but different offsets/quality. Use offset+len too.
     */
    private record IconKey(
            int offset,
            int length,
            int width,
            int height,
            int bitDepth
    ) {}
}