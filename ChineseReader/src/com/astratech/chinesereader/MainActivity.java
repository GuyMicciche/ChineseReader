package com.astratech.chinesereader;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by lastant on 7/16/2016.
 */
public class MainActivity extends AnnotationActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);


    try {
      PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
      int oldVer = sharedPrefs.getInt("version", 0);
      if (oldVer != pInfo.versionCode) {
        String stars = sharedPrefs.getString("stars", "");
        stars = stars.replaceAll(" ", "");
        if (stars.endsWith(";"))
          stars = stars.substring(1).replaceAll(";", "\n");

        sharedPrefs.edit().putString("stars", stars).commit();

        new File(getFilesDir(), "dict.db").delete();
        new File(getFilesDir(), "idx.db").delete();
        new File(getFilesDir(), "entries.bin").delete();
        new File(getFilesDir(), "parts.bin").delete();
        new File(Environment.getExternalStorageDirectory() + "/Pinyiner/").mkdir();
        sharedPrefs.edit().putInt("version", pInfo.versionCode).commit();
      }
      if (oldVer <= 110) {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_FOR_BOOK);
      }
      if (0 < oldVer && oldVer <= 370) {
        String prefTextSize = sharedPrefs.getString("pref_textSize", "medium");
        int textSizeInt = 16;
        if (prefTextSize.equals("small"))
          textSizeInt = 13;
        if (prefTextSize.equals("medium"))
          textSizeInt = 16;
        if (prefTextSize.equals("large"))
          textSizeInt = 19;
        if (prefTextSize.equals("extra"))
          textSizeInt = 23;

        String prefPinyinSize = sharedPrefs.getString("pref_pinyinSize", "medium");
        int pinyinSizeInt = 100;
        if (prefPinyinSize.equals("small"))
          pinyinSizeInt = 13 * 100 / textSizeInt;
        if (prefPinyinSize.equals("medium"))
          pinyinSizeInt = 16 * 100 / textSizeInt;
        if (prefPinyinSize.equals("large"))
          pinyinSizeInt = 19 * 100 / textSizeInt;
        if (prefPinyinSize.equals("extra"))
          pinyinSizeInt = 23 * 100 / textSizeInt;

        sharedPrefs.edit().putInt("pref_textSizeInt", textSizeInt).putInt("pref_pinyinSizeInt", pinyinSizeInt).commit();
      }

      Dict.loadDict(this.getApplication());

      pastedText = getBufText(this);
      textLen = pastedText.length();
      if (isFirstAnnotation) {
        String lastFile = sharedPrefs.getString("lastFile", "");
        if (lastFile.length() > 0 && sharedPrefs.getString("lastText", "").equals(pastedText)) {
          annotate(lastFile);
          return;
        }
      }

      setTitle("Pinyiner");

      if (!checkIsShared())
        if (textLen == 0)
          Toast.makeText(this.getApplication(), getString(R.string.msg_empty), Toast.LENGTH_LONG).show();
        else {
          annoMode = ANNOTATE_BUFFER;
          annotate(-1);
        }

    } catch (Exception e) {
      Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onPrepareOptionsMenu (Menu menu) {
    MenuItem openMenu = menu.findItem(R.id.menu_open_button);
    if (openMenu != null) {
      SubMenu subMenu = openMenu.getSubMenu();
      for (int i = 0; i < 4; i++) {
        subMenu.removeItem(i);
        String item = getPreferences(MODE_PRIVATE).getString("recent" + i, "");
        if (item.length() > 0) {
          subMenu.add(Menu.NONE, i, Menu.CATEGORY_SECONDARY, item.substring(item.lastIndexOf('/') + 1));
        }
      }
    }

    MenuItem gotoMenu = menu.findItem(R.id.action_goto);
    if (linesRecyclerView.progress == -1)
      gotoMenu.setVisible(false);
    else
      gotoMenu.setVisible(true);

    MenuItem bookmarksMenu = menu.findItem(R.id.menu_bookmarks);
    if (annoMode == ANNOTATE_FILE)
      bookmarksMenu.setVisible(true);
    else
      bookmarksMenu.setVisible(false);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle presses on the action bar items
    try {
      switch (item.getItemId()) {
        case R.id.action_paste:
          pastedText = getBufText(this);
          if (pastedText.length() == 0) {
            Toast.makeText(this.getApplication(), getString(R.string.msg_empty), Toast.LENGTH_LONG).show();
            return true;
          }

          saveFilePos();
          annoMode = ANNOTATE_BUFFER;
          textLen = pastedText.length();
          setTitle("Pinyiner");
          mBookmarks.clear();
          mFoundBookmarks.clear();
          annotate(-1);
          return true;

        case R.id.action_open:
          if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_FOR_FILEBROWSER);
          else
            openFileBrowser();

          return true;

        case R.id.action_save:
          if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_STORAGE_FOR_SAVE);
          else
            saveToFile();

          return true;

        case R.id.action_about:
          AboutDialog about = new AboutDialog(this);
          about.show();
          return true;

        case R.id.menu_starred:
          if (annTask != null && annTask.status == AsyncTask.Status.RUNNING) {
            annTask.cancel(true);
          }
          saveFilePos();
          startActivityForResult(new Intent(MainActivity.this, StarredActivity.class), STARRED_ACTIVITY_CODE);

          return true;

        case R.id.menu_bookmarks:
          AlertDialog.Builder builderSingle = new AlertDialog.Builder(MainActivity.this);
          builderSingle.setIcon(R.drawable.ic_launcher);
          builderSingle.setTitle("Bookmarks");

          final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(
              MainActivity.this,
              android.R.layout.select_dialog_item);

          for (int i = 0; i < mBookmarks.size(); i++)
            arrayAdapter.add(mBookmarks.get(i).mTitle);

          builderSingle.setNegativeButton(
              "Cancel",
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  dialog.dismiss();
                }
              });

          builderSingle.setAdapter(
              arrayAdapter,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  annotate(mBookmarks.get(which).mPosition);

                }
              });
          builderSingle.show();

          return true;

        default:
          if (item.getItemId() < 4) {
            String path = getPreferences(MODE_PRIVATE).getString("recent" + item.getItemId(), "");
            saveFilePos();
            annotate(path);
          }

          return super.onOptionsItemSelected(item);
      }
    } catch (Exception e) {
      Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    return false;
  }

  public void openFileBrowser() {
    Intent fileExploreIntent = new Intent(
        FileBrowserActivity.INTENT_ACTION_SELECT_FILE,
        null,
        this,
        com.astratech.chinesereader.FileBrowserActivity.class
    );
    String lastOpenDir = sharedPrefs.getString("lastOpenDir", "");
    if (!lastOpenDir.equals(""))
      fileExploreIntent.putExtra(FileBrowserActivity.startDirectoryParameter, lastOpenDir);

    startActivityForResult(
        fileExploreIntent,
        FILEBROWSER_ACTIVITY_CODE
    );
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case FILEBROWSER_ACTIVITY_CODE:
        if (resultCode == RESULT_OK) {
          saveFilePos();
          String file = data.getStringExtra(com.astratech.chinesereader.FileBrowserActivity.returnFileParameter);
          sharedPrefs.edit().putString("lastOpenDir", new File(file).getParent()).commit();
          annotate(file);
        }
        break;
      case STARRED_ACTIVITY_CODE:
        if (resultCode == RESULT_SETTINGS_CHANGED) {
          settingsChanged = true;
        }
        break;
    }

    super.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
      switch (requestCode) {
        case REQUEST_STORAGE_FOR_BOOK:
          try {
            InputStream is = getAssets().open("shei-shenghuo-de-geng-meihao.txt");
            File outFile = new File(Environment.getExternalStorageDirectory(), "/Pinyiner/shei-shenghuo-de-geng-meihao.txt");
            OutputStream out = new FileOutputStream(outFile);
            copyFile(is, out);
            is.close();
            out.flush();
            out.close();
          } catch (Exception e) { }
          break;
        case REQUEST_STORAGE_FOR_FILEBROWSER:
          openFileBrowser();
          break;
        case REQUEST_STORAGE_FOR_SAVE:
          saveToFile();
          break;
      }
  }

  @Override
  protected void onPause() {
    super.onPause();
    saveFilePos();

    if (annoMode == ANNOTATE_FILE)
      sharedPrefs.edit().putString("lastFile", curFilePath).commit();
    else {
      sharedPrefs.edit().putString("lastFile", "").putString("lastText", pastedText).commit();
    }

    isActive = false;
    if (sharedPrefs.getBoolean("pref_monitor", true) && Build.VERSION.SDK_INT >= 11)
      startService(new Intent(this, PinyinerClipboardService.class));
  }
}
