package com.astratech.chinesereader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

/**
 * Created by lastant on 6/1/2015.
 */
public class PinyinerReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    if (sharedPrefs.getBoolean("pref_monitor", true) && Build.VERSION.SDK_INT >= 11) {
      Intent monitorIntent = new Intent(context, PinyinerClipboardService.class);
      context.startService(monitorIntent);
    }
  }
}