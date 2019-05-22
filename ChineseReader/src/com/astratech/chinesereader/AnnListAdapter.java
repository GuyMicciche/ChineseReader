package com.astratech.chinesereader;

import java.util.ArrayList;
import java.util.List;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

public class AnnListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  private final int VIEW_ITEM = 1;
  private final int VIEW_HEADER = 0;
  private final int VIEW_FOOTER = 2;

  private AnnotationActivity context;
  private WordPopup wPopup;
  private List<ArrayList<Object>> mLines;

  public boolean showHeader = false;
  public boolean showFooter = false;

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public LineView mLineView;
    public ViewHolder(LineView v) {
      super(v);
      mLineView = v;
    }
  }

  public AnnListAdapter(AnnotationActivity context, List<ArrayList<Object>> items, RecyclerView recyclerView, WordPopup popup) {
    this.context = context;
    wPopup = popup;
    mLines = items;

    final LinearLayoutManager linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();

  }

  @Override
  public int getItemViewType(int position) {
    if (position == 0)
      return VIEW_HEADER;
    else if (position == getItemCount() - 1)
      return VIEW_FOOTER;
    else return VIEW_ITEM;
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

    RecyclerView.ViewHolder vh = null;

    if (viewType == VIEW_ITEM) {
      LineView v = new LineView(this.context);

      vh = new LineViewHolder(v);
    } else if (viewType == VIEW_HEADER) {
      View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.listheader, parent, false);

      vh = new ProgressViewHolder(v);
    } else if (viewType == VIEW_FOOTER) {
      View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.listfooter, parent, false);

      vh = new ProgressViewHolder(v);
    }
    return vh;
  }

  @Override
  public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {

    if (holder instanceof LineViewHolder) {
      ArrayList<Object> line = mLines.get(position - 1);

      LineView curView = ((LineViewHolder)holder).mLineView;
      curView.line = line;
      curView.lines = mLines;
      curView.hlIndex = context.hlIndex;
      curView.bookmarks.clear();

      if (context.mFoundBookmarks.size() > 0)
        for (int i = 0; i < line.size(); i++) {
          Bookmark itsBookmark = Bookmark.searchAnnotated(position - 1, i, context.mFoundBookmarks);
          if (itsBookmark != null)
            curView.bookmarks.add(itsBookmark);
          else
            curView.bookmarks.add(null);
        }

      if (curView.top == null) {
        curView.top = new ArrayList<String>();
      } else {
        curView.top.clear();
      }
      if (curView.bottom == null) {
        curView.bottom = new ArrayList<String>();
      } else {
        curView.bottom.clear();
      }
      if (curView.tones == null) {
        curView.tones = new ArrayList<Integer>();
      } else {
        curView.tones.clear();
      }

      int count = line.size();
      for (int i = 0; i < count; i++){
        Object word = line.get(i);

        if (word instanceof String) {
          curView.bottom.add((String)word);
          curView.top.add("");
          curView.tones.add(0);
        } else {
          int entry = (Integer)word;
          String key = Dict.getCh(entry);
          curView.bottom.add(key);
          if (AnnotationActivity.sharedPrefs.getString("pref_pinyinType", "marks").equals("none")) {
            curView.top.add("");
          } else {
            curView.top.add(Dict.pinyinToTones(Dict.getPinyin(entry)));
          }

          if (AnnotationActivity.sharedPrefs.getString("pref_toneColors", "none").equals("none")) {
            curView.tones.add(0);
          } else {
            int tones = Integer.parseInt(Dict.getPinyin(entry).replaceAll("[\\D]", ""));
            int reverseTones = 0;
            while (tones != 0) {
              reverseTones = reverseTones * 10 + tones % 10;
              tones = tones / 10;
            }
            curView.tones.add(reverseTones);
          }
        }
      }

      if (count == 0 || line.get(count - 1) instanceof String && ((String)line.get(count - 1)).length() == 0 ||
          context.endPos >= context.textLen && position - 1 == getItemCount() - 3)
        curView.lastLine = true;
      else
        curView.lastLine = false;

      curView.updateVars();

    } else {
      if (position == 0 && showHeader || position == mLines.size() + 1 && showFooter)
        ((ProgressViewHolder) holder).progressBar.setVisibility(View.VISIBLE);
      else
        ((ProgressViewHolder) holder).progressBar.setVisibility(View.GONE);
    }

  }

  @Override
  public int getItemCount() {
    return mLines.size() + 2;
  }

  public class LineViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    public LineView mLineView;

    public LineViewHolder(View v) {
      super(v);
      mLineView = (LineView) v;
      mLineView.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
      LineView lv = (LineView)view;
      int[] touchedData = new int[2];
      lv.getTouchedWord(touchedData);
      int lineNum = context.lines.indexOf(lv.line);

      if (touchedData[0] == -1 || lv.line.get(touchedData[0]) instanceof String || wPopup.isShowing() && context.hlIndex.y == lineNum && context.hlIndex.x == touchedData[0]) {
        wPopup.dismiss();
      } else {
        wPopup.show(view, lv.line, touchedData[0], touchedData[1], true);
        context.hlIndex.y = lineNum;
        context.hlIndex.x = touchedData[0];
        lv.invalidate();
      }
    };
  }

  public class ProgressViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    public ProgressBar progressBar;

    public ProgressViewHolder(View v) {
      super(v);
      progressBar = (ProgressBar) v.findViewById(R.id.progressBar);
      v.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
      wPopup.dismiss();
    }
  }
}
