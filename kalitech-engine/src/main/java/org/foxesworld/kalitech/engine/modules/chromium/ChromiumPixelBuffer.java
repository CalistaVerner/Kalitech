package org.foxesworld.kalitech.engine.modules.chromium;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Double-buffered pixel store for OSR frames.
 *
 * Threading:
 * - Producer: JCEF paint thread(s) calls {@link #writeFrame(ByteBuffer, int, int, ByteBufferAllocator)}.
 * - Consumer: jME render thread calls {@link #tryAcquireReadable()} and reads {@link Readable#pixels()}.
 *
 * Pixel format: BGRA (8-bit per channel), 4 bytes per pixel.
 */
public final class ChromiumPixelBuffer {

    private final Object lock = new Object();

    private ByteBuffer front;
    private ByteBuffer back;

    private int width;
    private int height;
    private int capacityBytes;

    private final AtomicInteger seq = new AtomicInteger(0);
    private volatile int publishedSeq = 0;

    public ChromiumPixelBuffer(int initialWidth, int initialHeight, ByteBufferAllocator allocator) {
        Objects.requireNonNull(allocator, "allocator");
        ensureCapacityInternal(initialWidth, initialHeight, allocator);
        this.front = allocator.allocateDirect(capacityBytes);
        this.back = allocator.allocateDirect(capacityBytes);
    }

    /**
     * Writes a full frame into the back buffer and publishes it.
     * The input buffer is not retained.
     */
    public void writeFrame(ByteBuffer srcPixels, int w, int h, ByteBufferAllocator allocator) {
        Objects.requireNonNull(srcPixels, "srcPixels");
        Objects.requireNonNull(allocator, "allocator");

        synchronized (lock) {
            ensureCapacityInternal(w, h, allocator);

            back.clear();

            ByteBuffer src = srcPixels.duplicate();
            src.clear();

            int n = Math.min(back.remaining(), src.remaining());
            if (n > 0) {
                int oldLimit = src.limit();
                src.limit(src.position() + n);
                back.put(src);
                src.limit(oldLimit);
            }

            back.flip();

            ByteBuffer tmp = front;
            front = back;
            back = tmp;

            publishedSeq = seq.incrementAndGet();
        }
    }

    /**
     * Returns null if no frame has been published yet.
     */
    public Readable tryAcquireReadable() {
        int s = publishedSeq;
        if (s == 0) {
            return null;
        }
        return new Readable(this, s);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int capacityBytes() {
        return capacityBytes;
    }

    private void ensureCapacityInternal(int w, int h, ByteBufferAllocator allocator) {
        int reqW = Math.max(1, w);
        int reqH = Math.max(1, h);
        int reqBytes = Math.multiplyExact(Math.multiplyExact(reqW, reqH), 4);

        if (reqBytes != capacityBytes || front == null || back == null) {
            this.width = reqW;
            this.height = reqH;
            this.capacityBytes = reqBytes;

            this.front = allocator.allocateDirect(reqBytes);
            this.back = allocator.allocateDirect(reqBytes);

            this.publishedSeq = 0;
            this.seq.set(0);
        } else {
            this.width = reqW;
            this.height = reqH;
        }
    }

    private ByteBuffer frontUnsafe() {
        return front;
    }

    /**
     * Readable snapshot handle.
     */
    public static final class Readable implements AutoCloseable {
        private final ChromiumPixelBuffer owner;
        private final int seqAtAcquire;

        private Readable(ChromiumPixelBuffer owner, int seqAtAcquire) {
            this.owner = owner;
            this.seqAtAcquire = seqAtAcquire;
        }

        public int width() {
            return owner.width();
        }

        public int height() {
            return owner.height();
        }

        public int sequence() {
            return seqAtAcquire;
        }

        /**
         * Returns a duplicate of the front buffer.
         */
        public ByteBuffer pixels() {
            synchronized (owner.lock) {
                ByteBuffer dup = owner.frontUnsafe().duplicate();
                dup.rewind();
                return dup;
            }
        }

        @Override
        public void close() {
        }
    }

    @FunctionalInterface
    public interface ByteBufferAllocator {
        ByteBuffer allocateDirect(int bytes);
    }
}