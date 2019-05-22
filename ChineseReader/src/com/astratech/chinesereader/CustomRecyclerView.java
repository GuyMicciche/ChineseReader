package com.astratech.chinesereader;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.preference.PreferenceManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * Created by lastant on 7/31/2014.
 */
public class CustomRecyclerView extends RecyclerView {
  public float progress = 0, length = 0, bannerHeight = 0;
  private Paint progressPaint;
  private ScaleGestureDetector mScaleDetector;
  private Context mContext;
  public AnnotationActivity mMainActivity;
  private float density;

  public CustomRecyclerView(Context context) {
    super(context);
  }
  public CustomRecyclerView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    mContext = context;
    density = context.getResources().getDisplayMetrics().density;
    progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
    progressPaint.setStrokeWidth(2 * density);
    progressPaint.setARGB(255, 0, 183, 235);
    progressPaint.setTextSize(17 * density);
    progressPaint.setFakeBoldText(true);

    mScaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.OnScaleGestureListener() {
      private int initTextSize;
      private SharedPreferences sharedPrefs;
      @Override
      public void onScaleEnd(ScaleGestureDetector detector) {
      }
      @Override
      public boolean onScaleBegin(ScaleGestureDetector detector) {
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        initTextSize = sharedPrefs.getInt("pref_textSizeInt", 16);
        return true;
      }
      @Override
      public boolean onScale(ScaleGestureDetector detector) {
        int textSizeInt = Math.max(10, Math.round(initTextSize * detector.getScaleFactor()));
        sharedPrefs.edit().putInt("pref_textSizeInt", textSizeInt).commit();
        mMainActivity.parentSettingsChanged = true;
        mMainActivity.redraw();
        mMainActivity.wPopup.dismiss();
        return false;
      }
    });
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    mScaleDetector.onTouchEvent(event);
    return super.onTouchEvent(event);
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    if (progress != -1) {
      Rect visibilityRect = new Rect();
      getLocalVisibleRect(visibilityRect);
      float x = canvas.getWidth() - 2 * mMainActivity.testView.scale;
      int maxHeight = (int) (visibilityRect.height() - bannerHeight - 2 * density);
      length = maxHeight * progress;

      progressPaint.setAlpha(255);
      canvas.drawLine(x, 1 * mMainActivity.testView.scale, x, length, progressPaint);

      progressPaint.setAlpha(80);
      String percent = String.format("%d", (int) (progress * 100)) + "%";
      float percentWidth = progressPaint.measureText(percent);
      canvas.drawText(percent, x - percentWidth - 2 * density, maxHeight, progressPaint);
    }

    super.dispatchDraw(canvas);
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event)
  {
    if (event.getAction() == MotionEvent.ACTION_DOWN && findChildViewUnder(event.getX(), event.getY()) == null) {
      mMainActivity.wPopup.dismiss();
    }
    return super.dispatchTouchEvent(event);
  }
}
