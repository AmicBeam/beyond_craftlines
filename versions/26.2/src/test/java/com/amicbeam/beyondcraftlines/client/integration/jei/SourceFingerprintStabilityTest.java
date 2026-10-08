package com.amicbeam.beyondcraftlines.client.integration.jei;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

final class SourceFingerprintStabilityTest
{
    @Test void presentationIdsMayChangeWithoutInvalidatingSyntheticRecipeSources() throws Exception
    {
        var first = scan("test:presentation_first_login");
        var second = scan("test:presentation_second_login");
        assertEquals(first.get(0), second.get(0));
        assertNotEquals(first.get(1), second.get(1));
    }

    @Test void aBrokenRecipeLayoutDoesNotAbortTheRemainingRecipesInItsCategory() throws Exception
    {
        var type = new RecipeType<>(Identifier.parse("test:generated"), Object.class);
        var category = (IRecipeCategory<?>) java.lang.reflect.Proxy.newProxyInstance(IRecipeCategory.class.getClassLoader(),
                new Class<?>[]{IRecipeCategory.class}, (proxy, method, args) -> method.getName().equals("getRecipeType") ? type : null);
        var lookup = (IRecipeLookup<?>) java.lang.reflect.Proxy.newProxyInstance(IRecipeLookup.class.getClassLoader(),
                new Class<?>[]{IRecipeLookup.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "includeHidden" -> proxy;
                    case "get" -> Stream.of(new Object(), new Object());
                    default -> null;
                });
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var manager = (IRecipeManager) java.lang.reflect.Proxy.newProxyInstance(IRecipeManager.class.getClassLoader(),
                new Class<?>[]{IRecipeManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("createRecipeLookup")) return lookup;
                    if (method.getName().equals("createRecipeLayoutDrawable"))
                    {
                        if (calls.incrementAndGet() == 1) throw new IllegalArgumentException("fixture broken layout");
                        return java.util.Optional.empty();
                    }
                    return null;
                });
        var runtime = (IJeiRuntime) java.lang.reflect.Proxy.newProxyInstance(IJeiRuntime.class.getClassLoader(),
                new Class<?>[]{IJeiRuntime.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getRecipeManager")) return manager;
                    if (method.getName().equals("getJeiHelpers"))
                        return java.lang.reflect.Proxy.newProxyInstance(method.getReturnType().getClassLoader(),
                                new Class<?>[]{method.getReturnType()}, (helper, helperMethod, helperArgs) -> {
                                    if (!helperMethod.getName().equals("getFocusFactory")) return null;
                                    return java.lang.reflect.Proxy.newProxyInstance(helperMethod.getReturnType().getClassLoader(),
                                            new Class<?>[]{helperMethod.getReturnType()}, (focus, focusMethod, focusArgs) -> null);
                                });
                    return null;
                });
        Class<?> taskType = Class.forName(JeiCatalystIndex.class.getName() + "$SearchTask");
        var constructor = taskType.getDeclaredConstructor(IRecipeCategory.class, Identifier.class);
        constructor.setAccessible(true);
        Object task = constructor.newInstance(category, type.getUid());
        var advance = taskType.getDeclaredMethod("advance", IJeiRuntime.class);
        var complete = taskType.getDeclaredMethod("complete");
        advance.setAccessible(true); complete.setAccessible(true);
        assertEquals(true, advance.invoke(task, runtime));
        assertEquals(false, complete.invoke(task));
        assertEquals(true, advance.invoke(task, runtime));
        assertEquals(true, complete.invoke(task));
        assertEquals(2, calls.get());
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> scan(String presentationId) throws Exception
    {
        var type = new RecipeType<>(Identifier.parse("test:generated"), Object.class);
        IRecipeCategory<?> category = (IRecipeCategory<?>) Proxy.newProxyInstance(IRecipeCategory.class.getClassLoader(),
                new Class<?>[]{IRecipeCategory.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getRecipeType" -> type;
                    case "getIdentifier" -> Identifier.parse(presentationId);
                    default -> null;
                });
        IRecipeLookup<?> lookup = (IRecipeLookup<?>) Proxy.newProxyInstance(IRecipeLookup.class.getClassLoader(),
                new Class<?>[]{IRecipeLookup.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "includeHidden" -> proxy;
                    case "get" -> Stream.of(new Object());
                    default -> null;
                });
        IRecipeManager manager = (IRecipeManager) Proxy.newProxyInstance(IRecipeManager.class.getClassLoader(),
                new Class<?>[]{IRecipeManager.class}, (proxy, method, args) -> method.getName().equals("createRecipeLookup") ? lookup : null);
        IJeiRuntime runtime = (IJeiRuntime) Proxy.newProxyInstance(IJeiRuntime.class.getClassLoader(),
                new Class<?>[]{IJeiRuntime.class}, (proxy, method, args) -> method.getName().equals("getRecipeManager") ? manager : null);
        Class<?> taskType = Class.forName(JeiCatalystIndex.class.getName() + "$SourceTask");
        var constructor = taskType.getDeclaredConstructor(Identifier.class, IRecipeCategory.class);
        constructor.setAccessible(true);
        Object task = constructor.newInstance(type.getUid(), category);
        var advance = taskType.getDeclaredMethod("advance", IJeiRuntime.class);
        advance.setAccessible(true);
        advance.invoke(task, runtime);
        advance.invoke(task, runtime);
        var tokens = taskType.getDeclaredField("tokens");
        var legacy = taskType.getDeclaredField("legacyTokens");
        tokens.setAccessible(true); legacy.setAccessible(true);
        return List.of(List.copyOf((List<String>) tokens.get(task)), List.copyOf((List<String>) legacy.get(task)));
    }
}
