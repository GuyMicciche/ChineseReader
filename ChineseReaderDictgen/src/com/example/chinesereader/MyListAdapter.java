package com.example.chinesereader;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Color;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MyListAdapter extends ArrayAdapter<ArrayList<Word>> {
  private Context context;
  private WordPopup wPopup;

  public MyListAdapter(Context context, int textViewResourceId, List<ArrayList<Word>> items, WordPopup popup) {
    super(context, textViewResourceId, items);
    this.context = context;
    wPopup = popup;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    View rowView = convertView;
    if (rowView == null) {
      LinearLayout row = new LinearLayout(this.context);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setLayoutParams(new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

      ArrayList<TextView> views = new ArrayList<TextView>();
      row.setTag(views);
      rowView = row;
    }

    ArrayList<Word> line = getItem(position);

    ArrayList<TextView> tvs = (ArrayList<TextView>) rowView.getTag();
    int tvsLength = tvs.size();
    int lineLength = line.size();

    for (int i = 0; i < Math.max(tvsLength, lineLength); i++) {
      if (i >= tvsLength) {
        TextView tv = new TextView(this.context);
        tv.setBackgroundColor(Color.TRANSPARENT);
        tv.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        tv.setGravity(Gravity.CENTER_HORIZONTAL);
        tv.setPadding(5, 0, 5, 0);
        tv.setTextSize(16.0f);
        tv.setFocusable(true);
        tv.setClickable(true);
        tv.setOnClickListener(new OnClickListener() {
          @Override
          public void onClick(View v) {
            Word w = (Word) v.getTag();
            if (w == null || wPopup.isShowing() && wPopup.parent != null && wPopup.parent.equals(v)) {
              wPopup.dismiss();
            } else {
              wPopup.show(v);
            }
          }
        });
        tvs.add(tv);
        ((LinearLayout)rowView).addView(tv);
      }

      if (i >= tvs.size()) {
        return null;
      }
      TextView tv = tvs.get(i);
      if (i < lineLength) {
        Word word = line.get(i);
        tv.setText(Html.fromHtml("<font color='red'>" + WordPopup.pinyinToTones(word.pinyin) + "</font><br/>" + word.ch), TextView.BufferType.SPANNABLE);
        if (word.pinyin.length() > 0) {
          tv.setTag(word);
        } else {
          tv.setTag(null);
        }
      } else {
        //tvs.remove(i);
        //((LinearLayout)rowView).removeViewAt(i);
        tv.setText("");
        tv.setTag(null);
      }
    }

    return rowView;
  }
}
