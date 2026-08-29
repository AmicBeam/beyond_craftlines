package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record ResourceMatchRule(Mode mode, Set<String> ignoredPaths)
{
    public static final ResourceMatchRule STRICT = new ResourceMatchRule(Mode.STRICT, Set.of());
    public static final ResourceMatchRule ITEM_ONLY = new ResourceMatchRule(Mode.ITEM_ONLY, Set.of());
    public ResourceMatchRule
    {
        mode = mode == null ? Mode.STRICT : mode;
        ignoredPaths = ignoredPaths == null ? Set.of() : ignoredPaths.stream()
                .filter(path -> path != null && path.startsWith("/") && path.length() <= 512)
                .limit(20).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (mode != Mode.IGNORE_PATHS) ignoredPaths = Set.of();
    }
    public boolean matches(IStackKey<?> left, IStackKey<?> right)
    {
        if (left == null || right == null) return false;
        if (mode == Mode.ITEM_ONLY) return left.isSame(right) || right.isSame(left);
        if (mode == Mode.STRICT) return StackKeyMatch.exact(left, right);
        if (!(left.isSame(right) || right.isSame(left))) return false;
        return flattened(left, ignoredPaths).equals(flattened(right, ignoredPaths));
    }
    public static List<String> differences(IStackKey<?> left, IStackKey<?> right)
    {
        Map<String,String> a=flattened(left,Set.of()),b=flattened(right,Set.of());
        LinkedHashSet<String> result=new LinkedHashSet<>();result.addAll(a.keySet());result.addAll(b.keySet());
        result.removeIf(path->java.util.Objects.equals(a.get(path),b.get(path)));
        return result.stream().sorted().limit(20).toList();
    }
    public String encode()
    {
        if(mode!=Mode.IGNORE_PATHS)return mode.id;
        return mode.id+";"+ignoredPaths.stream().sorted().map(path->Base64.getUrlEncoder().withoutPadding()
                .encodeToString(path.getBytes(StandardCharsets.UTF_8))).collect(java.util.stream.Collectors.joining(","));
    }
    public static ResourceMatchRule decode(String encoded)
    {
        if(encoded==null||encoded.isBlank())return STRICT;String[] sections=encoded.split(";",2);Mode mode=Mode.byId(sections[0]);
        if(mode!=Mode.IGNORE_PATHS||sections.length<2)return new ResourceMatchRule(mode,Set.of());
        LinkedHashSet<String> paths=new LinkedHashSet<>();for(String value:sections[1].split(","))try{paths.add(new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8));}catch(IllegalArgumentException ignored){}
        return new ResourceMatchRule(mode,paths);
    }
    private static Map<String,String> flattened(IStackKey<?> key,Set<String> ignored)
    {
        LinkedHashMap<String,String> result=new LinkedHashMap<>();try{Object tag=key.serializeNBT(com.wintercogs.beyonddimensions.util.RegistryAccessResolver.resolve());flatten(tag,"",result,java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));}catch(Throwable error){result.put("/fallback",key.toString());}ignored.forEach(result::remove);return Map.copyOf(result);
    }
    private static void flatten(Object value,String path,Map<String,String> result,Set<Object> seen)
    {
        if(value==null){result.put(path,"null");return;}if(!seen.add(value)){result.put(path,"<cycle>");return;}try{Collection<?> keys=keys(value);if(keys!=null){for(Object raw:keys){String key=String.valueOf(raw);flatten(child(value,key),path+"/"+escape(key),result,seen);}return;}if(value instanceof List<?> list){for(int i=0;i<list.size();i++)flatten(list.get(i),path+"/["+i+"]",result,seen);return;}result.put(path.isEmpty()?"/":path,value.toString());}finally{seen.remove(value);}
    }
    private static Collection<?> keys(Object value){for(String name:List.of("getAllKeys","keySet"))try{Object keys=value.getClass().getMethod(name).invoke(value);if(keys instanceof Collection<?> c)return c;}catch(ReflectiveOperationException ignored){}return null;}
    private static Object child(Object value,String key){try{Method method=value.getClass().getMethod("get",String.class);Object child=method.invoke(value,key);return child instanceof Optional<?> o?o.orElse(null):child;}catch(ReflectiveOperationException ignored){return null;}}
    private static String escape(String value){return value.replace("~","~0").replace("/","~1");}
    public enum Mode{STRICT("strict"),IGNORE_PATHS("paths"),ITEM_ONLY("item");private final String id;Mode(String id){this.id=id;}public String id(){return id;}public static Mode byId(String id){for(Mode mode:values())if(mode.id.equals(id))return mode;return STRICT;}}
}
