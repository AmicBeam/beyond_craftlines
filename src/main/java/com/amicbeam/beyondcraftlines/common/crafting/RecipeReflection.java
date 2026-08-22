package com.amicbeam.beyondcraftlines.common.crafting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Safe structural access to public third-party recipe members. */
final class RecipeReflection
{
    private RecipeReflection() {}

    static Object readPublicMember(Object target, String name)
    {
        if (target == null || name == null || name.isBlank()) return null;
        try
        {
            Method method = target.getClass().getMethod(name);
            if (method.getParameterCount() == 0 && !Modifier.isStatic(method.getModifiers()))
            {
                if (!method.canAccess(target) && !method.trySetAccessible()) return null;
                return method.invoke(target);
            }
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
        try
        {
            Field field = target.getClass().getField(name);
            if (Modifier.isStatic(field.getModifiers())) return null;
            if (!field.canAccess(target) && !field.trySetAccessible()) return null;
            return field.get(target);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; }
    }
}
