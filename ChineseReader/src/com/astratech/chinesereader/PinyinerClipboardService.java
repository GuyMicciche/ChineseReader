/*
 * Copyright 2013 Tristan Waddington
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.astratech.chinesereader;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.widget.RemoteViews;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Monitors the {@link ClipboardManager} for changes and logs the text to a file.
 */
public class PinyinerClipboardService extends Service {
  public static final int NOTIFICATION_ID = 123;
  private static final String FILENAME = "clipboard-history.txt";

  private Context context;
  private File mHistoryFile;
  private ExecutorService mThreadPool = Executors.newSingleThreadExecutor();
  private ClipboardManager mClipboardManager;
  private SharedPreferences sharedPrefs;
  public Dict dict = null;

  @TargetApi(11)
  @Override
  public void onCreate() {
    super.onCreate();

    context = this;

    mHistoryFile = new File(getExternalFilesDir(null), FILENAME);
    mClipboardManager =
        (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    mClipboardManager.addPrimaryClipChangedListener(
        mOnPrimaryClipChangedListener);

    sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
  }

  @TargetApi(11)
  @Override
  public void onDestroy() {
    super.onDestroy();

    if (mClipboardManager != null) {
      mClipboardManager.removePrimaryClipChangedListener(
          mOnPrimaryClipChangedListener);
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private boolean isExternalStorageWritable() {
    String state = Environment.getExternalStorageState();
    if (Environment.MEDIA_MOUNTED.equals(state)) {
      return true;
    }
    return false;
  }

  private ClipboardManager.OnPrimaryClipChangedListener mOnPrimaryClipChangedListener =
      new ClipboardManager.OnPrimaryClipChangedListener() {
        @Override
        public void onPrimaryClipChanged() {
          String pastedText = MainActivity.getBufText(context);

          if (!sharedPrefs.getBoolean("pref_monitor", true) || MainActivity.isActive)
            return;

          int textLen = pastedText.length();
          for (int i = 0; i < textLen; i++)
            if (pastedText.charAt(i) >= '\u25CB' && pastedText.charAt(i) <= '\u9FA5') {
              if (dict == null)
                dict = new Dict();
              if (Dict.entries == null)
                Dict.loadDict(context);
              ArrayList<ArrayList<Object>> lines = new ArrayList<ArrayList<Object>>();
              AnnTask annotateTask = new AnnTask(context, AnnTask.TASK_ANNOTATE, MainActivity.ANNOTATE_BUFFER, 0, 0, 0, 5, 0, lines, new ArrayList<Object>(), pastedText, pastedText.length(), null, new LineView(context), displayNotification, true, null);
              annotateTask.execute();

              break;
            }
        }
      };

  private AnnTask.AnnInterface displayNotification = new AnnTask.AnnInterface() {
    @Override
    public void onCompleted(int task, int splitLineIndex, String pastedText, ArrayList<ArrayList<Object>> tempLines, int curPos, long tempStartPos, long tempEndPos, boolean isRemaining, ArrayList<Bookmark> foundBookmarks) {

      StringBuilder pinyin = new StringBuilder("");

      RemoteViews smallView = null;

      if (Build.VERSION.SDK_INT < 16) {
        for (ArrayList<Object> line : tempLines) {
          Object lastWord = null;

          for (Object word : line) {
            if (lastWord != null && (lastWord instanceof Integer || lastWord.getClass() != word.getClass()))
              pinyin.append(" ");

            if (word instanceof String)
              pinyin.append((String) word);
            else
              pinyin.append(Dict.pinyinToTones(Dict.getPinyin((Integer) word)));

            lastWord = word;
          }
        }

        smallView = new RemoteViews(getPackageName(), R.layout.notification_small);
        smallView.setTextViewText(R.id.notifsmall_text, pinyin);
      } else {
        smallView = new RemoteViews(getPackageName(), R.layout.notification_big);
      }

      dict = null;

      NotificationCompat.Builder mBuilder =
        new NotificationCompat.Builder(getApplicationContext())
            .setSmallIcon(R.drawable.notification)
            .setContentTitle("Pinyiner")
            .setContentText(pinyin)
            .setContent(smallView)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVibrate(new long[0]);


      Intent resultIntent = new Intent(getApplication(), MainActivity.class);
      resultIntent.setAction(Intent.ACTION_SEND);
      resultIntent.setType("text/plain");
      resultIntent.putExtra(Intent.EXTRA_TEXT, pastedText);

// The stack builder object will contain an artificial back stack for the
// started Activity.
// This ensures that navigating backward from the Activity leads out of
// your application to the Home screen.
      TaskStackBuilder stackBuilder = TaskStackBuilder.create(getApplicationContext());
// Adds the back stack for the Intent (but not the Intent itself)
      stackBuilder.addParentStack(MainActivity.class);
// Adds the Intent that starts the Activity to the top of the stack
      stackBuilder.addNextIntent(resultIntent);
      PendingIntent resultPendingIntent =
          stackBuilder.getPendingIntent(
              0,
              PendingIntent.FLAG_UPDATE_CURRENT
          );
      mBuilder.setContentIntent(resultPendingIntent);
      NotificationManager mNotificationManager =
          (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      mNotificationManager.cancelAll();
// mId allows you to update the notification_big later on.
      Notification notif = mBuilder.build();

      if (Build.VERSION.SDK_INT >= 16) {
        LineView lv = new LineView(context);
        lv.line = tempLines.get(0);
        lv.lines = tempLines;
        lv.hlIndex = new Point(-1, -1);
        lv.top = new ArrayList<String>();
        lv.bottom = new ArrayList<String>();
        lv.tones = new ArrayList<Integer>();
        lv.charTypeface = Typeface.DEFAULT;

        int wordHeight = (int)(lv.getWordHeight());
        int lineCount = (int)Math.min(tempLines.size(), Math.floor(256 * lv.scale / wordHeight) + 1);
        int width = Math.round(AnnTask.screenWidth);
        lv.measure(width, wordHeight);
        lv.layout(0, 0, width, wordHeight);
        Bitmap bitmap = Bitmap.createBitmap(width,(int)Math.min(Math.max(64 * lv.scale, wordHeight * lineCount), 256 * lv.scale), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint whiteBg = new Paint();
        whiteBg.setColor(0xFFFFFFFF);
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), whiteBg);

        for (int i = 0; i < lineCount; i++) {
          lv.lines = tempLines;
          lv.line = tempLines.get(i);
          lv.bottom.clear();
          lv.top.clear();
          lv.tones.clear();
          int count = lv.lines.get(i).size();

          if (count == 0 || lv.line.get(count - 1) instanceof String && ((String)lv.line.get(count - 1)).length() == 0 ||
              tempEndPos >= pastedText.length() && i == tempLines.size() - 1)
            lv.lastLine = true;
          else
            lv.lastLine = false;

          for (int j = 0; j < count; j++) {
            Object word = lv.lines.get(i).get(j);

            if (word instanceof String) {
              lv.bottom.add((String) word);
              lv.top.add("");
              lv.tones.add(0);
            } else {
              int entry = (Integer) word;
              String key = Dict.getCh(entry);
              lv.bottom.add(key);
              if (sharedPrefs.getString("pref_pinyinType", "marks").equals("none")) {
                lv.top.add("");
              } else {
                lv.top.add(Dict.pinyinToTones(Dict.getPinyin(entry)));
              }

              if (sharedPrefs.getString("pref_toneColors", "none").equals("none")) {
                lv.tones.add(0);
              } else {
                int tones = Integer.parseInt(Dict.getPinyin(entry).replaceAll("[\\D]", ""));
                int reverseTones = 0;
                while (tones != 0) {
                  reverseTones = reverseTones * 10 + tones % 10;
                  tones = tones / 10;
                }
                lv.tones.add(reverseTones);
              }
            }
          }

          lv.draw(canvas);
          canvas.translate(0, wordHeight);
        }

        RemoteViews bigView = new RemoteViews(getPackageName(), R.layout.notification_big);
        bigView.setImageViewBitmap(R.id.notif_img, bitmap);
        smallView.setImageViewBitmap(R.id.notif_img, Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), (int)(64 * lv.scale)));
        notif.bigContentView = bigView;
      }

      mNotificationManager.notify(NOTIFICATION_ID, notif);
    }
  };
}
