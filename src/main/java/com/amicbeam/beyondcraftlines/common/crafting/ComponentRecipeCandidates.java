package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.*;

/** Candidate discovery only. Similarity never makes two inventory keys interchangeable. */
public final class ComponentRecipeCandidates
{
    private static final BoundedIdentityCache<IStackKey<?>, Map<String, String>> FIELDS = new BoundedIdentityCache<>(256);
    private ComponentRecipeCandidates() {}
    public static void clear() { FIELDS.clear(); }
    public static boolean sameResource(IStackKey<?> left, IStackKey<?> right)
    { return left != null && right != null && left.getTypeId().equals(right.getTypeId())
            && Objects.equals(left.getSource(), right.getSource()); }

    /** Exact output wins. A selected recipe with ambiguous same-resource outputs needs separate variants. */
    public static KeyAmount selectedOutput(IStackKey<?> requested, List<KeyAmount> outputs)
    {
        for (KeyAmount output : outputs) if (StackKeyMatch.exact(requested, output.key())) return output;
        List<KeyAmount> candidates = outputs.stream().filter(value -> sameResource(requested, value.key())).toList();
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    public static int distance(IStackKey<?> requested, IStackKey<?> candidate)
    {
        if (StackKeyMatch.exact(requested, candidate)) return 0;
        if (!sameResource(requested, candidate)) return Integer.MAX_VALUE;
        Map<String, String> left = FIELDS.computeIfAbsent(requested, ComponentRecipeCandidates::fields);
        Map<String, String> right = FIELDS.computeIfAbsent(candidate, ComponentRecipeCandidates::fields);
        if (left.isEmpty() || right.isEmpty()) return 1_000_000;
        return distance(left, right);
    }
    static int distance(Map<String, String> left, Map<String, String> right)
    {
        Set<String> keys = new HashSet<>(left.keySet()); keys.addAll(right.keySet());
        int cost = 1;
        for (String key : keys) if (!Objects.equals(left.get(key), right.get(key)))
            cost += left.containsKey(key) && right.containsKey(key) ? 2 : 1;
        return cost;
    }
    private static Map<String, String> fields(IStackKey<?> key)
    {
        try
        {
            Map<String, String> fields = new TreeMap<>();
            flatten("", key.serializeNBT(com.wintercogs.beyonddimensions.util.RegistryAccessResolver.resolve()), fields, 0, new int[]{8192});
            return Map.copyOf(fields);
        }
        catch (RuntimeException | LinkageError ignored) { return Map.of(); }
    }
    private static void flatten(String path, Tag value, Map<String, String> fields, int depth, int[] budget)
    {
        if (value == null || fields.size() >= 128 || depth > 32 || budget[0] <= 0 || path.length() > 1024) return;
        if (value instanceof CompoundTag compound)
            for (String name : compound.getAllKeys().stream().sorted().toList())
            {
                if (fields.size() >= 128 || budget[0] <= 0) break;
                flatten(path + "/" + name, compound.get(name), fields, depth + 1, budget);
            }
        else
        {
            String text = value.toString();
            String encoded = value.getId() + ":" + text.substring(0, Math.min(text.length(), 256));
            budget[0] -= path.length() + encoded.length();
            if (budget[0] >= 0) fields.put(path, encoded);
        }
    }
}
