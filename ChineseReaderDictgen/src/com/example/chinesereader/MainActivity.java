package com.example.chinesereader;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity {

  public static final String EXTRA_MESSAGE = "com.example.myfirstapp.MESSAGE";
  private ArrayList<ArrayList<Word>> lines;
  private MyListAdapter linesAdapter;
  private SQLiteDatabase dictDb;
  private TextView testTv;
  private int widthMeasureSpec;
  private int heightMeasureSpec;
  private int curWidth;
  private int listWidth;
  private WordPopup wPopup;
  private ClipboardManager clipboard;
  private String theText;
  private AnnotateTask annotateTask;
  private int textLen = 0;
  private int visibleLines = 0;

  private int curPos = 0;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    setContentView(R.layout.activity_main);

    testTv = new TextView(this);
    testTv.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    testTv.setTextSize(16.0f);
    testTv.setBackgroundColor(Color.TRANSPARENT);
    testTv.setText("a\na");

    this.lines = new ArrayList<ArrayList<Word>>();
    ListView lv = (ListView) findViewById(R.id.lines);

    lv.setOnScrollListener(new AbsListView.OnScrollListener() {
      @Override
      public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (totalItemCount - firstVisibleItem - visibleItemCount < visibleItemCount &&
            curPos < textLen) {
          if (annotateTask.getStatus() != AsyncTask.Status.RUNNING)  {
            annotateTask = new AnnotateTask();
            annotateTask.execute();
          }
        }
      }

      @Override
      public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (wPopup.isShowing()) {
          wPopup.dismiss();
        }
      }
    });

    LinearLayout ll = (LinearLayout) findViewById(R.id.layout);
    ll.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(android.view.View view) {
        if (wPopup.isShowing()) {
          wPopup.dismiss();
        }
      }
    });

    widthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

    DataBaseHelper dbh = new DataBaseHelper(this);
    dictDb = dbh.openDatabase();

    wPopup = new WordPopup(this, dictDb);

    this.linesAdapter = new MyListAdapter(this, 0, this.lines, wPopup);
    lv.setAdapter(this.linesAdapter);

    annotateTask = new AnnotateTask();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle presses on the action bar items
    switch (item.getItemId()) {
      case R.id.action_paste:

        class Entry {
          public String chinese;
          public String py = "";
          public String ref;
          public boolean found;
        }
        ArrayList<Entry> entries = new ArrayList<Entry>();

        String query = "SELECT chs AS ch1,cht AS ch2,py,eng,weight,_id FROM dic UNION SELECT * FROM (SELECT DISTINCT cht AS ch1,chs AS ch2, CASE WHEN cht IS NOT NULL THEN 0 ELSE 1 END as py,  CASE WHEN cht IS NOT NULL THEN 0 ELSE 1 END as eng, MAX(weight),_id FROM dic WHERE chs != cht GROUP BY cht)  ORDER BY ch1 ASC, weight DESC, _id ASC ";
        Cursor cur = dictDb.rawQuery(query, new String[]{});
        cur.moveToFirst();
        FileOutputStream outputStream = null, idxStream = null;

        theText = "OK";

        try {
          outputStream = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "dict.txt"));
          idxStream = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "idx.txt"));
          outputStream.write("L".getBytes());

          StringBuilder s = new StringBuilder();
          String chs = "", cht = "", py = "", eng = "";
          int outPos = 1;
          while(!cur.isAfterLast()) {
            if (cur.getString(0).trim().equals(chs) && !cur.getString(2).trim().equals("0")) {
              String en = cur.getString(3).trim();
              if (en.length() > 0) {
                if (!eng.equals(en))
                  eng += '$' + cur.getString(2).trim().replaceAll(" ", "") + '$' + en;
              }
            } else {
              if (chs.length() != 0) {
                s.setLength(0);
                s.append(cht).append(eng).append('\n');
                outputStream.write(s.toString().getBytes());

                Entry entry = new Entry();
                entry.chinese = chs;
                entry.py = py.replaceAll(" ", "");
                entry.ref = getCodeString(outPos);
                outPos += s.toString().getBytes().length;
                s.setLength(0);
                entry.found = true;
                entries.add(entry);
              }


              if (cur.getString(2).trim().equals("0")) {
                if (cur.getString(0).equals(chs))
                  Log.e("PINYINER", chs);
                else {
                  Entry entry = new Entry();
                  entry.chinese = cur.getString(0).trim();
                  entry.ref = cur.getString(1).trim();
                  entry.found = false;
                  entries.add(entry);
                }
                chs = "";
              } else {

                chs = cur.getString(0).trim();
                cht = cur.getString(1).trim();
                py = cur.getString(2).trim();
                eng = cur.getString(3).trim();
              }
            }
            cur.moveToNext();
          }
          outputStream.flush();
          outputStream.close();

          for (Entry entry : entries) {
            if (!entry.found) {
              int index = Collections.binarySearch(entries, entry, new Comparator<Entry>() {
                @Override
                public int compare(Entry entry, Entry entry2) {
                  return entry.chinese.compareTo(entry2.ref);
                }
              });

              entry.ref = getCodeString(index);
            }

            s.setLength(0);
            s.append(entry.chinese).append(entry.py).append((char)0).append(entry.ref);
            idxStream.write(s.toString().getBytes());
          }

          idxStream.flush();
          idxStream.close();

        } catch (Exception e) {
          theText = "ERROR: " + e.getMessage();
        }

        String text = "";
        if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
          if (annotateTask.getStatus() != AsyncTask.Status.FINISHED &&
              annotateTask.getStatus() != AsyncTask.Status.PENDING) {
            annotateTask.cancel(true);
          }

          textLen = theText.length();
          curPos = 0;
          wPopup.dismiss();

          LinearLayout ll = (LinearLayout) findViewById(R.id.layout);
          testTv.measure(widthMeasureSpec, heightMeasureSpec);
          visibleLines = ll.getHeight() / testTv.getMeasuredHeight();

          ListView linesList = (ListView) findViewById(R.id.lines);
          listWidth = linesList.getWidth();
          this.lines.clear();

          annotateTask = new AnnotateTask();
          annotateTask.execute();
        }
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  public String getCodeString(int code) {
    StringBuilder numStr = new StringBuilder();
    while (code > 0) {
      numStr.append((char)(code % 128));
      code >>= 7;
    }

    return numStr.toString();
  }

  public boolean containsASCII(String str) {
    int count = str.length();
    for (int i = 0; i < count; i++) {
      if (str.charAt(i) < 128) {
        return true;
      }
    }
    return  false;
  }

  class AnnotateTask extends AsyncTask<Void, ArrayList<Word>, Void> {
    private Integer addedLines  = 0;

    @Override
    protected Void doInBackground(Void... unused) {
      ArrayList<Word> curLine = new ArrayList<Word>();
      curWidth = 0;
      StringBuilder notFound = new StringBuilder("");
      String searchStr = "";
      String[] inS = new String[10];
      boolean isntCh = false;
      while (curPos < textLen && addedLines < visibleLines) {
        searchStr = theText.substring(curPos, curPos + Math.min(10, textLen - curPos));
        isntCh = false;
        for (int j = 0; j < searchStr.length(); j++) {
          if (searchStr.charAt(j) < '○' ||
              searchStr.charAt(j) > '龥') {
            if (j == 0) {
              isntCh = true;
              break;
            }

            searchStr = searchStr.substring(0, j);
            break;
          }
        }

        if (isntCh) {
          notFound.append(searchStr.charAt(0));
          if (isCancelled()) {
            return(null);
          }
          curPos++;
          continue;
        }

        int len = searchStr.length();
        boolean trad = false;

        String query = "SELECT chs,py,eng,weight,length(chs) AS le FROM dic WHERE chs BETWEEN ? AND ? UNION SELECT chs,py,eng,weight,length(chs) FROM dic WHERE chs IN (?,?,?) ORDER BY le DESC, weight DESC LIMIT 6";
        Cursor cur = dictDb.rawQuery(query, new String[]{
            len >= 4 ? searchStr.substring(0, 4) : "a",
            len >= 4 ? searchStr : "a",
            searchStr.substring(0, 1),
            len >= 2 ? searchStr.substring(0, 2) : "a",
            len >= 3 ? searchStr.substring(0, 3) : "a",
        });
        cur.moveToFirst();

        if (cur.isAfterLast()) {
          query = "SELECT cht,py,eng,weight,length(cht) AS le FROM dic WHERE cht BETWEEN ? AND ? UNION SELECT cht,py,eng,weight,length(cht) AS le FROM dic WHERE cht IN (?,?,?) ORDER BY le DESC, weight DESC LIMIT 6";
          cur.close();

          cur = dictDb.rawQuery(query, new String[]{
              len >= 4 ? searchStr.substring(0, 4) : "a",
              len >= 4 ? searchStr : "a",
              searchStr.substring(0, 1),
              len >= 2 ? searchStr.substring(0, 2) : "a",
              len >= 3 ? searchStr.substring(0, 3) : "a",
          });

          cur.moveToFirst();
          trad = true;
        }

        if (!cur.isAfterLast()) {

          if (notFound.length() > 0) {
            curLine = addNotFound(notFound, curLine);
          }

          String cha = cur.getString(0);
          String py = cur.getString(1);

          curLine = addWord(cha, py, trad, curLine);
          if (isCancelled()) {
            return(null);
          }
          curPos += cha.length();
        } else {
          notFound.append(searchStr.charAt(0));
          if (isCancelled()) {
            return(null);
          }
          curPos++;
        }

        cur.close();
      }

      if (notFound.length() > 0) {
        curLine = addNotFound(notFound, curLine);
        if (isCancelled()) {
          return(null);
        }
        curPos += notFound.length();
      }

      curWidth = 0;
      publishProgress(curLine);

      return(null);
    }

    @Override
    protected void onProgressUpdate( ArrayList<Word>... item) {
      lines.add(item[0]);
      linesAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onPostExecute(Void unused) {

    }

    private ArrayList<Word> addNotFound(StringBuilder notFound, ArrayList<Word> curLine) {
      int newline = -1;
      while ((newline = notFound.indexOf("\n")) != -1) {
        if (newline != 0) {
          curLine = addWord(notFound.substring(0, newline), "", false, curLine);
        }
        publishProgress(curLine);
        curLine = new ArrayList<Word>();
        addedLines++;
        curWidth = 0;
        notFound.delete(0, (newline + 1));
      }
      if (notFound.length() > 0) {
        curLine = addWord(notFound.toString(), "", false, curLine);
      }
      notFound.setLength(0);

      return curLine;
    }

    private ArrayList<Word> addWord(String ch, String py, boolean trad, ArrayList<Word> curLine) {
      while(true) {
        if (py == "") {
          testTv.setText(ch);
        } else {
          testTv.setText(py.replaceAll(" ", "").replaceAll("\\d", "") + "\n" + ch);
        }
        testTv.measure(widthMeasureSpec, heightMeasureSpec);
        int tvWidth = testTv.getMeasuredWidth() + 10;

        if (curWidth + tvWidth > listWidth) {
          if (py == "") {
            int sep = breakWord(ch);
            if (sep != 0) {
              curLine.add(new Word(ch.substring(0, sep), "", false));
              ch = ch.substring(sep);
            }
            publishProgress(curLine);
            curLine = new ArrayList<Word>();
            addedLines++;
            curWidth = 0;
          } else {
            publishProgress(curLine);
            curLine = new ArrayList<Word>();
            addedLines++;
            curLine.add(new Word(ch, py, trad));
            curWidth = tvWidth;
            break;
          }

        } else {
          curLine.add(new Word(ch, py, trad));
          curWidth += tvWidth;
          break;
        }
      }

      return curLine;
    }

    private int breakWord(String str) {
      String[] strs = str.split("(?<=[\\p{Punct}\\s+])");
      StringBuilder cand = new StringBuilder(str.length());
      int width = 0, i = 0, candLen = 0;

      while (width < listWidth) {
        candLen = cand.length();

        if (i < strs.length) {
          cand.setLength(0);
          for (int j = 0; j <= i; j++) {
            cand.append(strs[j]);
          }
          testTv.setText(cand.toString());
          testTv.measure(widthMeasureSpec, heightMeasureSpec);
          int tvWidth = testTv.getMeasuredWidth() + 10;
          width = curWidth + tvWidth;

          i++;
        } else {
          break;
        }
      }

      if (i > 0) {
        return candLen;
      } else {
        return 0;
      }
    }
  }
}


