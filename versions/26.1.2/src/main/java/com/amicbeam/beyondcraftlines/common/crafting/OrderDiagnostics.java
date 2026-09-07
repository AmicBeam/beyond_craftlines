package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

public final class OrderDiagnostics
{
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(
            "beyond_craftlines.order");
    public static final String PREFIX = "[NBT-ORDER]";

    private OrderDiagnostics() {}

    public static String resource(IStackKey<?> key)
    {
        if (key == null) return "null";
        String exact;
        try { exact = RecipeResourceResolver.resolutionKey(key); }
        catch (RuntimeException | LinkageError ignored) { exact = "unavailable"; }
        int separator = exact.lastIndexOf('|');
        String digest = separator < 0 ? exact : exact.substring(separator + 1);
        if (digest.length() > 12) digest = digest.substring(0, 12);
        return key.getTypeId() + "/" + key.getModId() + "/" + key.getSource() + "#" + digest;
    }

    public static String token(String token)
    {
        if (token == null) return "null";
        int separator = token.lastIndexOf('|');
        String digest = separator < 0 ? token : token.substring(separator + 1);
        if (digest.length() > 12) digest = digest.substring(0, 12);
        return digest;
    }
}
