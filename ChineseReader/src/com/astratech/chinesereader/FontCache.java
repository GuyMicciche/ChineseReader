package com.astratech.chinesereader;

import android.content.Context;
import android.graphics.Typeface;

import java.util.Hashtable;

public class FontCache {

  private static Hashtable<String, Typeface> fontCache = new Hashtable<String, Typeface>();

  public static Typeface get(String name, Context context) {
    Typeface tf = fontCache.get(name);
    if(tf == null) {
      try {
        if (name.equals("default"))
          tf = Typeface.DEFAULT;
        else
          if (name.startsWith("b_"))
            tf = Typeface.create(Typeface.createFromAsset(context.getAssets(), name.substring(2)), Typeface.BOLD);
          else
            tf = Typeface.createFromAsset(context.getAssets(), name);
      }
      catch (Exception e) {
        return null;
      }
      fontCache.put(name, tf);
    }
    return tf;
  }
}