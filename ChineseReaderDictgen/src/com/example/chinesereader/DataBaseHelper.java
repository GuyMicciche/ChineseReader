package com.example.chinesereader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class DataBaseHelper {

  private static final String DB_NAME = "dict.db";

  private Context context;

  public DataBaseHelper(Context context) {
    this.context = context;
  }

  public SQLiteDatabase openDatabase() {
    File dbFile = context.getDatabasePath(DB_NAME);

    try {
      copyDatabase(dbFile);
    } catch (IOException e) {
      throw new RuntimeException("Error creating source database", e);
    }

    return SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
  }

  private void copyDatabase(File dbFile) throws IOException {
    dbFile.getParentFile().mkdirs();
    dbFile.createNewFile();
    OutputStream os = new FileOutputStream(dbFile);

    InputStream is = context.getAssets().open(DB_NAME);

    byte[] buffer = new byte[1024];
    while (is.read(buffer) > 0) {
      os.write(buffer);
    }

    os.flush();
    os.close();
    is.close();
  }
}