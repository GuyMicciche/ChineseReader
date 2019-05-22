package com.example.chinesereader;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.MotionEvent;

public class WordPopup {
  protected Context mContext;
  protected PopupWindow mWindow;
  protected View mRootView;
  public View parent;
  protected WindowManager mWindowManager;
  private ImageView mArrowUp;
  private ImageView mArrowDown;
  private TextView mText;
  private ScrollView mScroller;
  private Word word;
  protected Drawable mBackground = null;
  private Point screen = new Point();
  private SQLiteDatabase dictDb;

  public WordPopup(Context context, SQLiteDatabase dictDb) {
    mContext = context;
    this.dictDb = dictDb;
    mWindow = new PopupWindow(context);

    mWindow.setTouchInterceptor(new OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
          mWindow.dismiss();

          return false;
        }

        return false;
      }
    });

    mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

    LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    mRootView = (ViewGroup) inflater.inflate(R.layout.popup, null);
    mScroller = (ScrollView) mRootView.findViewById(R.id.scroller);
    mArrowDown = (ImageView) mRootView.findViewById(R.id.arrow_down);
    mArrowUp = (ImageView) mRootView.findViewById(R.id.arrow_up);
    mText = (TextView) mRootView.findViewById(R.id.content);
    mRootView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    display.getSize(screen);
    mText.setMaxHeight(screen.y - 20);
    mText.setMaxWidth(screen.x - 40);

    mWindow.setBackgroundDrawable(new BitmapDrawable());
    mWindow.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
    mWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
    mWindow.setTouchable(true);
    mWindow.setFocusable(false);
    mWindow.setOutsideTouchable(false);
    mWindow.setContentView(mRootView);
  }

  public void setText(String text) {
    mText.setText(text);
    //mText.invalidate();
  }

  public void show(View anchor) {
    this.dismiss();

    String query = "SELECT py,eng,weight FROM dic WHERE chs=? ORDER BY weight DESC";
    Cursor cur = dictDb.rawQuery(query, new String[]{
        ((Word)anchor.getTag()).ch
    });
    cur.moveToFirst();
    if (!cur.isAfterLast()) {
      StringBuilder allMean = new StringBuilder();
      allMean.append("• " + cur.getString(1).replace("/", "\n• "));
      cur.moveToNext();

      while (!cur.isAfterLast()) {
        allMean.append("\n\n[ " + pinyinToTones(cur.getString(0)) + " ]\n• " + cur.getString(1).replace("/", "\n• "));
        cur.moveToNext();
      }

      mText.setText(allMean.toString());
    }


    this.parent = anchor;
    anchor.setBackgroundColor(0xFF9ECEFF);

    int xPos = 0, yPos = 0, arrowPos;

    int[] location = new int[2];

    anchor.getLocationOnScreen(location);

    Rect anchorRect = new Rect(location[0], location[1], location[0] + anchor.getWidth(), location[1]
        + anchor.getHeight());

    mWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, 0, 0);
    mScroller.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

    int rootHeight = mScroller.getMeasuredHeight();
    int rootWidth = mScroller.getMeasuredWidth();

    xPos = anchorRect.centerX() - (rootWidth / 2);

    if ((xPos + rootWidth) > screen.x) {
      xPos = screen.x - rootWidth;
    } else if (xPos < 0) {
      xPos = 0;
    }

    arrowPos = anchorRect.centerX() - xPos;

    int dyTop = anchorRect.top;
    int dyBottom = screen.y - anchorRect.bottom;

    boolean onTop = (dyBottom < rootHeight && dyTop > dyBottom) ? true : false;

    if (onTop) {
      if (rootHeight + 28 > dyTop - 115) {
        yPos = 115;
        rootHeight = anchorRect.top - yPos - 28;
      } else {
        yPos = anchorRect.top - rootHeight - 28;
      }
    } else {
      yPos = anchorRect.bottom;

      if (rootHeight + 28 > dyBottom) {
        rootHeight = dyBottom - 28;
      }
    }

    showArrow(((onTop) ? R.id.arrow_down : R.id.arrow_up), arrowPos, rootWidth);
    //mWindow.showAsDropDown(anchor, );
    mWindow.update(xPos, yPos, rootWidth, rootHeight + 28);
  }

  private void showArrow(int whichArrow, int requestedX, int rootWidth) {
    final View showArrow = (whichArrow == R.id.arrow_up) ? mArrowUp : mArrowDown;
    final View hideArrow = (whichArrow == R.id.arrow_up) ? mArrowDown : mArrowUp;

    showArrow.setVisibility(View.VISIBLE);

    final int arrowWidth = showArrow.getMeasuredWidth();

    ViewGroup.MarginLayoutParams param = (ViewGroup.MarginLayoutParams) showArrow.getLayoutParams();

    if (requestedX > rootWidth - 17 - arrowWidth / 2) {
      requestedX = rootWidth - 17 - arrowWidth / 2;
    } else if (requestedX < 17 + arrowWidth / 2) {
      requestedX = 17 + arrowWidth / 2;
    }

    param.leftMargin = requestedX - arrowWidth / 2;

    hideArrow.setVisibility(View.INVISIBLE);
    showArrow.invalidate();
    hideArrow.invalidate();
  }

  public void dismiss() {
    if (this.parent != null) {
      this.parent.setBackgroundColor(Color.TRANSPARENT);
    }
    mWindow.dismiss();
  }

  public boolean isShowing() {
    return mWindow.isShowing();
  }

  public static String pinyinToTones(String py) {
    String[] parts = py.split(" ");
    int len = parts.length;
    StringBuilder pinyin = new StringBuilder();
    for (int j = 0; j < len; j++) {
      pinyin.append(WordPopup.convertToneNumber2ToneMark(parts[j]));
    }

    return pinyin.toString();
  }

  private static String convertToneNumber2ToneMark(final String pinyinStr) {
    String lowerCasePinyinStr = pinyinStr.toLowerCase();

    if (lowerCasePinyinStr.matches("[a-z]*[1-5]?")) {
      final char defaultCharValue = '$';
      final int defaultIndexValue = -1;

      char unmarkedVowel = defaultCharValue;
      int indexOfUnmarkedVowel = defaultIndexValue;

      final char charA = 'a';
      final char charE = 'e';
      final String ouStr = "ou";
      final String allUnmarkedVowelStr = "aeiouv";
      final String allMarkedVowelStr = "āáăàaēéĕèeīíĭìiōóŏòoūúŭùuǖǘǚǜü";

      if (lowerCasePinyinStr.matches("[a-z]*[1-5]")) {

        int tuneNumber = Character.getNumericValue(lowerCasePinyinStr.charAt(lowerCasePinyinStr.length() - 1));

        int indexOfA = lowerCasePinyinStr.indexOf(charA);
        int indexOfE = lowerCasePinyinStr.indexOf(charE);
        int ouIndex = lowerCasePinyinStr.indexOf(ouStr);

        if (-1 != indexOfA) {
          indexOfUnmarkedVowel = indexOfA;
          unmarkedVowel = charA;
        } else if (-1 != indexOfE) {
          indexOfUnmarkedVowel = indexOfE;
          unmarkedVowel = charE;
        } else if (-1 != ouIndex) {
          indexOfUnmarkedVowel = ouIndex;
          unmarkedVowel = ouStr.charAt(0);
        } else {
          for (int i = lowerCasePinyinStr.length() - 1; i >= 0; i--) {
            if (String.valueOf(lowerCasePinyinStr.charAt(i)).matches("["
                + allUnmarkedVowelStr + "]")) {
              indexOfUnmarkedVowel = i;
              unmarkedVowel = lowerCasePinyinStr.charAt(i);
              break;
            }
          }
        }

        if ((defaultCharValue != unmarkedVowel)
            && (defaultIndexValue != indexOfUnmarkedVowel)) {
          int rowIndex = allUnmarkedVowelStr.indexOf(unmarkedVowel);
          int columnIndex = tuneNumber - 1;

          int vowelLocation = rowIndex * 5 + columnIndex;

          char markedVowel = allMarkedVowelStr.charAt(vowelLocation);

          StringBuffer resultBuffer = new StringBuffer();

          resultBuffer.append(lowerCasePinyinStr.substring(0, indexOfUnmarkedVowel).replaceAll("v", "ü"));
          resultBuffer.append(markedVowel);
          resultBuffer.append(lowerCasePinyinStr.substring(indexOfUnmarkedVowel + 1, lowerCasePinyinStr.length() - 1).replaceAll("v", "ü"));

          return resultBuffer.toString();

        } else
        // error happens in the procedure of locating vowel
        {
          return lowerCasePinyinStr;
        }
      } else
      // input string has no any tune number
      {
        // only replace v with ü (umlat) character
        return lowerCasePinyinStr.replaceAll("v", "ü");
      }
    } else
    // bad format
    {
      return lowerCasePinyinStr;
    }
  }
}
