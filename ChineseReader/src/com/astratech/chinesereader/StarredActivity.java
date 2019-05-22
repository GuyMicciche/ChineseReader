package com.astratech.chinesereader;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Random;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by lastant on 7/11/2016.
 */
public class StarredActivity extends AnnotationActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    wPopup.dismiss();
    pastedText = sharedPrefs.getString("stars", "");
    textLen = pastedText.length();
    annoMode = ANNOTATE_STARRED;
    annotate(-1);

    int count = 0;
    for (int c = 0; c < textLen; c++)
      if (pastedText.charAt(c) == '\n')
        count++;
    setTitle("Starred(" + count + ")");
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.starred_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle presses on the action bar items
    try {
      switch (item.getItemId()) {
        case R.id.menu_starred_export:
        case R.id.menu_starred_export_pinyin:
          if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                item.getItemId() == R.id.menu_starred_export ? REQUEST_STORAGE_FOR_STARRED_EXPORT: REQUEST_STORAGE_FOR_STARRED_EXPORT_PINYIN);
          else
            starredExport(item.getItemId());

          return true;

        case R.id.menu_starred_export_pleco:
          if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_FOR_STARRED_EXPORT_PLECO);
          else
            starredExportPleco();

          return true;

        case R.id.menu_starred_clear:
          new AlertDialog.Builder(this)
              .setTitle("Clear all starred")
              .setMessage("Are you sure you want to clear all starred words?")
              .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                  sharedPrefs.edit().putString("stars", "").commit();
                  pastedText = "";
                  textLen = 0;
                  lines.clear();
                  linesAdapter.notifyDataSetChanged();
                  linesAdapter.showHeader = false;
                  linesAdapter.showFooter = false;
                  setTitle("Starred(0)");
                  Toast.makeText(StarredActivity.this, "All starred words cleared", Toast.LENGTH_LONG).show();
                }
              })
              .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                }
              })
              .setIcon(android.R.drawable.ic_dialog_alert)
              .show();

          return true;
        default:
          return super.onOptionsItemSelected(item);
      }
    } catch (Exception e) {
      Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    return false;
  }

  public void starredExport(int exportId) {

    String stars = sharedPrefs.getString("stars", "");
    if (stars == null || stars.length() < 2) {
      Toast.makeText(this, "Starred list is empty. Nothing to export.", Toast.LENGTH_LONG).show();
      return;
    }

    Random random = new Random();
    SQLiteDatabase db = openOrCreateDatabase("anki.db", MODE_PRIVATE, null);
    String[] commands = getString(R.string.anki_scheme).split(";;");
    for (String command : commands)
      db.execSQL(command);

    int oldIndex = 0;
    int nIndex;
    while ((nIndex = stars.indexOf("\n", oldIndex)) > -1) {

      int entry = Dict.binarySearch(stars.substring(oldIndex, nIndex), false);
      oldIndex = nIndex + 1;

      if (entry == -1)
        continue;

      String id = Integer.toString(Math.abs(random.nextInt())), uuid = UUID.randomUUID().toString().substring(0, 10);
      StringBuilder english = new StringBuilder();

      if (exportId == R.id.menu_starred_export_pinyin)
        english.append(Dict.pinyinToTones(Dict.getPinyin(entry))).append("<br/>");

      english.append(Dict.getCh(entry)).append("\u001F");

      if (exportId == R.id.menu_starred_export)
        english.append("[ ").append(Dict.pinyinToTones(Dict.getPinyin(entry))).append(" ]<br/>");

      String[] parts = Dict.getEnglish(entry).replace("/", "<br/>• ").split("\\$");
      int j = 0;
      for (String str : parts) {
        if (j++ % 2 == 1)
          english.append("<br/><br/>[ ").append(Dict.pinyinToTones(str)).append(" ]<br/>");
        else {
          english.append("• ");

          int bracketIndex, bracketEnd = 0;
          while ((bracketIndex = str.indexOf("[", bracketEnd)) > -1) {
            english.append(str, bracketEnd, bracketIndex);
            bracketEnd = str.indexOf("]", bracketIndex);
            english.append(Dict.pinyinToTones(str.substring(bracketIndex, bracketEnd)));
          }
          english.append(str, bracketEnd, str.length());
        }
      }

      db.execSQL("insert into notes values(?, ?, 1399962367564, 1400901624, -1, '', ?, 0, 147133787, 0, '')", new String[]{
          id,
          uuid,
          english.toString()
      });

      db.execSQL("insert into cards values(?, ?, 1400901287521, 0, 1400901624, -1, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, '')", new String[]{
          Integer.toString(random.nextInt()),
          id
      });
    }
    db.close();

    try {
      File dir = new File(
          Environment.getExternalStorageDirectory().getAbsolutePath(), "/AnkiDroid");
      if (!dir.exists())
        dir = new File(
            Environment.getExternalStorageDirectory().getAbsolutePath(), "/Pinyiner");

      dir.mkdirs();
      FileOutputStream os = new FileOutputStream(dir.getAbsolutePath() + "/pinyiner_starred.apkg");
      InputStream is = new FileInputStream(getDatabasePath("anki.db").getPath());
      ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os));

      zos.putNextEntry(new ZipEntry("collection.anki2"));
      byte[] buffer = new byte[1024];
      int readLen = 0;
      while ((readLen = is.read(buffer)) > 0) {
        zos.write(buffer, 0, readLen);
      }
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("media"));
      buffer[0] = 0x7b;
      buffer[1] = 0x7d;
      zos.write(buffer, 0, 2);
      zos.closeEntry();
      zos.flush();
      zos.close();
      is.close();
      os.flush();
      os.close();

      Toast.makeText(this, "Successfully exported to " + dir.getAbsolutePath() + "/pinyiner_starred.apkg", Toast.LENGTH_LONG).show();

    } catch (Exception e) {
      Toast.makeText(this, "Could not export: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  public void starredExportPleco() {
    String stars = sharedPrefs.getString("stars", "");
    if (stars.length() < 2) {
      Toast.makeText(this, "Starred list is empty. Nothing to export.", Toast.LENGTH_LONG).show();
      return;
    }

    try {
      File dir = new File(
          Environment.getExternalStorageDirectory().getAbsolutePath(), "/Pinyiner");

      dir.mkdirs();
      FileOutputStream os = new FileOutputStream(dir.getAbsolutePath() + "/pinyiner_starred.txt");
      BufferedOutputStream bos = new BufferedOutputStream(os);

      StringBuilder english = new StringBuilder();

      int oldIndex = 0, nIndex;
      while ((nIndex = stars.indexOf("\n", oldIndex)) > -1) {
        english.setLength(0);

        int entry = Dict.binarySearch(stars.substring(oldIndex, nIndex), false);
        oldIndex = nIndex + 1;

        if (entry == -1)
          continue;

        english.append(Dict.getCh(entry)).append("\t")
            .append(Dict.getPinyin(entry).replaceAll("(\\d)", "$1 ")).append("\t");

        String[] parts = Dict.getEnglish(entry).replace("/", "; ").split("\\$");
        int j = 0;
        for (String str : parts) {
          if (j++ % 2 == 1)
            english.append(" [ ").append(str.replaceAll("(\\d)", "$1 ")).append("] ");
          else {
            english.append(str);
          }
        }
        english.append("\n");

        bos.write(english.toString().getBytes("UTF-8"));
      }
      os.flush();
      bos.flush();
      bos.close();
      os.close();

      Toast.makeText(this, "Successfully exported to " + dir.getAbsolutePath() + "/pinyiner_starred.txt", Toast.LENGTH_LONG).show();

    } catch (Exception e) {
      Toast.makeText(this, "Could not export: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }


  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
      switch (requestCode) {
        case REQUEST_STORAGE_FOR_SAVE:
          saveToFile();
          break;
        case REQUEST_STORAGE_FOR_STARRED_EXPORT:
          starredExport(R.id.menu_starred_export);
          break;
        case REQUEST_STORAGE_FOR_STARRED_EXPORT_PINYIN:
          starredExport(R.id.menu_starred_export_pinyin);
          break;
        case REQUEST_STORAGE_FOR_STARRED_EXPORT_PLECO:
          starredExportPleco();
          break;
      }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case SETTINGS_ACTIVITY_CODE:
        if (resultCode == RESULT_SETTINGS_CHANGED) {
          settingsChanged = true;
          parentSettingsChanged = true;
        }
    }

    super.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public void onBackPressed() {
    if (wPopup.isShowing()) {
      if (wPopup.history.size() > 0) {
        int lastWord = wPopup.history.get(wPopup.history.size() - 1);
        wPopup.history.remove(wPopup.history.size() - 1);
        wPopup.show(wPopup.parent, wPopup.history.size() > 0 ? null : wPopup.line, lastWord, wPopup.showX, false);
      } else {
        wPopup.dismiss();
      }
    } else {
      if (parentSettingsChanged) {
        Intent resultIntent = new Intent();
        setResult(AnnotationActivity.RESULT_SETTINGS_CHANGED, resultIntent);
        finish();
      }
      super.onBackPressed();
    }
  }
}
