package com.joshua.coppergolemjobs;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Only captures synchronous drops from the specific target of a worker attack. */
public final class DropCapture implements AutoCloseable {
    private static final ThreadLocal<DropCapture> ACTIVE = new ThreadLocal<>();
    private final Entity target;
    private final WorkerState worker;
    private final DropCapture previous;
    public DropCapture(Entity target, WorkerState worker) {
        this.target = target;
        this.worker = worker;
        previous = ACTIVE.get();
        ACTIVE.set(this);
    }
    public static boolean collect(Entity source, ItemStack stack) {
        DropCapture capture = ACTIVE.get();
        if (capture == null || capture.target != source) return false;
        if (!stack.isEmpty()) capture.worker.pending.add(stack.copy());
        return true;
    }
    @Override public void close() {
        if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
    }
}
