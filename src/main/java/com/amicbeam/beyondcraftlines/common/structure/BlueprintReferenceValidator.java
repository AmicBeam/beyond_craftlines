package com.amicbeam.beyondcraftlines.common.structure;

import java.util.UUID;

public final class BlueprintReferenceValidator
{
    private static final int SHA256_HEX_LENGTH = 64;

    private BlueprintReferenceValidator() {}

    public static boolean isValid(String namespace, String path, String hash)
    {
        if (!"beyond_craftlines".equals(namespace) || path == null || !isSha256(hash)) return false;
        try
        {
            UUID.fromString(path);
            return true;
        }
        catch (IllegalArgumentException ignored)
        {
            return false;
        }
    }

    private static boolean isSha256(String hash)
    {
        if (hash == null || hash.length() != SHA256_HEX_LENGTH) return false;
        for (int i = 0; i < hash.length(); i++)
        {
            char value = hash.charAt(i);
            if (Character.digit(value, 16) < 0) return false;
        }
        return true;
    }
}
