package com.amicbeam.beyondcraftlines.common.crafting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** Safe structural access to public third-party recipe members. */
final class RecipeReflection
{
    private static final ClassValue<PublicMembers> PUBLIC_MEMBERS = new ClassValue<>()
    {
        @Override
        protected PublicMembers computeValue(Class<?> type)
        {
            LinkedHashMap<String, Method> methods = new LinkedHashMap<>();
            try
            {
                for (Method method : type.getMethods())
                    if (method.getParameterCount() == 0 && !Modifier.isStatic(method.getModifiers()))
                        methods.putIfAbsent(method.getName(), method);
            }
            catch (RuntimeException | LinkageError ignored) {}
            LinkedHashMap<String, Field> fields = new LinkedHashMap<>();
            try
            {
                for (Field field : type.getFields())
                    if (!Modifier.isStatic(field.getModifiers()))
                        fields.putIfAbsent(field.getName(), field);
            }
            catch (RuntimeException | LinkageError ignored) {}
            return new PublicMembers(Map.copyOf(methods), Map.copyOf(fields));
        }
    };

    private RecipeReflection() {}

    static Object readPublicMember(Object target, String name)
    {
        if (target == null || name == null || name.isBlank()) return null;
        PublicMembers members;
        try
        { members = PUBLIC_MEMBERS.get(target.getClass()); }
        catch (RuntimeException | LinkageError ignored) { return null; }
        Method method = members.methods().get(name);
        if (method != null)
        {
            try
            {
                if (method.canAccess(target) || method.trySetAccessible()) return method.invoke(target);
            }
            catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
        }
        Field field = members.fields().get(name);
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

    private record PublicMembers(Map<String, Method> methods, Map<String, Field> fields) {}
}
