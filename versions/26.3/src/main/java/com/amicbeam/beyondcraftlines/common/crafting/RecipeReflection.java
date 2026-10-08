package com.amicbeam.beyondcraftlines.common.crafting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Safe structural access to public third-party recipe members. */
final class RecipeReflection
{
    private static final ClassValue<ConcurrentMap<String, PublicMember>> PUBLIC_MEMBERS = new ClassValue<>()
    {
        @Override
        protected ConcurrentMap<String, PublicMember> computeValue(Class<?> type)
        { return new ConcurrentHashMap<>(); }
    };

    private RecipeReflection() {}

    static Object readPublicMember(Object target, String name)
    {
        if (target == null || name == null || name.isBlank()) return null;
        PublicMember member;
        try
        {
            Class<?> type = target.getClass();
            member = PUBLIC_MEMBERS.get(type).computeIfAbsent(name,
                    candidate -> findPublicMember(type, candidate));
        }
        catch (RuntimeException | LinkageError ignored) { return null; }
        Method method = member.method();
        if (method != null)
        {
            try
            {
                if (method.canAccess(target) || method.trySetAccessible()) return method.invoke(target);
            }
            catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
        }
        Field field = member.field();
        if (field != null)
        {
            try
            {
                if (field.canAccess(target) || field.trySetAccessible()) return field.get(target);
            }
            catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
        }
        return null;
    }

    private static PublicMember findPublicMember(Class<?> type, String name)
    {
        Method method = null;
        try
        {
            Method candidate = type.getMethod(name);
            if (!Modifier.isStatic(candidate.getModifiers())) method = candidate;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
        Field field = null;
        try
        {
            Field candidate = type.getField(name);
            if (!Modifier.isStatic(candidate.getModifiers())) field = candidate;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
        return new PublicMember(method, field);
    }

    private record PublicMember(Method method, Field field) {}
}
