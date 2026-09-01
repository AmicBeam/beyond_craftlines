package com.amicbeam.beyondcraftlines.common.network;

import java.util.Arrays;
import java.util.List;

public final class OrderMenuError
{
    private static final String PREFIX="translated\u001f";private static final int MAX_PART=256;private OrderMenuError(){}
    public static String translated(String translationKey,Object...arguments){if(!validKey(translationKey)||arguments.length>4)throw new IllegalArgumentException("invalid order menu error");
        StringBuilder encoded=new StringBuilder(PREFIX).append(translationKey);for(Object argument:arguments){String value=String.valueOf(argument);
            if(value.length()>MAX_PART||value.indexOf('\u001f')>=0)throw new IllegalArgumentException("invalid order menu error argument");encoded.append('\u001f').append(value);}
        if(encoded.length()>512)throw new IllegalArgumentException("order menu error is too long");return encoded.toString();}
    public static Details decode(String encoded){if(encoded==null||!encoded.startsWith(PREFIX)||encoded.length()>512)return null;String[] parts=encoded.substring(PREFIX.length()).split("\u001f",-1);
        if(parts.length<1||parts.length>5||!validKey(parts[0]))return null;return new Details(parts[0],Arrays.asList(parts).subList(1,parts.length));}
    private static boolean validKey(String value){return value!=null&&value.startsWith("error.beyond_craftlines.")&&value.length()<=128&&value.matches("[a-z0-9_.]+" );}
    public record Details(String translationKey,List<String> arguments){public Details{arguments=List.copyOf(arguments);}}
}
