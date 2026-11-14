package com.amicbeam.beyondcraftlines.common.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Order-local compatibility cache and per-round shadow inventory. No global inventory equality. */
public final class OrderedSlotPlan<K>
{
    public interface Inventory<K>
    {
        int slots();
        K key(int slot);
        long count(int slot);
        long limit(int slot, K key);
        boolean accepts(int slot, K key);
    }

    public record Allocation(int slot, long amount) {}
    private record Probe<K>(int slot, K key) {}
    private final Inventory<K> inventory;
    private final Map<Probe<K>, Boolean> compatibility;
    private final Map<Integer, Integer> gridAssignments = new HashMap<>();
    private final List<K> keys = new ArrayList<>();
    private final List<Long> counts = new ArrayList<>();
    private final java.util.Set<Integer> occupiedGridSlots = new java.util.HashSet<>();
    private int previousGridCell = -1;

    public OrderedSlotPlan(Inventory<K> inventory) { this(inventory, new HashMap<>()); }

    private OrderedSlotPlan(Inventory<K> inventory, Map<Probe<K>, Boolean> compatibility)
    { this.inventory = inventory; this.compatibility = compatibility; }

    /** Another recipe step in this order gets fresh reservations, but reuses compatibility probes. */
    public OrderedSlotPlan<K> newLayout() { return new OrderedSlotPlan<>(inventory, compatibility); }

    public boolean hasAssignment(int gridCell) { return gridAssignments.containsKey(gridCell); }

    /** Refresh quantity/capacity, retaining only this order's item/slot compatibility decisions. */
    public void beginRound()
    {
        keys.clear();
        counts.clear();
        for (int slot = 0; slot < inventory.slots(); slot++)
        {
            keys.add(inventory.key(slot));
            counts.add(inventory.count(slot));
        }
    }

    public List<Allocation> offer(K key, long amount, int gridCell)
    {
        if (amount <= 0) return List.of();
        if (gridCell >= 0)
        {
            Integer assigned = gridAssignments.get(gridCell);
            if (assigned == null)
            {
                // Empty cells reserve the next unused position. A slot rejected by A stays available to B.
                for (int cell = previousGridCell + 1; cell < gridCell; cell++)
                {
                    int empty = nextUnusedSlot();
                    if (empty < 0) return List.of();
                    occupiedGridSlots.add(empty);
                    previousGridCell = cell;
                }
                for (int slot = 0; slot < inventory.slots(); slot++)
                    if (!occupiedGridSlots.contains(slot)
                            && accepts(slot, key)) { assigned = slot; break; }
                if (assigned == null) return List.of();
                gridAssignments.put(gridCell, assigned);
                occupiedGridSlots.add(assigned);
                previousGridCell = gridCell;
            }
            long accepted = reserve(assigned, key, amount);
            return accepted > 0 ? List.of(new Allocation(assigned, accepted)) : List.of();
        }
        List<Allocation> result = new ArrayList<>();
        for (int slot = 0; slot < inventory.slots() && amount > 0; slot++)
        {
            long accepted = reserve(slot, key, amount);
            if (accepted <= 0) continue;
            result.add(new Allocation(slot, accepted));
            amount -= accepted;
        }
        return List.copyOf(result);
    }

    private int nextUnusedSlot()
    {
        for (int slot = 0; slot < inventory.slots(); slot++)
            if (!occupiedGridSlots.contains(slot)) return slot;
        return -1;
    }

    private boolean accepts(int slot, K key)
    { return compatibility.computeIfAbsent(new Probe<>(slot, key), probe -> inventory.accepts(slot, key)); }

    private long reserve(int slot, K key, long amount)
    {
        if (slot >= keys.size() || !accepts(slot, key)) return 0;
        K present = keys.get(slot);
        if (counts.get(slot) > 0 && !Objects.equals(present, key)) return 0;
        long accepted = Math.min(amount, Math.max(0, inventory.limit(slot, key) - counts.get(slot)));
        if (accepted > 0)
        {
            keys.set(slot, key);
            counts.set(slot, counts.get(slot) + accepted);
        }
        return accepted;
    }
}
