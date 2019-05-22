package com.astratech.chinesereader;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.Console;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by lastant on 1/28/14.
 */
public class LineView extends View {
  public ArrayList<Object> line;
  public List<ArrayList<Object>> lines;
  public Paint pinyinPaint, charPaint, hlPaint;
  public Rect pinyinBounds = new Rect(), charBounds = new Rect(), subCharBounds = new Rect();
  public String pinyinType, wordDist;
  public int textSizeInt, pinyinSizeInt;
  public int vMargin, hMargin;
  public float lastX;
  public ArrayList<String> top;
  public ArrayList<String> bottom;
  public ArrayList<Integer> tones;
  public ArrayList<Bookmark> bookmarks;
  public float scale, space;
  public boolean lastLine = false;
  public static SharedPreferences sharedPrefs;
  public Typeface charTypeface, charTypefaceNoBold;
  public int[] toneColors;
  public Point hlIndex;

  public LineView(Context context) {
    super(context);

    this.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
          lastX = event.getX();
        }
        return false;
      }
    });
    
    if (sharedPrefs == null) {
      sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    scale = context.getResources().getDisplayMetrics().density;
    init();
    updateVars();
    bookmarks = new ArrayList<>();
  }

  public void init() {
    pinyinPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
    charPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
    hlPaint = new Paint();
    pinyinPaint.setColor((int)Long.parseLong(sharedPrefs.getString("pref_pinyinColor", "FF0582FF"), 16)); //3D9EFF
    charPaint.setColor(Color.BLACK);
    hlPaint.setColor(0xFFC4E1FF); //9ECEFF);
    pinyinPaint.setStyle(Paint.Style.FILL);
    charPaint.setStyle(Paint.Style.FILL);
  }

  public void updateVars() {
    pinyinType = sharedPrefs.getString("pref_pinyinType", "marks");
    textSizeInt = sharedPrefs.getInt("pref_textSizeInt", 16);
    pinyinSizeInt = sharedPrefs.getInt("pref_pinyinSizeInt", 100);
    wordDist = sharedPrefs.getString("pref_wordDist", "dynamic");
    pinyinPaint.setColor((int)Long.parseLong(sharedPrefs.getString("pref_pinyinColor", "FF024D93"), 16));
    String fontName = sharedPrefs.getString("pref_charFont", "default");
    charTypeface = FontCache.get(fontName, getContext());
    charTypefaceNoBold = fontName.startsWith("b_") ? FontCache.get(fontName.substring(2), getContext()) : charTypeface;
    charPaint.setTypeface(charTypeface);

    toneColors = new int[5];
    String toneColorsPrefs = sharedPrefs.getString("pref_toneColors", "none");
    if (!toneColorsPrefs.equals("none"))
      for (int i = 0; i < 5; i++) {
        toneColors[i] = (int) Long.parseLong(toneColorsPrefs.substring(i * 9, i * 9 + 8), 16);
      }
    else
      toneColors = null;

    charPaint.setTextSize(textSizeInt * scale);// + 0.5f);
    float pinyinSize = textSizeInt * pinyinSizeInt / 100;
    if (Math.round(pinyinSize) == 0)
      pinyinType = "none";
    else
      pinyinPaint.setTextSize(pinyinSize * scale);

    hMargin = Math.round(Math.max(textSizeInt, pinyinSize) * scale / 5);
    vMargin = Math.round(Math.max(textSizeInt, pinyinSize) * scale / 4);

    if ("none".equals(wordDist))
      hMargin = 1;

    requestLayout();
  }

  @Override
  public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    if (pinyinType.equals("none")) {
      setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.round(vMargin + charPaint.getTextSize()));
    } else {
      setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.round(pinyinPaint.getTextSize() + vMargin + charPaint.getTextSize()));
    }
  }

  @Override
  public void onDraw(Canvas canvas) {
    float curX = hMargin, totalWidth = 0;
    int count = line.size();
    int lineNum = lines.indexOf(line);
    space = 0;

    if ("dynamic".equals(wordDist) && !lastLine) {
      for (int i = 0; i < count; i++) {
        Object word = line.get(i);
        float maxWidth;

        if (word instanceof String) {
          if (!((String) word).matches("[^\\w]*"))
            charPaint.setTypeface(Typeface.DEFAULT);
          else
            charPaint.setTypeface(charTypefaceNoBold);

          charPaint.getTextBounds((String) word, 0, ((String) word).length(), charBounds);
          totalWidth += charPaint.measureText((String) word);
        } else {
          charPaint.setTypeface(charTypeface);

          String key = bottom.get(i);
          if (pinyinType.equals("none")) {
            charPaint.getTextBounds(key, 0, key.length(), charBounds);

            maxWidth = charBounds.width();
          } else {
            String pinyin = top.get(i);
            pinyinPaint.getTextBounds(pinyin, 0, pinyin.length(), pinyinBounds);
            charPaint.getTextBounds(key, 0, key.length(), charBounds);

            maxWidth = Math.max(pinyinBounds.width(), charBounds.width());
          }

          totalWidth += maxWidth + hMargin * 2;
        }
      }

      space = (getWidth() - 2 * hMargin - totalWidth) / (count - 1);
    }


    for (int i = 0; i < count; i++){
      Object word = line.get(i);
      float maxWidth;

      if (word instanceof String) {
        if (!((String) word).matches("[^\\w]*"))
          charPaint.setTypeface(Typeface.DEFAULT);
        else
          charPaint.setTypeface(charTypefaceNoBold);

        charPaint.setColor(Color.BLACK);
        charPaint.getTextBounds((String)word, 0, ((String)word).length(), charBounds);
        maxWidth = charPaint.measureText((String)word);
        if (maxWidth > 0) {
          if (pinyinType.equals("none")) {
            canvas.drawText(bottom.get(i), curX - (charBounds.left < 0 ? charBounds.left : 0), Math.round(charPaint.getTextSize()), charPaint);
          } else {
            canvas.drawText(bottom.get(i), curX - (charBounds.left < 0 ? charBounds.left : 0), Math.round(pinyinPaint.getTextSize() + charPaint.getTextSize()), charPaint);
          }
        }

        curX += maxWidth + space;
      } else {
        charPaint.setTypeface(charTypeface);

        String key = bottom.get(i);
        if (pinyinType.equals("none")) {
          charPaint.getTextBounds(key, 0, key.length(), charBounds);

          maxWidth = charBounds.width();

          if (hlIndex.y == lineNum && hlIndex.x == i) {
            canvas.drawRect(curX, 0, curX + maxWidth + hMargin * 2, charPaint.getTextSize() + vMargin, hlPaint);
          }

          if (bookmarks.size() > 0 && bookmarks.get(i) != null)
            drawBookmark(canvas, bookmarks.get(i), curX);

          if (toneColors != null) { //means tone colors are selected
            int charTones = tones.get(i);
            int chars = key.length();
            int subCharX = 0;
            for (int c = 0; c < chars; c++) {
              int tone = charTones % 10;
              charTones /= 10;
              charPaint.setColor(toneColors[tone - 1]);

              canvas.drawText(key, c, c + 1, curX + subCharX + hMargin - charBounds.left, charPaint.getTextSize(), charPaint);

              charPaint.getTextBounds(key, c, c + 1, subCharBounds);
              subCharX += subCharBounds.width() + subCharBounds.left;
            }
          } else {
            charPaint.setColor(Color.BLACK);
            canvas.drawText(key, curX + hMargin - charBounds.left, charPaint.getTextSize(), charPaint);
          }
        } else {
          String pinyin = top.get(i);
          pinyinPaint.getTextBounds(pinyin, 0, pinyin.length(), pinyinBounds);
          charPaint.getTextBounds(key, 0, key.length(), charBounds);

          maxWidth = Math.max(pinyinBounds.width(), charBounds.width());

          if (hlIndex.y == lineNum && hlIndex.x == i) {
            canvas.drawRect(curX, 0, curX + maxWidth + hMargin * 2, pinyinPaint.getTextSize() + vMargin + charPaint.getTextSize(), hlPaint);
          }

          if (bookmarks.size() > 0 && bookmarks.get(i) != null)
            drawBookmark(canvas, bookmarks.get(i), curX);

          canvas.drawText(pinyin, curX + hMargin - pinyinBounds.left + (maxWidth - pinyinBounds.width()) / 2, pinyinPaint.getTextSize(), pinyinPaint);

          if (toneColors != null) { //means tone colors are selected
            int charTones = tones.get(i);
            int chars = key.length();
            int subCharX = 0;
            for (int c = 0; c < chars; c++) {
              int tone = charTones % 10;
              charTones /= 10;
              charPaint.setColor(toneColors[tone - 1]);

              canvas.drawText(key, c, c + 1, curX + subCharX + hMargin - charBounds.left + (maxWidth - charBounds.width()) / 2, pinyinPaint.getTextSize() + charPaint.getTextSize(), charPaint);

              charPaint.getTextBounds(key, c, c + 1, subCharBounds);
              subCharX += subCharBounds.width() + subCharBounds.left;
            }
          } else {
            charPaint.setColor(Color.BLACK);
            canvas.drawText(key, curX + hMargin - charBounds.left + (maxWidth - charBounds.width()) / 2, pinyinPaint.getTextSize() + charPaint.getTextSize(), charPaint);
          }
        }

        curX += maxWidth + hMargin * 2 + space;
      }

    }
  }

  public void drawBookmark(Canvas canvas, Bookmark itsBookmark, float curX) {
    int hlColor = hlPaint.getColor();
    hlPaint.setColor(0xffff0000);
    //hlPaint.setAntiAlias(true);

    Path path = new Path();
    path.setFillType(Path.FillType.EVEN_ODD);
    path.moveTo(curX, 0);
    path.lineTo(curX + 12 * scale, 0);
    path.lineTo(curX, 12 * scale);
    path.close();

    canvas.drawPath(path, hlPaint);

    hlPaint.setColor(hlColor);
  }

  public int getWordWidth(Object word) {
    if (pinyinPaint == null) {
      init();
    }

    if (word instanceof String) {
      if (!((String) word).matches("[^\\w]*"))
        charPaint.setTypeface(Typeface.DEFAULT);
      else
        charPaint.setTypeface(charTypeface);

      float wordWidth = charPaint.measureText((String)word);
      return Math.round(wordWidth);
    } else {
      charPaint.setTypeface(charTypeface);

      int entry = (Integer)word;
      if (pinyinType.equals("none")) {
        String key = Dict.getCh(entry);
        charPaint.getTextBounds(key, 0, key.length(), charBounds);

        return charBounds.width() + hMargin * 2;
      } else {
        String pinyin = Dict.pinyinToTones(Dict.getPinyin(entry)), key = Dict.getCh(entry) ;
        pinyinPaint.getTextBounds(pinyin, 0, pinyin.length(), pinyinBounds);
        charPaint.getTextBounds(key, 0, key.length(), charBounds);

        return Math.max(pinyinBounds.width(), charBounds.width()) + hMargin * 2;
      }
    }
  }

  public int getWordWidth(String str) {
    return Math.round(charPaint.measureText(str));
  }

  public void getTouchedWord(int[] touchedData) {
    int curX = hMargin, count = line.size();
    touchedData[0] = -1;

    for (int i = 0; i < count; i++) {
      int width = getWordWidth(line.get(i));
      if (curX <= lastX && curX + width >= lastX) {
        touchedData[0] = i;
        touchedData[1] = curX + width / 2;
        return;
      }
      curX += width + space;
    }
  }

  public float getWordHeight() {
    if (pinyinType.equals("none")) {
      return charPaint.getTextSize() + hMargin;
    } else {
      return pinyinPaint.getTextSize() + hMargin * 2 + charPaint.getTextSize();
    }
  }

  public int getMargins ()  {
    return hMargin * 2;
  }

  public static long getLineSize(ArrayList<Object> line, boolean isFile) {
    long res = 0;

    try {
      int length = line.size();
      for (int j = 0; j < length; j++) {
        Object word = line.get(j);
        if (word instanceof String)
          if (((String)word).length() == 0)
            res += 1;
          else
            res += isFile ? ((String) word).getBytes("UTF-8").length : ((String) word).length();
        else
          res += isFile ? Dict.getLength((Integer) word) * 3 : Dict.getLength((Integer) word);
      }
    }  catch (Exception e) {}

    return res;
  }
}
