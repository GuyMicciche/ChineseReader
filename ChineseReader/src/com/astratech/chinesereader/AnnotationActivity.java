package com.astratech.chinesereader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ClipDescription;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

public class AnnotationActivity extends AppCompatActivity {
  public final static int ANNOTATE_BUFFER = 0,
    ANNOTATE_SHARE = 1, ANNOTATE_FILE = 2, ANNOTATE_STARRED = 3;

  public ArrayList<ArrayList<Object>> lines = new ArrayList<ArrayList<Object>>();
  public AnnListAdapter linesAdapter;
  public LinearLayoutManager linesLayoutManager;
  public WordPopup wPopup;
  public Point hlIndex = new Point();
  public AnnTask annTask;
  public Toolbar mToolbar;
  public static SharedPreferences sharedPrefs;
  public String curFilePath = "", curSaveName = "";
  public int annoMode;
  public boolean isFirstAnnotation = true,
    isFirstFileAnnotation = true;
  public LineView testView;
  public CustomRecyclerView linesRecyclerView;
  public Activity app;
  public long textLen, startPos, endPos;
  public int curPos;
  public String pastedText = "";
  public RandomAccessFile openedFile;
  public static boolean isActive = false;
  private RecyclerView.ItemAnimator defaultItemAnimator;
  public boolean settingsChanged = false, parentSettingsChanged = false;
  public ArrayList<Bookmark> mBookmarks, mFoundBookmarks;
  public static final int REQUEST_STORAGE_FOR_FILEBROWSER = 1, REQUEST_STORAGE_FOR_SAVE = 2, REQUEST_STORAGE_FOR_STARRED_EXPORT = 3, REQUEST_STORAGE_FOR_STARRED_EXPORT_PINYIN = 4, REQUEST_STORAGE_FOR_STARRED_EXPORT_PLECO = 5, REQUEST_STORAGE_FOR_BOOK = 6, FILEBROWSER_ACTIVITY_CODE = 1, SETTINGS_ACTIVITY_CODE = 2, STARRED_ACTIVITY_CODE = 3, RESULT_SETTINGS_CHANGED = 10;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    app = this;

    try {
      if (findViewById(R.id.lines) != null) {
        AnnTask.updateVars(this);
        checkIsShared();

        return;
      }

      curFilePath = "";
      isFirstAnnotation = true;
      isFirstFileAnnotation = true;

      PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
      sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

      setContentView(R.layout.activity_main);
      mToolbar = (Toolbar) findViewById(R.id.toolbar); // Attaching the layout to the toolbar object
      setSupportActionBar(mToolbar);
      getSupportActionBar().setDisplayShowHomeEnabled(true);
      getSupportActionBar().setIcon(R.drawable.logo_toolbar);

      wPopup = new WordPopup(this);
      testView = new LineView(this);
      testView.updateVars();

      lines = new ArrayList<ArrayList<Object>>();
      linesRecyclerView = (CustomRecyclerView) findViewById(R.id.lines);
      linesRecyclerView.mMainActivity = this;
      linesLayoutManager = new LinearLayoutManager(this);
      linesRecyclerView.setLayoutManager(linesLayoutManager);
      linesAdapter = new AnnListAdapter(this, this.lines, linesRecyclerView, wPopup);
      linesRecyclerView.setAdapter(linesAdapter);
      defaultItemAnimator = linesRecyclerView.getItemAnimator();

      linesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
          super.onScrolled(recyclerView, dx, dy);

          int totalItemCount = linesLayoutManager.getItemCount() - 2;
          int firstVisibleItem = linesLayoutManager.findFirstVisibleItemPosition();
          int lastVisibleItem = linesLayoutManager.findLastVisibleItemPosition() - 1;
          int visibleItemCount = lastVisibleItem - firstVisibleItem;

          if (totalItemCount != 0 && textLen != 0 && totalItemCount > visibleItemCount)
            ((CustomRecyclerView)recyclerView).progress = (startPos + (endPos - startPos) * lastVisibleItem / (float)totalItemCount) / textLen;
          else
            ((CustomRecyclerView)recyclerView).progress = -1;

          if (totalItemCount <= lastVisibleItem && endPos < textLen) {
            if (annTask == null || annTask.status != AsyncTask.Status.RUNNING) {
              annTask = new AnnTask(AnnotationActivity.this, AnnTask.TASK_ANNOTATE, annoMode, curPos, startPos, endPos, 5, 0, lines, new ArrayList<Object>(), pastedText, textLen, openedFile, testView, updateLines, true, mBookmarks);
              annTask.executeWrapper();
            }
            return;
          }

          if (firstVisibleItem == 0 && linesAdapter.showHeader && startPos > 0) {
            if (annTask.status != AsyncTask.Status.RUNNING) {
              annTask = new AnnTask(AnnotationActivity.this, AnnTask.TASK_ANNOTATE_BACK, annoMode, curPos, startPos, endPos, 5, 0, lines, new ArrayList<Object>(), pastedText, textLen, openedFile, testView, updateLines, true, mBookmarks);
              annTask.executeWrapper();
            }
          }
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
          wPopup.dismiss();
        }
      });

      mBookmarks = new ArrayList<>();
      mFoundBookmarks = new ArrayList<>();

    } catch (Exception e) {
      Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  public void annotate(long position) {
    if (annTask != null) {
      annTask.cancel(true);
    }

    lines.clear();
    linesAdapter.notifyDataSetChanged();
    linesAdapter.showHeader = false;
    linesAdapter.showFooter = true;

    wPopup.dismiss();

    startPos = 0;
    curPos = 0;
    endPos = 0;
    isFirstFileAnnotation = true;

    if (annoMode == ANNOTATE_FILE) {
      startPos = Math.max(0, getPreferences(MODE_PRIVATE).getLong(curFilePath, 0));
      if (startPos >= textLen)
        startPos = 0;
      endPos = startPos;

      mBookmarks = Bookmark.readFromFile(curFilePath + ".bookmarks");
      mFoundBookmarks.clear();
    } else if (annoMode == ANNOTATE_STARRED){
      curFilePath = "";
      curSaveName = "Pinyiner_Starred";
    } else {
      curFilePath = "";
      curSaveName = "";
    }

    if (position >= 0)
      startPos = endPos = position;

    if (startPos == 0) {
      linesAdapter.showHeader = false;
    }

    annTask = new AnnTask(AnnotationActivity.this, AnnTask.TASK_ANNOTATE, annoMode, curPos, startPos, endPos, 5, 0, lines, new ArrayList<Object>(), pastedText, textLen, openedFile, testView, updateLines, true, mBookmarks);
    annTask.executeWrapper();
  }

  public void annotate(String filePath) {
    File fileFd = new File(filePath);
    try {
      openedFile = new RandomAccessFile(fileFd, "r");
      textLen = fileFd.length();
      if (annoMode != ANNOTATE_FILE)
        sharedPrefs.edit().putString("lastText", pastedText).commit();
      annoMode = ANNOTATE_FILE;
      curFilePath = filePath;
      curSaveName = fileFd.getName().replaceAll("(\\.[^.]*)$","_Pinyiner");

      setTitle(fileFd.getName());

      annotate(-1);
    } catch (Exception e) {
      Toast.makeText(this.getApplication(), e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  public static String getBufText(Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
      android.text.ClipboardManager clipboard = (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
      if (!clipboard.hasText()) {
        return "";
      } else {
        return clipboard.getText().toString();
      }
    } else {
      android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
      if (clipboard.hasPrimaryClip() && (clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
          clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML))) {
        return clipboard.getPrimaryClip().getItemAt(0).coerceToText(context).toString();
      } else {
        return "";
      }
    }
  }

  AnnTask.AnnInterface updateLines = new AnnTask.AnnInterface() {
    @Override
    public void onCompleted(int task, int splitLineIndex, String pastedText, ArrayList<ArrayList<Object>> tempLines, int curPosNew, long tempStartPos, long tempEndPos, boolean isRemaining, ArrayList<Bookmark> foundBookmarks) {
      if (curSaveName.equals("") && curPos == 0)  {
        StringBuilder fName = new StringBuilder();
        int p = 0, r = 0;
        while (fName.length() < 16 && r < tempLines.size() && (p < tempLines.get(r).size() || r < tempLines.size() - 1)) {
          Object word = tempLines.get(r).get(p);
          if (word instanceof Integer) {
            if (fName.length() > 0) {
              fName.append("-");
            }
            fName.append(Dict.getPinyin((Integer) word).replaceAll("[^a-zA-Z]+", ""));
          } else {
            String s = ((String) word).replaceAll("[^a-zA-Z0-9]+", "");
            fName.append(s.substring(0, Math.min(s.length(), 16)));
          }
          if (++p == tempLines.get(r).size()) {
            p = 0;
            r++;
          }
        }

        curSaveName = fName.toString();
      }

      curPos = curPosNew;

      linesRecyclerView.setItemAnimator(defaultItemAnimator);

      switch (task) {
        case AnnTask.TASK_ANNOTATE:
        case AnnTask.TASK_SPLIT:

          if (isRemaining) {
            linesAdapter.showFooter = true;
          } else {
            linesAdapter.showFooter = false;
          }

          int firstVisiblePosition = linesLayoutManager.findFirstVisibleItemPosition();
          View firstVisible = linesLayoutManager.findViewByPosition(firstVisiblePosition);
          int top = firstVisible != null ? firstVisible.getTop() : 0;

          if (task == AnnTask.TASK_SPLIT) {
            linesRecyclerView.setItemAnimator(null);
            int toRemove = lines.size() - splitLineIndex;
            while (toRemove-- > 0) {
              lines.remove(splitLineIndex);
              linesAdapter.notifyItemRemoved(splitLineIndex + 1);
            }

            if (annoMode == ANNOTATE_FILE && mBookmarks.size() > 0) {
              int bookmarksRemoveFrom = Bookmark.searchClosest(endPos, mFoundBookmarks);
              while (mFoundBookmarks.size() > bookmarksRemoveFrom)
                mFoundBookmarks.remove(bookmarksRemoveFrom);
            }
          }

          int rmCount = -1;
          if (annoMode == ANNOTATE_FILE && !isFirstFileAnnotation && firstVisiblePosition > AnnTask.visibleLines) {
            rmCount = firstVisiblePosition - AnnTask.visibleLines;
            tempStartPos = getPosition(lines, rmCount + 1, 0, true);
            for (int i = 0; i < rmCount; i++) {
              lines.remove(0);
              linesAdapter.notifyItemRemoved(1);
            }
            int bookmarksRemoveUntil = Bookmark.searchClosest(tempStartPos, mFoundBookmarks);
            for (int i = 0; i < bookmarksRemoveUntil; i++)
              mFoundBookmarks.remove(0);
            for (int i = 0; i < mFoundBookmarks.size(); i++)
              mFoundBookmarks.get(i).mLine -= rmCount;
          }

          for (int i = 0; i < foundBookmarks.size(); i++)
            foundBookmarks.get(i).mLine += lines.size();
          mFoundBookmarks.addAll(foundBookmarks);

          lines.addAll(tempLines);
          linesAdapter.notifyItemRangeInserted(lines.size() - tempLines.size() + 1, tempLines.size());

          if (tempStartPos > 0) {
            linesAdapter.showHeader = true;
            if (isFirstFileAnnotation) {
              linesLayoutManager.scrollToPositionWithOffset(1, 0);
            }
          } else {
            linesAdapter.showHeader = false;
          }

          if (rmCount > -1) {
            linesLayoutManager.scrollToPositionWithOffset(AnnTask.visibleLines, top);
          }

          tempLines.clear();

          break;

        case AnnTask.TASK_ANNOTATE_BACK:
          int remainCount = linesLayoutManager.findFirstVisibleItemPosition() + AnnTask.visibleLines * 2;
          if (lines.size() > remainCount) {
            rmCount = lines.size() - remainCount;
            for (int i = 0; i < rmCount; i++) {
              ArrayList<Object> rmLine = lines.remove(remainCount);
              linesAdapter.notifyItemRemoved(remainCount + 1);
              tempEndPos -= LineView.getLineSize(rmLine, annoMode == ANNOTATE_FILE);
            }

            if (annoMode == ANNOTATE_FILE && mFoundBookmarks.size() > 0) {
              int bookmarksRemoveFrom = Bookmark.searchClosest(tempEndPos, mFoundBookmarks);
              while (mFoundBookmarks.size() > bookmarksRemoveFrom)
                mFoundBookmarks.remove(bookmarksRemoveFrom);
            }
          }

          firstVisiblePosition = linesLayoutManager.findFirstVisibleItemPosition();
          int newFirstVisiblePosition = firstVisiblePosition + tempLines.size();
          top = linesLayoutManager.findViewByPosition(firstVisiblePosition).getTop() + linesLayoutManager.findViewByPosition(firstVisiblePosition).getHeight() - linesLayoutManager.findViewByPosition(firstVisiblePosition + 1).getHeight();
          if (tempStartPos == 0)
            linesAdapter.showHeader = false;

          for (int i = 0; i < mFoundBookmarks.size(); i++)
            mFoundBookmarks.get(i).mLine += tempLines.size();
          mFoundBookmarks.addAll(0, foundBookmarks);

          lines.addAll(0, tempLines);
          linesAdapter.notifyItemRangeInserted(1, tempLines.size());
          linesLayoutManager.scrollToPositionWithOffset(newFirstVisiblePosition, top);

          tempLines.clear();

          break;
      }

      isFirstAnnotation = false;
      isFirstFileAnnotation = false;

      startPos = tempStartPos;
      endPos = tempEndPos;

      if (settingsChanged) {
        redraw();
      }

      //update header and footer
      linesAdapter.notifyItemChanged(0);
      linesAdapter.notifyItemChanged(linesAdapter.getItemCount() - 1);
    }
  };

  public void redraw(){
    if (annTask != null && annTask.status == AsyncTask.Status.RUNNING)
      return;

    settingsChanged = false;
    AnnTask.updateVars(this);
    final int currentTop = linesRecyclerView.getChildAt(0).getTop();

    annTask = new AnnTask(AnnotationActivity.this, AnnTask.TASK_ANNOTATE, annoMode, curPos, startPos, endPos, 5, 0, lines, new ArrayList<Object>(), pastedText, textLen, openedFile, testView, updateLines, true, mBookmarks);
    annTask.redrawLines(linesRecyclerView);
    startPos = annTask.startPos;
    endPos = annTask.endPos;

    linesAdapter.notifyDataSetChanged();

    if (startPos > 0)
      linesAdapter.showHeader = true;

    final RecyclerView ll = linesRecyclerView;
    ll.clearFocus();
    ll.post(new Runnable() {
      @Override
      public void run() {
        ll.requestFocusFromTouch();
        ((LinearLayoutManager)ll.getLayoutManager()).scrollToPositionWithOffset(1, currentTop);
        ll.requestFocus();
      }
    });
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle presses on the action bar items
    try {
      switch (item.getItemId()) {
        case R.id.action_settings:
          Intent i = new Intent(this, SettingsActivity.class);
          startActivityForResult(i, SETTINGS_ACTIVITY_CODE);
          return true;

        case R.id.action_goto:
          AlertDialog.Builder builder = new AlertDialog.Builder(this);
          builder.setTitle("Go To ... %");
          final EditText inputGoTo = new EditText(this);
          inputGoTo.setInputType(InputType.TYPE_CLASS_NUMBER);
          InputFilter[] fa= new InputFilter[1];
          fa[0] = new InputFilter.LengthFilter(2);
          inputGoTo.setFilters(fa);
          inputGoTo.setText(Integer.toString((int) (Math.min(linesRecyclerView.progress * 100, 99))));
          inputGoTo.setGravity(Gravity.CENTER);
          inputGoTo.setSelection(inputGoTo.getText().length());
          builder.setView(inputGoTo);
          builder.setPositiveButton("Go", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              int newPercent = 0;

              try {
                newPercent = Math.max(0, Math.min(Integer.parseInt(inputGoTo.getText().toString()), 100));
                long newPos = Math.round(textLen * newPercent / 100);
                annotate(newPos);
                dialog.dismiss();
              } catch(NumberFormatException nfe) {
                Toast.makeText(AnnotationActivity.this, "Invalid percent number", Toast.LENGTH_LONG).show();
              }
            }
          });
          builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) { dialog.cancel();
            }
          });
          AlertDialog dialog = builder.create();
          dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

          dialog.show();
          break;
      }
    } catch (Exception e) {
      Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    return false;
  }


  private static String dumpFilePath;
  private static FileWriter dumpFileWriter;
  private static Button dumpStartButton;
  private static ProgressBar dumpProgress;
  private static TextView dumpProgressText;
  private static boolean dumpCancelled;
  AnnTask.AnnInterface dumpPinyinAnnInterface = new AnnTask.AnnInterface() {
    @Override
    public void onCompleted(int task, int splitLineIndex, String pastedText, ArrayList<ArrayList<Object>> tempLines, int curPos, long tempStartPos, long tempEndPos, boolean isRemaining, ArrayList<Bookmark> foundBookmarks) {

      try {

        for (ArrayList<Object> line : tempLines) {
          Object lastWord = null;

          for (Object word : line) {
            if (lastWord != null && (lastWord instanceof Integer || lastWord.getClass() != word.getClass()))
              dumpFileWriter.write(" ");

            if (word instanceof String)
              if (((String)word).length() == 0)
                dumpFileWriter.write("\n\r");
              else
                dumpFileWriter.write((String) word);
            else
              dumpFileWriter.write(Dict.pinyinToTones(Dict.getPinyin((Integer) word)));

            lastWord = word;
          }
        }

        if (isRemaining && !dumpCancelled) {
          int progress = Math.round(tempEndPos * 100 / textLen);
          dumpProgress.setProgress(progress);
          dumpProgressText.setText(Integer.toString(progress) + "%");
          dumpPinyin(tempStartPos, tempEndPos);
        } else {
          dumpFileWriter.flush();
          dumpFileWriter.close();

          dumpStartButton.setEnabled(true);
          dumpProgress.setVisibility(View.GONE);
          dumpProgressText.setVisibility(View.GONE);
          Toast.makeText(AnnotationActivity.this.getApplication(), "Saved to " + dumpFilePath, Toast.LENGTH_LONG).show();
        }

      } catch (Exception e) {
        Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
      }
    }
  };

  public void dumpPinyin(long startPos, long endPos) {
    AnnTask annTask = new AnnTask(getApplicationContext(), AnnTask.TASK_ANNOTATE, annoMode, curPos, startPos, endPos, 5, 0, new ArrayList<ArrayList<Object>>(), new ArrayList<Object>(), pastedText, textLen, openedFile, null, dumpPinyinAnnInterface, false, null);
    annTask.executeWrapper();
  }

  AnnTask.AnnInterface dumpBothAnnInterface = new AnnTask.AnnInterface() {
    @Override
    public void onCompleted(int task, int splitLineIndex, String pastedText, ArrayList<ArrayList<Object>> tempLines, int curPos, long tempStartPos, long tempEndPos, boolean isRemaining, ArrayList<Bookmark> foundBookmarks) {

      try {
        StringBuilder textLine = new StringBuilder();

        for (ArrayList<Object> line : tempLines) {
          Object lastWord = null;

          textLine.setLength(0);
          for (Object word : line) {
            if (lastWord != null && word instanceof Integer) {
              dumpFileWriter.write("\t");
              textLine.append("\t");
            }

            if (word instanceof String) {
              if (((String) word).length() != 0) {
                textLine.append(((String) word).replaceAll("\t", ""));
              }
            }
            else {
              dumpFileWriter.write(Dict.pinyinToTones(Dict.getPinyin((Integer) word)));
              textLine.append(Dict.getCh((Integer) word));
            }

            if (lastWord != null || !(word instanceof String))
              lastWord = word;
          }

          dumpFileWriter.write("\n\r");
          dumpFileWriter.write(textLine.toString());
          dumpFileWriter.write("\n\r");
        }

        if (isRemaining && !dumpCancelled) {
          int progress = Math.round(tempEndPos * 100 / textLen);
          dumpProgress.setProgress(progress);
          dumpProgressText.setText(Integer.toString(progress) + "%");
          dumpBoth(tempStartPos, tempEndPos);
        } else {
          dumpFileWriter.flush();
          dumpFileWriter.close();

          dumpStartButton.setEnabled(true);
          dumpProgress.setVisibility(View.GONE);
          dumpProgressText.setVisibility(View.GONE);
          Toast.makeText(AnnotationActivity.this.getApplication(), "Saved to " + dumpFilePath, Toast.LENGTH_LONG).show();
        }

      } catch (Exception e) {
        Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
      }
    }
  };

  public void dumpBoth(long startPos, long endPos) {
    AnnTask annTask = new AnnTask(getApplicationContext(), AnnTask.TASK_ANNOTATE, annoMode, curPos, startPos, endPos, 5, 0, new ArrayList<ArrayList<Object>>(), new ArrayList<Object>(), pastedText, textLen, openedFile, testView, dumpBothAnnInterface, true, null);
    annTask.executeWrapper();
  }

  public void saveToFile() {
    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
    dialogBuilder.setTitle("Save to file");

    final View views = getLayoutInflater().inflate(R.layout.alertdialog_save, null);
    final EditText input = (EditText) views.findViewById(R.id.edit);

    int counter = 0;
    StringBuilder tempName = new StringBuilder(curSaveName);
    while (true) {
      File file = new File(Environment.getExternalStorageDirectory() + "/Pinyiner/", tempName.toString() + ".txt");
      if (file.exists()) {
        counter++;
        tempName.setLength(0);
        tempName.append(curSaveName).append("(").append(counter).append(")");
      } else {
        input.setText(tempName);
        break;
      }
    }

    dialogBuilder.setView(views);

    dialogBuilder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int whichButton) {
      }
    });

    dialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int whichButton) {
      }
    });

    final AlertDialog alert = dialogBuilder.show();

    dumpStartButton = alert.getButton(DialogInterface.BUTTON_POSITIVE);
    dumpStartButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        try {
          File dir = new File(Environment.getExternalStorageDirectory() + "/Pinyiner/");
          if (!dir.exists())
            dir.mkdirs();

          if (((RadioButton) views.findViewById(R.id.radio_text)).isChecked()) {
            String path = dir.getAbsolutePath() + "/" + input.getText().toString() + ".txt";

            if (annoMode == ANNOTATE_FILE) {
              FileInputStream fis = new FileInputStream(curFilePath);
              FileOutputStream fos = new FileOutputStream(path);
              copyFile(fis, fos);
              fis.close();
              fos.close();
            } else {
              FileWriter fw = new FileWriter(path);
              fw.write(pastedText, 0, pastedText.length());
              fw.flush();
              fw.close();
            }

            Toast.makeText(AnnotationActivity.this.getApplication(), "Saved to " + path, Toast.LENGTH_LONG).show();
          } else if (((RadioButton) views.findViewById(R.id.radio_pinyin)).isChecked()) {
            String path = dir.getAbsolutePath() + "/" + input.getText().toString() + ".txt";

            view.setEnabled(false);
            dumpFilePath = path;
            dumpFileWriter = new FileWriter(path);
            dumpProgress = (ProgressBar) views.findViewById(R.id.progress);
            dumpProgressText = (TextView) views.findViewById(R.id.progress_text);
            dumpProgress.setVisibility(View.VISIBLE);
            dumpProgressText.setVisibility(View.VISIBLE);
            dumpCancelled = false;
            dumpPinyin(0, 0);
          } else if (((RadioButton) views.findViewById(R.id.radio_both)).isChecked()) {
            String path = dir.getAbsolutePath() + "/" + input.getText().toString() + ".tsv";

            view.setEnabled(false);
            dumpFilePath = path;
            dumpFileWriter = new FileWriter(path);
            dumpProgress = (ProgressBar) views.findViewById(R.id.progress);
            dumpProgressText = (TextView) views.findViewById(R.id.progress_text);
            dumpProgress.setVisibility(View.VISIBLE);
            dumpProgressText.setVisibility(View.VISIBLE);
            dumpCancelled = false;
            dumpBoth(0, 0);
          }

        } catch (Exception e) {
          Toast.makeText(AnnotationActivity.this.getApplication(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
      }
    });

    Button dumpCancelButton = alert.getButton(DialogInterface.BUTTON_NEGATIVE);
    dumpCancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        dumpCancelled = true;

        alert.dismiss();
      }
    });
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case SETTINGS_ACTIVITY_CODE:
        if (resultCode == RESULT_SETTINGS_CHANGED) {
          settingsChanged = true;
        }
        break;
    }

    super.onActivityResult(requestCode, resultCode, data);
  }

  public void splitAnnotation(int splitLineIndex, int curWidth, ArrayList<Object> curLine) {
    if (annTask != null && annTask.status == AsyncTask.Status.RUNNING) {
      annTask.cancel(true);
    }

    annTask = new AnnTask(AnnotationActivity.this, AnnTask.TASK_SPLIT, annoMode, curPos, startPos, endPos, curWidth, splitLineIndex, lines, curLine, pastedText, textLen, openedFile, testView, updateLines, true, mBookmarks);
    annTask.executeWrapper();
  }

  public boolean checkIsShared() {
    Intent intent = getIntent();
    String action = intent.getAction();
    String type = intent.getType();

    if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_PROCESS_TEXT.equals(action)) && type != null && "text/plain".equals(type)) {
      String text = null;
      if (Intent.ACTION_SEND.equals(action))
        text = intent.getStringExtra(Intent.EXTRA_TEXT);
      else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)//ACTION_PROCESS_TEXT
        text = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);

      if (text != null && text.length() > 0) {
        saveFilePos();
        pastedText = text;
        textLen = pastedText.length();
      } else {
        return false;
      }
      intent.removeExtra(Intent.EXTRA_TEXT);
      mFoundBookmarks.clear();
      mBookmarks.clear();
      annoMode = ANNOTATE_SHARE;
      annotate(-1);
      return true;
    }

    return false;
  }

  //When rotated
  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    settingsChanged = true;

    wPopup.configure(this.getApplication());
    wPopup.dismiss();

    if (curPos > 0) {
      if (annTask.status == AsyncTask.Status.RUNNING) {
        annTask.cancel(true);
        return;
      }

      redraw();
    }
  }

  //When settings are set
  @Override
  public void onResume() {
    super.onResume();

    isActive = true;

    NotificationManager mNotificationManager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    mNotificationManager.cancelAll();

    if (checkIsShared()) {
      return;
    }

    if (settingsChanged) {
      wPopup.dismiss();
      wPopup.configure(this.getApplication());

      redraw();
    }
  }

  @Override
  protected void onNewIntent (Intent intent)  {
    super.onNewIntent(intent);
    setIntent(intent);

    checkIsShared();
  }

  public long getPosition(ArrayList<ArrayList<Object>> list, int lineNum, int wordNum, boolean isFile) {
    long pos = startPos;

    for (int i = 0; i < lineNum; i++) {
      ArrayList row = list.get(i);
      int len = row.size();

      if (i == lineNum - 1 && wordNum > -1)
        len = wordNum;

      for (int j = 0; j < len; j++) {
        Object word = row.get(j);

        if (word instanceof String) {
          try {
            int wordLen;
            if (isFile)
              wordLen = ((String) word).getBytes("UTF-8").length;
            else
              wordLen = ((String) word).length();

            if (wordLen == 0) {
              pos += 1;
            } else {
              pos += wordLen;
            }
          } catch (Exception e) {
            Toast.makeText(this.getApplication(), e.getMessage(), Toast.LENGTH_LONG).show();
          }
        } else {
          if (isFile)
            pos += Dict.getLength((Integer)word) * 3;
          else
            pos += Dict.getLength((Integer)word);
        }
      }
    }

    return pos;
  }
//TODO: called too often
  public void saveFilePos() {
    if (curFilePath.equals(""))
      return;

    int curRow = linesLayoutManager.findFirstVisibleItemPosition() - 1;

    getPreferences(MODE_PRIVATE).edit().putLong(curFilePath, getPosition(lines, curRow, -1, true)).commit();

    ArrayList<String> recent = new ArrayList<String>();
    for (int i = 0; i < 4; i++) {
      String item = getPreferences(MODE_PRIVATE).getString("recent" + i, "");
      if (item.length() > 0) {
        recent.add(item);
      }
    }
    int pos = recent.indexOf(curFilePath);
    if (pos > -1) {
      recent.remove(pos);
    }
    if (recent.size() > 3) {
      recent.remove(3);
    }
    recent.add(0, curFilePath);

    for (int i = 0; i < recent.size(); i++) {
      getPreferences(MODE_PRIVATE).edit().putString("recent" + i, recent.get(i)).commit();
    }

    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
      supportInvalidateOptionsMenu();
    } else {
      invalidateOptionsMenu();
    }
  }

  public static void copyFile(InputStream is, OutputStream os) {
    try {
      byte[] buffer = new byte[1024];
      int readLen = 0;
      while ((readLen = is.read(buffer)) > 0) {
        os.write(buffer, 0, readLen);
      }
    } catch (Exception e) {}
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
      super.onBackPressed();
    }
  }
}