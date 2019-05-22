package com.astratech.chinesereader;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.MotionEvent;
import android.widget.Toast;

import java.util.ArrayList;

public class WordPopup {
  protected AnnotationActivity mContext;
  protected PopupWindow mWindow;
  protected View mRootView;
  public View parent;
  protected WindowManager mWindowManager;
  private ImageView mArrowUp;
  private ImageView mArrowDown;
  private TextView mChars, mContent, mBookmark;
  private LinearLayout mBubble;
  public int screenX, screenY, showX;
  public float scale;
  public ArrayList<Object> line;
  public int wordIndex, entry;
  private ScrollView mScroll;
  Button splitButton, bookmarkButton;
  public ArrayList<Integer> history;

  public WordPopup(final AnnotationActivity activity) {
    mContext = activity;
    mWindow = new PopupWindow(mContext);
    if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
      mWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
    mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    history = new ArrayList<Integer>();

    LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    mRootView = inflater.inflate(R.layout.popup, null);
    mBubble = (LinearLayout) mRootView.findViewById(R.id.bubble);
    mScroll = (ScrollView)mRootView.findViewById(R.id.scroller);
    mArrowDown = (ImageView) mRootView.findViewById(R.id.arrow_down);
    mArrowUp = (ImageView) mRootView.findViewById(R.id.arrow_up);
    mChars = (TextView) mRootView.findViewById(R.id.charsText);
    mChars.setLinksClickable(true);
    mChars.setMovementMethod(LinkMovementMethod.getInstance());
    mContent = (TextView) mRootView.findViewById(R.id.content);
    mContent.setLinksClickable(true);
    mContent.setMovementMethod(LinkMovementMethod.getInstance());
    mBookmark = (TextView) mRootView.findViewById(R.id.bookmarkTitle);

    this.configure(mContext);

    mWindow.setBackgroundDrawable(new BitmapDrawable());
    mWindow.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
    mWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
    mWindow.setTouchable(true);
    mWindow.setFocusable(false);
    mWindow.setOutsideTouchable(false);
    mWindow.setContentView(mRootView);

    OnTouchListener highlight = new OnTouchListener() {
      public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN: {
            ((Button)v).setTextColor(0xe0f47521);
            v.getBackground().setColorFilter(0xe0f47521, PorterDuff.Mode.SRC_ATOP);
            v.invalidate();
            break;
          }
          case MotionEvent.ACTION_UP: {
            ((Button)v).setTextColor(0x99333333);
            v.getBackground().clearColorFilter();
            v.invalidate();
            break;
          }
        }
        return false;
      }
    };

    Button copyButton = (Button) mRootView.findViewById(R.id.charsCopy);
    copyButton.setOnTouchListener(highlight);
    copyButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        String ch = Dict.getCh(entry);

        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
          android.text.ClipboardManager clipboard = (android.text.ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
          clipboard.setText(ch);
        } else {
          android.content.ClipboardManager clipboard = (android.content.ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
          android.content.ClipData clip = android.content.ClipData.newPlainText("Chinese", ch);
          clipboard.setPrimaryClip(clip);
        }

        AnnotationActivity.sharedPrefs.edit().putString("lastText", ch).commit();
        Toast.makeText(mContext, "Copied to clipboard", Toast.LENGTH_SHORT).show();
      }
    });

    splitButton = (Button) mRootView.findViewById(R.id.button_split);
    splitButton.setOnTouchListener(highlight);
    splitButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        dismiss();

        ArrayList<Object> subWords = breakWord(Dict.getCh(entry));
        int lineIndex = mContext.lines.indexOf(line);

        mContext.linesAdapter.notifyDataSetChanged();

        Object firstWord = subWords.get(0);
        if (mContext.annoMode == AnnotationActivity.ANNOTATE_FILE) {
          mContext.curPos = 0;
          mContext.endPos = activity.getPosition(mContext.lines, lineIndex + 1, wordIndex, true) + (firstWord instanceof Integer ? Dict.getLength((int)firstWord) : ((String)firstWord).length()) * 3;
        }
        else
          mContext.endPos = (int)activity.getPosition(mContext.lines, lineIndex + 1, wordIndex, false) + (firstWord instanceof Integer ? Dict.getLength((int)firstWord) : ((String)firstWord).length());

        ArrayList<Object> curLine = (ArrayList<Object>)line.clone();
        int toRemove = curLine.size() - wordIndex;
        while (toRemove-- > 0)
          curLine.remove(wordIndex);
        curLine.add(subWords.get(0));

        int curWidth = 5;
        for (Object word : curLine)
          curWidth += activity.testView.getWordWidth(word);

        activity.splitAnnotation(lineIndex, curWidth, curLine);
      }
    });

    final Button starButton = (Button) mRootView.findViewById(R.id.button_star);
    starButton.setOnTouchListener(highlight);
    starButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        String stars = AnnotationActivity.sharedPrefs.getString("stars", "");
        String ch = Dict.getCh(entry, "simplified");
        int index = stars.startsWith(ch + "\n") ? 0 : stars.indexOf("\n" + ch + "\n");
        if (index > -1) {
          AnnotationActivity.sharedPrefs.edit().putString("stars", stars.substring(0, index) + stars.substring(index + ch.length() + 1)).commit();
          Toast.makeText(mContext, "Unstarred", Toast.LENGTH_SHORT).show();
          toggleStar(starButton, false);
        } else {
          if (stars.endsWith("    ")) //Sometimes after closing the app, empty spaces are added to prefs
            stars = stars.substring(0, stars.length() - 4);
          AnnotationActivity.sharedPrefs.edit().putString("stars", stars + ch + "\n").commit();
          Toast.makeText(mContext, "Starred", Toast.LENGTH_SHORT).show();
          toggleStar(starButton, true);
        }
      }
    });

    bookmarkButton = (Button) mRootView.findViewById(R.id.button_bookmark);
    bookmarkButton.setOnTouchListener(highlight);
    bookmarkButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        int lineNum = mContext.lines.indexOf(line);
        long bookmarkPos = mContext.getPosition(mContext.lines, lineNum + 1, wordIndex, true);
        int bookmarksPos = Bookmark.searchClosest(bookmarkPos, mContext.mBookmarks);

        if (mContext.mBookmarks.size() > bookmarksPos && mContext.mBookmarks.get(bookmarksPos).mPosition == bookmarkPos) {
          mContext.mBookmarks.remove(bookmarksPos);
          if (!Bookmark.saveToFile(mContext.mBookmarks, mContext.curFilePath + ".bookmarks"))
            Toast.makeText(mContext, "Bookmarks could not be stored. File location is not writable.", Toast.LENGTH_LONG).show();

          int foundBookmarksPos = Bookmark.searchClosest(bookmarkPos, mContext.mFoundBookmarks);
          if (mContext.mFoundBookmarks.size() > foundBookmarksPos && mContext.mFoundBookmarks.get(foundBookmarksPos).mPosition == bookmarkPos)
            mContext.mFoundBookmarks.remove(foundBookmarksPos);

          mContext.linesAdapter.notifyItemChanged(lineNum + 1);
          show(mContext.linesLayoutManager.findViewByPosition(lineNum + 1), line, wordIndex, showX, false);
        } else {
          AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
          builder.setTitle("New bookmark");
          final EditText inputBookmark = new EditText(mContext);
          inputBookmark.setInputType(EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
          inputBookmark.setText(Dict.pinyinToTones(Dict.getPinyin(entry)));
          inputBookmark.selectAll();
          builder.setView(inputBookmark);
          builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              try {
                int lineNum = mContext.lines.indexOf(line);
                long bookmarkPos = mContext.getPosition(mContext.lines, lineNum + 1, wordIndex, true);
                int bookmarksPos = Bookmark.searchClosest(bookmarkPos, mContext.mBookmarks);
                int foundBookmarksPos = Bookmark.searchClosest(bookmarkPos, mContext.mFoundBookmarks);

                Bookmark newBookmark = new Bookmark(bookmarkPos, inputBookmark.getText().toString());
                newBookmark.setAnnotatedPosition(lineNum, wordIndex);
                mContext.mFoundBookmarks.add(foundBookmarksPos, newBookmark);
                mContext.mBookmarks.add(bookmarksPos, newBookmark);

                if (!Bookmark.saveToFile(mContext.mBookmarks, mContext.curFilePath + ".bookmarks"))
                  Toast.makeText(mContext, "Bookmarks could not be stored. File location is not writable.", Toast.LENGTH_LONG).show();

                mContext.linesAdapter.notifyItemChanged(lineNum + 1);
                show(mContext.linesLayoutManager.findViewByPosition(lineNum + 1), line, wordIndex, showX, false);
              } catch(NumberFormatException nfe) {
                Toast.makeText(mContext, "", Toast.LENGTH_LONG).show();
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
        }
      }
    });

    final Button shareButton = (Button) mRootView.findViewById(R.id.button_share);
    shareButton.setOnTouchListener(highlight);
    shareButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        String ch = Dict.getCh(entry);
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, ch);
        sendIntent.setType("text/plain");
        mContext.startActivity(sendIntent);
      }
    });

    LinearLayout popupButtons = (LinearLayout)mRootView.findViewById(R.id.popupButtons);
    Dictionaries.DictInfo[] dicts = Dictionaries.getDictList();
    PackageManager pm = mContext.getPackageManager();
    for (Dictionaries.DictInfo dict : dicts) {
      boolean installed = Dictionaries.isPackageInstalled(pm, dict.packageName);
      if (installed) {
        Button dictBtn = new Button(mContext);
        dictBtn.setText(dict.id);
        dictBtn.setTextSize(20);
        dictBtn.setTextColor(0x99333333);
        dictBtn.setTag(dict);
        dictBtn.setPadding((int)(10 * scale), (int)(2 * scale), 0, (int)(2 * scale));
        dictBtn.setBackgroundColor(Color.TRANSPARENT);
        dictBtn.setMinimumWidth(0);
        dictBtn.setMinWidth(0);
        dictBtn.setMinimumHeight(0);
        dictBtn.setMinHeight(0);
        dictBtn.setSingleLine(true);
        dictBtn.setOnTouchListener(highlight);
        popupButtons.addView(dictBtn);
        dictBtn.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            Dictionaries.DictInfo dict = (Dictionaries.DictInfo)view.getTag();
            try {
              Dictionaries.findInDictionary(mContext, dict, Dict.getCh(entry, "simplified"));
            } catch (Exception e) { Toast.makeText(mContext, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
          }
        });
      }
    }
  }

  public void toggleStar(View starButton, boolean set) {
    Drawable starBg;

    if (starButton == null)
      starButton = mRootView.findViewById(R.id.button_star);

    if (set)
      starBg = mContext.getResources().getDrawable(R.drawable.star_on);
    else
      starBg = mContext.getResources().getDrawable(R.drawable.star);

    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN ) {
      starButton.setBackgroundDrawable(starBg);
    } else {
      starButton.setBackground(starBg);
    }
  }

  public void toggleBookmark(View bookmarkButton, boolean set) {
    Drawable bookmarkBg;

    if (bookmarkButton == null)
      bookmarkButton = mRootView.findViewById(R.id.button_bookmark);

    if (set)
      bookmarkBg = mContext.getResources().getDrawable(R.drawable.bookmark_on);
    else
      bookmarkBg = mContext.getResources().getDrawable(R.drawable.bookmark);

    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN ) {
      bookmarkButton.setBackgroundDrawable(bookmarkBg);
    } else {
      bookmarkButton.setBackground(bookmarkBg);
    }
  }

  public void configure(Context context) {
    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    scale = context.getResources().getDisplayMetrics().density;

    if(Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB){
      Point screen = new Point();
      display.getSize(screen);
      screenX = screen.x;
      screenY = screen.y;
    } else {
      screenX = display.getWidth();
      screenY = display.getHeight();
    }

    mChars.setMaxWidth((int)(screenX - 16 * scale));
    mContent.setMaxHeight((int)(screenY - 5 * scale));
    mContent.setMaxWidth((int)(screenX - 16 * scale));
    mBookmark.setMaxWidth((int)(screenX - 45 * scale));
  }

  public static ArrayList<Object> breakWord(String theText) {
    int textLen = theText.length(), curPos = 0, last;
    ArrayList<Object> words = new ArrayList<Object>();

    while (curPos < textLen) {
      int i = Math.min(textLen - curPos - 1, 3);

      if (curPos == 0) {
        i = Math.min(textLen - curPos - 2, 3);
      }

      last = -1;
      for (; i >= 0; i--) {
        if (i == 3 && curPos > 0) {
          last = Dict.binarySearch(theText.substring(curPos, curPos + i + 1), true);
        } else {
          if (last >= 0) {
            last = Dict.binarySearch(theText.substring(curPos, curPos + i + 1), 0, last - 1, false);
          } else {
            last = Dict.binarySearch(theText.substring(curPos, curPos + i + 1), false);
          }
        }

        if (last >= 0) {
          if (i == 3) { //the found entry may be longer than 4 (3 + 1)
            if (Dict.getLength(last) > textLen - curPos) { //the found may be longer than the ending
              continue;
            }
            String word = theText.substring(curPos, curPos + Dict.getLength(last));
            if (Dict.equals(last, word)) {
              words.add(last);
              curPos += word.length();
              break;
            }
          } else {
            words.add(last);
            curPos += i + 1;
            break;
          }
        }
      }

      if (i == -1 && last < 0) {
        words.add(theText.substring(curPos, curPos + 1));
        curPos++;
      }
    }

    return words;
  }

  class WordSpan extends ClickableSpan {
    public String link;

    public void onClick(View view) {
      int wordNum = Dict.binarySearch(link, false);
      if (wordNum != -1) {
        if (history.size() > 0)
          history.add(entry);
        else
          history.add(wordIndex);
        show(parent, null, wordNum, showX, false);
      }
      else
        Toast.makeText(mContext, "The word is not in the dictionary", Toast.LENGTH_LONG).show();
    }
  }

  class CopySpan extends ClickableSpan {
    public String link;

    public void onClick(View view) {
      if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
        android.text.ClipboardManager clipboard = (android.text.ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setText(link);
      } else {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Chinese", link);
        clipboard.setPrimaryClip(clip);
      }

      AnnotationActivity.sharedPrefs.edit().putString("lastText", link).commit();
      Toast.makeText(mContext, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }
  }

  public void show(View anchor, ArrayList<Object> line, int wordNum, int showX, boolean redraw) {
    try {
      this.showX = showX;

      if (redraw)
        this.dismiss();

      if (line != null) {
        this.line = line;
        this.wordIndex = wordNum;
        this.entry = (Integer) line.get(wordNum);
        splitButton.setVisibility(View.VISIBLE);
        bookmarkButton.setVisibility(View.VISIBLE);
      } else {
        this.entry = wordNum;
        splitButton.setVisibility(View.GONE);
        bookmarkButton.setVisibility(View.GONE);
      }

      String sTrad = Dict.getCh(entry, "traditional");
      String sSimp = Dict.getCh(entry, "simplified");

      SpannableStringBuilder text = new SpannableStringBuilder();

      if (sSimp.length() > 1) {
        ArrayList<Object> subWords = breakWord(sSimp);
        for (Object word : subWords) {
          if (word instanceof Integer) { //was found
            String ch = Dict.getCh((int)word, "simplified");
            text.append(ch).append(" ");
            WordSpan clickable = new WordSpan();
            clickable.link = ch;
            text.setSpan(clickable, text.length() - ch.length() - 1, text.length() - 1, Spanned.SPAN_USER);
          } else {
            text.append((String)word).append(" ");
          }
        }
      } else {
        text.append(sSimp).append(" ");
        text.setSpan(new AbsoluteSizeSpan(24, true), text.length() - sSimp.length() - 1, text.length() - 1, Spanned.SPAN_USER);
        splitButton.setVisibility(View.GONE);
      }

      if (!sTrad.equals(sSimp)) {
        text.append(sTrad);
      }

      mChars.setText(text);

      text.clear();
      String pinyin = Dict.pinyinToTones(Dict.getPinyin(entry));
      text.append("[ ").append(pinyin).append(" ]  ");

      appendInlineButtons(text, pinyin);

      text.append("\n");

      String[] parts = Dict.getEnglish(entry).replace("/", "\n• ").split("\\$");

      int i = 0;
      for (String s : parts) {
        if (i++ % 2 == 1) {
          pinyin = Dict.pinyinToTones(s);
          text.append("\n\n[ ").append(pinyin).append(" ]  ");
          appendInlineButtons(text, pinyin);
          text.append("\n");
        } else {
          text.append("• ");

          int beforeAppended = text.length();

          int bracketIndex, bracketEnd = 0;
          while ((bracketIndex = s.indexOf("[", bracketEnd)) > -1) {
            text.append(s, bracketEnd, bracketIndex);
            bracketEnd = s.indexOf("]", bracketIndex);
            text.append(Dict.pinyinToTones(s.substring(bracketIndex, bracketEnd)));
          }

          text.append(s, bracketEnd, s.length());

          int afterAppended = text.length();

          for (int m = beforeAppended; m < afterAppended; m++) {
            if (text.charAt(m) >= '\u25CB' && text.charAt(m) <= '\u9FA5') {
              int n = m + 1;
              while (n < text.length() && text.charAt(n) >= '\u25CB' && text.charAt(n) <= '\u9FA5')
                n++;

              WordSpan clickable = new WordSpan();
              clickable.link = text.subSequence(m, n).toString();
              text.setSpan(clickable, m, n, Spanned.SPAN_USER);
            }
          }
        }
      }

      mContent.setText(text);

      final LinearLayout bookmarkTitleLayout = (LinearLayout) mRootView.findViewById(R.id.bookmark);
      if (mContext.annoMode != AnnotationActivity.ANNOTATE_FILE) {
        bookmarkButton.setVisibility(View.GONE);
        bookmarkTitleLayout.setVisibility(View.GONE);
      } else {
        int lineNum = mContext.lines.indexOf(line);
        long bookmarkPos = mContext.getPosition(mContext.lines, lineNum + 1, wordIndex, true);
        int bookmarksPos = Bookmark.searchClosest(bookmarkPos, mContext.mBookmarks);
        boolean isBookmarked = mContext.mBookmarks.size() > bookmarksPos && mContext.mBookmarks.get(bookmarksPos).mPosition == bookmarkPos;

        toggleBookmark(bookmarkButton, isBookmarked);

        if (isBookmarked) {
          bookmarkTitleLayout.setVisibility(View.VISIBLE);
          ((TextView) mRootView.findViewById(R.id.bookmarkTitle)).setText(mContext.mBookmarks.get(bookmarksPos).mTitle);
        } else
          bookmarkTitleLayout.setVisibility(View.GONE);
      }

      toggleStar(null, AnnotationActivity.sharedPrefs.getString("stars", "").startsWith(Dict.getCh(entry, "simplified") + "\n") ||
          AnnotationActivity.sharedPrefs.getString("stars", "").contains("\n" + Dict.getCh(entry, "simplified") + "\n"));

      this.parent = anchor;

      float xPos = 0, yPos = 0, arrowPos;

      int[] location = new int[2];

      anchor.getLocationOnScreen(location);

      Rect anchorRect = new Rect(location[0], location[1], location[0] + anchor.getWidth(), location[1]
          + anchor.getHeight());

      if (redraw)
        mWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, 0, 0);
      mBubble.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

      float rootHeight = mBubble.getMeasuredHeight();
      float rootWidth = mBubble.getMeasuredWidth();

      xPos = showX - (rootWidth / 2);

      if ((xPos + rootWidth) > screenX) {
        xPos = screenX - rootWidth;
      } else if (xPos < 0) {
        xPos = 0;
      }

      arrowPos = showX - xPos;

      float dyTop = anchorRect.top - 60 * scale;
      float dyBottom = screenY - anchorRect.bottom - 20 * scale;

      boolean onTop = dyBottom < rootHeight && dyTop > dyBottom;

      if (onTop) {
        if (rootHeight + 20 * scale > dyTop) {
          yPos = 60 * scale;
          rootHeight = anchorRect.top - yPos - 20 * scale;
        } else {
          yPos = anchorRect.top - rootHeight - 20 * scale;
        }
      } else {
        yPos = anchorRect.bottom;

        if (rootHeight > dyBottom) {
          rootHeight = dyBottom;
        }
      }

      showArrow((onTop ? R.id.arrow_down : R.id.arrow_up), arrowPos, rootWidth);

      mWindow.update(Math.round(xPos), Math.round(yPos), Math.round(rootWidth), Math.round(rootHeight + 21 * scale));
      mScroll.fullScroll(View.FOCUS_UP);
    } catch (Exception e) {
      Toast.makeText(mContext, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  private void appendInlineButtons(SpannableStringBuilder text, String target) {
    CopySpan copySpan = new CopySpan();
    copySpan.link = target;
    text.append(" ");
    Drawable copyImage = ContextCompat.getDrawable(mContext, R.drawable.ic_menu_copy_holo_light);
    copyImage.setBounds(0, 0, (int)(mContent.getLineHeight() * 1.2), (int)(mContent.getLineHeight() * 1.2));
    text.setSpan(new ImageSpan(copyImage), text.length() - 2, text.length() - 1, 0);
    text.setSpan(copySpan, text.length() - 2, text.length() - 1, Spanned.SPAN_USER);
  }

  private void showArrow(int whichArrow, float requestedX, float rootWidth) {
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

    param.leftMargin = Math.round(requestedX - arrowWidth / 2);

    hideArrow.setVisibility(View.INVISIBLE);
    showArrow.invalidate();
    hideArrow.invalidate();
  }

  public void dismiss() {
    int oldHlIndex = mContext.hlIndex.y;
    mContext.hlIndex.y = -1;

    if (mWindow.isShowing()) {
      if (oldHlIndex != -1)
        mContext.linesLayoutManager.findViewByPosition(oldHlIndex + 1).invalidate();
      history.clear();
      mWindow.dismiss();
    }
  }

  public boolean isShowing() {
    return mWindow.isShowing();
  }
}
