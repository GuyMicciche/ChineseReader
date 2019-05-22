package com.astratech.chinesereader;

import android.content.Context;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.RandomAccessFile;
import java.util.ArrayList;

/**
 * Created by lastant on 5/18/2015.
 */
public class AnnTask extends AsyncTask<Void, Void, Integer> {
  public static final int TASK_ANNOTATE = 0, TASK_ANNOTATE_BACK = 2, TASK_SPLIT = 4;
  public ArrayList<ArrayList<Object>>
      lines,
      tempLines = new ArrayList<ArrayList<Object>>(),
      tempBackLines = new ArrayList<ArrayList<Object>>();
  public long textLen, startPos = -1, endPos = 0, tempStartPos, tempEndPos, curFilePos;
  private int task, splitLineIndex, curPos, bufLen, curWidth, notFound, annoMode, hMargin;
  public static int screenWidth, screenHeight, visibleLines, perLine;
  public Status status = Status.FINISHED;
  private String pastedText, bufText;
  private ArrayList<Object> curLine;
  private Context activity;
  private RandomAccessFile openedFile;
  private static LineView testView;
  private AnnInterface inter;
  private static boolean formatting;
  private boolean mHopelessBreak;
  private ArrayList<Bookmark> bookmarks, foundBookmarks;

  public interface AnnInterface {
    void onCompleted(int task, int splitLineIndex, String pastedText, ArrayList<ArrayList<Object>> tempLines, int curPos, long tempStartPos, long tempEndPos, boolean isRemaining, ArrayList<Bookmark> foundBookmarks);
  }

  public AnnTask(Context activity, int task, int annoMode, int curPos, long startPos, long endPos, int curWidth, int splitLineIndex, ArrayList<ArrayList<Object>> lines, ArrayList<Object> curLine, String pastedText, long textLen, RandomAccessFile openedFile, LineView testView, AnnInterface inter, boolean formatting, ArrayList bookmarks) {
    super();
    this.status = Status.RUNNING;

    this.activity = activity;
    this.task = task;
    this.annoMode = annoMode;
    this.curPos = curPos;
    this.startPos = startPos;
    this.endPos = endPos;
    this.curWidth = curWidth;
    this.splitLineIndex = splitLineIndex;
    this.lines = lines;
    this.curLine = curLine;
    this.pastedText = pastedText;
    this.textLen = textLen;
    this.openedFile = openedFile;
    this.testView = testView;
    this.inter = inter;
    this.formatting = formatting;
    this.bookmarks = bookmarks;
    this.foundBookmarks = new ArrayList<Bookmark>();
    curFilePos = -1;

    updateVars(activity);
  }

  @Override
  public Integer doInBackground(Void... unused) {
    try {

      switch (task) {
        case TASK_SPLIT:
          curPos = 0;
          bufText = "";
          bufLen = 0;
          notFound = 0;
          break;

        case TASK_ANNOTATE:
        case TASK_ANNOTATE_BACK:
          curPos = 0;
          bufText = "";
          bufLen = 0;
          curLine = new ArrayList<Object>();
          hMargin = testView != null ? testView.hMargin : 0;
          curWidth = hMargin;
          notFound = 0;
          break;
      }

      tempEndPos = endPos;
      tempStartPos = startPos;
      mHopelessBreak = false;

      while (
          (task == TASK_ANNOTATE || task == TASK_SPLIT) && (curPos < bufLen || curPos == bufLen && tempEndPos < textLen) && (tempLines.size() < visibleLines * 2 || (!formatting && tempEndPos - endPos < 500)) ||
              task == TASK_ANNOTATE_BACK && (curPos < bufLen || curPos == bufLen && tempStartPos > 0)) {
        if ((task == TASK_ANNOTATE || task == TASK_SPLIT) && bufLen - curPos < 18 && tempEndPos < textLen) {
          if (notFound > 0) {
            curLine = addNotFound(notFound, curLine);
            notFound = 0;
          }
          bufText = getNextBuffer();
          bufLen = bufText.length();
        } else if (task == TASK_ANNOTATE_BACK && curPos == bufLen) {
          if (notFound > 0) {
            curLine = addNotFound(notFound, curLine);
            notFound = 0;
          }
          if (curLine.size() > 0) {
            tempLines.add(curLine);
            curLine = new ArrayList<Object>();
          }
          tempBackLines.addAll(0, tempLines);
          tempLines.clear();
          if (tempBackLines.size() < visibleLines * 2) {
            bufText = getPrevBuffer();
            bufLen = bufText.length();
          } else {
            break;
          }
        }

        if (bufText.charAt(curPos) < '\u25CB' || bufText.charAt(curPos) > '\u9FA5') {
          if (checkCancelled()) return (null);

          notFound++;

          if (bufText.charAt(curPos) == ' ' && notFound > 1) {
            curPos++;
            curLine = addNotFound(notFound, curLine);
            notFound = 0;

            if (curFilePos != -1)
              curFilePos++;

            continue;
          }

          if (notFound > perLine * visibleLines * 2 && task == TASK_ANNOTATE) {
            notFound--;
            break;
          }

          if (curFilePos != -1)
            curFilePos += bufText.substring(curPos, curPos + 1).getBytes("UTF-8").length;
          curPos++;
          continue;
        }

        if (notFound > 0) {
          curLine = addNotFound(notFound, curLine);
          notFound = 0;
        }

        int last = -1;

        int i = 3;
        for (; i >= 0; i--) {
          int j = 1;
          for (; j <= i && curPos + j < bufLen; j++) {
            if (bufText.charAt(curPos + j) < '\u25CB' || bufText.charAt(curPos + j) > '\u9FA5') {
              break;
            }
          }

          if (j == i + 1) {
            if (i == 3) {
              last = Dict.binarySearch(bufText.substring(curPos, curPos + i + 1), true);
            } else {
              if (last >= 0) {
                last = Dict.binarySearch(bufText.substring(curPos, curPos + i + 1), 0, last - 1, false);
              } else {
                last = Dict.binarySearch(bufText.substring(curPos, curPos + i + 1), false);
              }
            }

            if (last >= 0) {
              if (i == 3) { //the found entry may be longer than 4 (3 + 1)
                if (Dict.getLength(last) > bufLen - curPos) { //the found may be longer than the ending
                  continue;
                }
                String word = bufText.substring(curPos, curPos + Dict.getLength(last));
                if (Dict.equals(last, word)) {
                  curLine = addWord(last, curLine);

                  Bookmark bookmark = Bookmark.search(curFilePos, bookmarks);
                  if (bookmark != null) {
                    bookmark.setAnnotatedPosition(tempLines.size(), curLine.size() - 1);
                    foundBookmarks.add(bookmark);
                  }

                  if (checkCancelled()) return (null);

                  if (curFilePos != -1)
                    curFilePos += word.length() * 3;
                  curPos += word.length();
                  break;
                }
              } else {
                curLine = addWord(last, curLine);

                Bookmark bookmark = Bookmark.search(curFilePos, bookmarks);
                if (bookmark != null) {
                  bookmark.setAnnotatedPosition(tempLines.size(), curLine.size() - 1);
                  foundBookmarks.add(bookmark);
                }

                if (checkCancelled()) return (null);

                if (curFilePos != -1)
                  curFilePos += (i + 1) * 3;
                curPos += i + 1;

                break;
              }
            }
          }
        }

        if (i < 0) {
          notFound++;
          if (curFilePos != -1)
            curFilePos += bufText.substring(curPos, curPos + 1).getBytes("UTF-8").length;
          curPos++;
        }
      }

      if (notFound > 0) {
        curLine = addNotFound(notFound, curLine);
        notFound = 0;
      }

      if (curLine.size() > 0)
       if (task == TASK_ANNOTATE_BACK || tempEndPos == textLen && curPos == bufLen || tempLines.size() == 0) { //back annotation or end of text or 1-line text
          tempLines.add(curLine);
          curLine = new ArrayList<Object>();
        } else {
          curPos -= LineView.getLineSize(curLine, false);
        }

      if (task == TASK_ANNOTATE || task == TASK_SPLIT) {
        if (annoMode == AnnotationActivity.ANNOTATE_FILE)
          tempEndPos -= bufText.substring(curPos).getBytes("UTF-8").length;
        else
          tempEndPos -= bufLen - curPos;
      }

      if (task == TASK_ANNOTATE_BACK) {
        tempBackLines.addAll(0, tempLines);
        tempLines = tempBackLines;
      }

      return (task);
    }  catch (Exception e) {
      status = Status.FINISHED;
      Log.e("pinyiner", "Annotation error " + e.getMessage());
    }

    return task;
  }

  @Override
  public void onProgressUpdate(Void... unused) {
  }

  @Override
  public void onPostExecute(Integer task) {
    status = Status.FINISHED;

    if (checkCancelled()) return;

    inter.onCompleted(task, splitLineIndex, pastedText, tempLines, curPos, tempStartPos, tempEndPos, curPos < bufLen || (annoMode == AnnotationActivity.ANNOTATE_FILE && tempEndPos + bufText.substring(0, curPos).getBytes().length < textLen), foundBookmarks);
  }

  public String getNextBuffer() {
    if (annoMode == AnnotationActivity.ANNOTATE_FILE) {
      byte[] buffer = new byte[1024];
      try {
        tempEndPos -= bufText.substring(curPos).getBytes("UTF-8").length;
        openedFile.seek(tempEndPos);
        int readCount = openedFile.read(buffer);

        int i = 0;
        while ((buffer[i] & 0x000000FF) >= 0x80 && (buffer[i] & 0x000000FF) < 0xC0 && i < readCount) {
          if (tempStartPos == tempEndPos + i) //make sure it's only adjusted for the starting condition
            tempStartPos++;
          i++;
        }

        if (tempStartPos == tempEndPos + 1 && tempStartPos > 0)
          if (buffer[i] == '\n') {
            tempStartPos++;
            i++;
          } else if (buffer[i] == '\r' && buffer[i + 1] == '\n') {
            tempStartPos += 2;
            i += 2;
          }

        if (tempEndPos + readCount < textLen) {
          while ((buffer[readCount - 1] & 0x000000FF) >= 0x80 && (buffer[readCount - 1] & 0x000000FF) < 0xC0)
            readCount--;
          if ((buffer[readCount - 1] & 0x000000FF) >= 0xC0)
            readCount--;
        }

        int bookmarkStart = Bookmark.searchClosest(tempEndPos, bookmarks);
        if (bookmarkStart != -1 && bookmarks.size() > bookmarkStart && bookmarks.get(bookmarkStart).mPosition < tempEndPos + readCount)
          curFilePos = tempEndPos;
        else
          curFilePos = -1;

        curPos = 0;
        tempEndPos += readCount;

        return new String(buffer, i, readCount - i, "UTF-8");
      } catch (Exception e) {
        Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
        return null;
      }
    } else { //ANNOTATE_BUFFER
      tempEndPos -= bufLen - curPos;
      long length = Math.min(pastedText.length() - tempEndPos, 300);
      curPos = 0;
      tempEndPos += length;
      return pastedText.substring((int)(tempEndPos - length), (int)tempEndPos);
    }
  }

  public String getPrevBuffer() {
    if (annoMode == AnnotationActivity.ANNOTATE_FILE) {
      byte[] buffer = new byte[1024];
      try {
        int readCount = 0, i = 0, j = 0;

        long toRead = Math.min(tempStartPos, buffer.length); //+2 - in case the character at startPos is broken
        openedFile.seek(tempStartPos - toRead);
        readCount = openedFile.read(buffer, 0, (int) toRead);
        while ((buffer[i] & 0x000000FF) >= 0x80 && (buffer[i] & 0x000000FF) < 0xC0 && i < readCount)
          i++;

        if (tempStartPos > readCount) { //not at the beginning of file
          j = i;
          while (buffer[j] != '\n' && j < readCount)
            j++;

          if (j < readCount - 1)
            i = j + 1;
        }

        String text = new String(buffer, i, readCount - i, "UTF-8");
        tempStartPos -= readCount - i;

        curPos = 0;

        int bookmarkStart = Bookmark.searchClosest(tempStartPos, bookmarks);
        if (bookmarkStart != -1 && bookmarks.size() > bookmarkStart && bookmarks.get(bookmarkStart).mPosition < tempEndPos)
          curFilePos = tempStartPos;
        else
          curFilePos = -1;

        return text;
      } catch (Exception e) {
        Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
        return null;
      }
    } else { //ANNOTATE_BUFFER
      int start = (int)Math.max(tempStartPos - 300, 0);
      int end = (int)tempStartPos;

      int j = start;
      if (start > 0) {
        while (pastedText.charAt(j) != '\n' && j < end)
          j++;

        if (j < end - 1)
          start = j + 1;
      }

      tempStartPos = start;
      return pastedText.substring(start, end);
    }
  }

  public String emptyString = new String("");

  public ArrayList<Object> addNotFound(int notFound, ArrayList<Object> curLine) {
    String str = bufText.substring(curPos - notFound, curPos);
    int nIndex = 0, oldIndex = 0;
    while ((nIndex = str.indexOf("\n", oldIndex)) > -1) {
      if (nIndex != oldIndex) {
        String words = str.substring(oldIndex, nIndex);
        curLine = addWord(words, curLine);
      }
      oldIndex = nIndex + 1;

      curLine.add(emptyString);
      tempLines.add(curLine);
      curLine = new ArrayList<Object>();

      curWidth = hMargin;
    }

    if (oldIndex < str.length()) {
      String words = str.substring(oldIndex);
      curLine = addWord(words, curLine);
    }

    return curLine;
  }

  public ArrayList<Object> checkPunctAdd(Object word, int tvWidth, ArrayList<Object> curLine) {
    final String stickyPrev = "!~)]}\"':;,.?\uFF0C\u3002\uFF01\uFF1F\u2026\uFF09\uFF1B\uFF1A\u2019\u201D\u300B\u3011\u300F\u3001";
    final String stickyNext = "([{<\u300A\u201C\u2018\u3010\u300E\uFF08";

    if (curLine.size() == 0 && tempLines.size() > 0) {
      ArrayList<Object> lastLine = tempLines.get(tempLines.size() - 1);

      int lastLineLen = lastLine.size();
      if (lastLineLen == 0 ) {
        curLine.add(word);
        curWidth = hMargin + tvWidth;
        return curLine;
      }

      Object last = lastLine.get(lastLineLen - 1);
      if (word instanceof String && ((String) word).length() > 0 && stickyPrev.indexOf(((String) word).charAt(0)) > -1 && !mHopelessBreak) {
        if (last instanceof String) {
          int lastIndex = ((String) last).length() - 1;
          char c;
          while (lastIndex >= 0 && ((c = ((String) last).charAt(lastIndex)) < 'a' || c > 'Z'))
            lastIndex--;
          while (lastIndex >= 0 && (c = ((String) last).charAt(lastIndex)) >= 'a' && c <= 'Z')
            lastIndex--;

          String strMove = ((String) last).substring(lastIndex + 1);
          String rest = ((String) last).substring(0, lastIndex + 1);
          if (lastLineLen > 1 || rest.length() > 0) { //make sure no empty line is left
            lastLine.remove(lastLine.size() - 1);
            curWidth = hMargin + testView.getWordWidth(strMove) + testView.getMargins();
            if (rest.length() > 0) {
              lastLine.add(rest);
              curLine.add(strMove);
            } else
              curLine = checkPunctAdd(strMove, testView.getWordWidth(strMove) + testView.getMargins(), curLine);
          } else
            mHopelessBreak = true;
        } else {
          lastLine.remove(lastLine.size() - 1);
          curLine = checkPunctAdd(last, testView.getWordWidth(last), curLine);
          curWidth = hMargin + testView.getWordWidth(last);
        }

        curLine = addWord(word, curLine);
        return curLine;
      } else if (last instanceof String && !mHopelessBreak) {
        String strLast = (String) last;
        int lastIndex = strLast.length() - 1;
        if (lastIndex >= 0 && stickyNext.contains(strLast.substring(lastIndex))) {
          lastIndex -= 1;
          while (lastIndex >= 0 && stickyNext.indexOf(strLast.charAt(lastIndex)) > -1)
            lastIndex--;

          String strMove = ((String) last).substring(lastIndex + 1);
          String rest = ((String) last).substring(0, lastIndex + 1);
          if (lastLineLen > 1 || rest.length() > 0) { //make sure no empty line is left
            lastLine.remove(lastLine.size() - 1);

            if (rest.length() > 0) {
              lastLine.add(rest);
            }

            curLine.add(strMove);
            curWidth = hMargin + testView.getWordWidth(strMove) + testView.getMargins();

            return addWord(word, curLine);
          } else {
            curLine.add(word);
            curWidth += testView.getWordWidth(word);
          }

          return curLine;
        }
      }
    }

    curLine.add(word);
    curWidth += tvWidth;

    mHopelessBreak = false;

    return  curLine;
  }

  public ArrayList<Object> addWord(Object word, ArrayList<Object> curLine) {
    int curLen = curLine.size();
    if (curLen > 0) {
      if (word instanceof String) {
        Object last = curLine.get(curLen - 1);
        if (last instanceof String) {
          int lastLen = ((String) last).length();
          if (lastLen > 0 && ((String) last).charAt(lastLen - 1) != ' ') {
            word = ((String) last).concat((String) word);
            curLine.remove(curLen - 1);

            if (formatting)
              curWidth -= testView.getWordWidth(last);
          }
        }
      }
    }

    if (!formatting) {
      curLine.add(word);
      return curLine;
    }

    while(true ) {
      int tvWidth = testView.getWordWidth(word);

      if (curWidth + tvWidth > screenWidth) {
        if (word instanceof String) {
          String str = (String)word;
          int sep = breakWord(str);

          ArrayList<Object> newCurLine = curLine;

          if (sep != 0) {
            newCurLine = checkPunctAdd(str.substring(0, sep), tvWidth, curLine);
            word = str.substring(sep);
          } else {
            if (curLine.size() == 0) {
              sep = breakWordHard(str);
              newCurLine = checkPunctAdd(str.substring(0, sep), tvWidth, curLine);
              word = str.substring(sep);
              mHopelessBreak = true;
            }

            if (curLine.size() == 1) {
              mHopelessBreak = true;
            }
          }

          if (newCurLine == curLine) { //if new lines have been added in the process, don't add a new line
            tempLines.add(curLine);
            curLine = new ArrayList<Object>();
            curWidth = hMargin;
          } else
            curLine = newCurLine;

        } else {
          if (curLine.size() > 0) {
            tempLines.add(curLine);
            curLine = new ArrayList<Object>();
          }
          curWidth = hMargin;
          curLine = checkPunctAdd(word, tvWidth, curLine);
          break;
        }
      } else {
        if (!(word instanceof String) || ((String) word).length() > 0)
          curLine = checkPunctAdd(word, tvWidth, curLine);
        break;
      }
    }

    return curLine;
  }

  public int breakWord(String str) {
    String[] strs = str.split("(?<=[\\p{Punct}\\s+\uFF0C\u3002\uFF01\uFF1F\u2026\uFF09\uFF1B\uFF1A\u2019\u201D\u300B\u3011\u300F\u3001\u300A\u201C\u2018\u3010\u300E\uFF08])");
    int width = curWidth + testView.getMargins(), i = 0, candLen = 0;

    while (width < screenWidth && i < strs.length) {
      candLen += strs[i].length();
      width = curWidth + testView.getMargins() + testView.getWordWidth(str.substring(0, candLen));
      i++;
    }

    if (width  <= screenWidth) {
      return candLen;
    } else if (i > 1) {
      return candLen - strs[i - 1].length();
    } else {
      return 0;
    }
  }

  public int breakWordHard(String str) {
    int width = curWidth + testView.getMargins(), i = 0;

    int lo = 0;
    int hi = str.length() - 1;
    int mid = 0, res = 0;
    while (lo < hi) {
      mid = (hi + lo) / 2;
      res = width + testView.getMargins() + testView.getWordWidth(str.substring(0, mid + 1));
      if      (res > screenWidth) hi = mid - 1;
      else if (res < screenWidth) lo = mid + 1;
      else return mid;
    }

    if (res <= screenWidth) {
      return mid + 1;
    } else {
      return Math.max(1, mid);
    }
  }

  public void redrawLines(RecyclerView listView) {
    int currentLine = Math.max(((LinearLayoutManager)listView.getLayoutManager()).findFirstVisibleItemPosition() - 1, 0);
    int linesSize = lines.size();

    for (int i = 0; i < currentLine && i < linesSize; i++) {
      ArrayList<Object> line = lines.get(i);
      startPos += LineView.getLineSize(line, annoMode == AnnotationActivity.ANNOTATE_FILE);
    }

    ArrayList<Object> curLine = new ArrayList<Object>();
    tempLines = new ArrayList<ArrayList<Object>>();
    curWidth = hMargin;
    int linesCount = lines.size();
    int i = 0;
    for (i = currentLine; i < linesCount && tempLines.size() < visibleLines * 2; i++) {
      ArrayList<Object> line = lines.get(i);
      int wordCount = line.size();
      for (int j = 0; j < wordCount; j++) {
        Object word = line.get(j);

        if (word instanceof String && ((String) word).length() == 0) {
          curLine.add("");
          tempLines.add(curLine);
          curLine = new ArrayList<Object>();
          curWidth = hMargin;
        } else {
          curLine = addWord(word, curLine);
        }
      }
    }

    if (curLine.size() > 0 && endPos >= textLen)
      tempLines.add(curLine);
    else
      endPos -= LineView.getLineSize(curLine, annoMode == AnnotationActivity.ANNOTATE_FILE);

    if (i < linesCount)
      for (; i < linesCount; i++)
        endPos -= LineView.getLineSize(lines.get(i), annoMode == AnnotationActivity.ANNOTATE_FILE);


    lines.clear();
    lines.addAll(tempLines);

    status = Status.FINISHED;
  }

  public static void updateVars(Context activity) {
    WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();

    if (formatting) {
      testView.updateVars();
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
        Point screen = new Point();
        display.getSize(screen);
        perLine = Math.max(Math.min(Math.round(screen.x / testView.getWordWidth("W")), Integer.MAX_VALUE / 2), 1);
        visibleLines = Math.max(Math.min(Math.round(screen.y / testView.getWordHeight()), (Integer.MAX_VALUE / perLine) / 2), 1);
        screenWidth = screen.x;
        screenHeight = screen.y;
      } else {
        perLine = Math.max(Math.min(Math.round(display.getWidth() / testView.getWordWidth("W")), Integer.MAX_VALUE / 2), 1);
        visibleLines = Math.max(Math.min(Math.round(display.getHeight() / testView.getWordHeight()), (Integer.MAX_VALUE / perLine) / 2), 1);
        screenWidth = display.getWidth();
        screenHeight = display.getHeight();
      }
    } else {
      visibleLines = 1;
      perLine = Integer.MAX_VALUE / 2;
    }
  }

  public boolean checkCancelled() {
    if (isCancelled()) {
      status = Status.FINISHED;
      return true;
    } else
      return false;
  }

  public void executeWrapper() {
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
      executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    else
      execute();
  }
}
