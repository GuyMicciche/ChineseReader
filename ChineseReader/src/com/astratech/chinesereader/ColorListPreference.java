package com.astratech.chinesereader;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;

public class ColorListPreference extends ListPreference {
  private Context mContext;

  public ColorListPreference(Context context, AttributeSet attrs) {
    super(context, attrs);

    mContext = context;
  }

  @Override
  protected void onPrepareDialogBuilder(final AlertDialog.Builder builder) {
    if (builder == null) {
      throw new NullPointerException("Builder is null");
    }
    CharSequence[] entries = getEntries();
    CharSequence[] entryValues = getEntryValues();

    if (entries == null || entryValues == null || entries.length != entryValues.length) {
      throw new IllegalStateException("Invalid entries array or entryValues array");
    }

    ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getContext(), android.R.layout.select_dialog_singlechoice, entries) {
      @Override
      public View getView(int position, View row, ViewGroup parent) {
        View view = super.getView(position, row, parent);

        CheckedTextView checkedTextView = (CheckedTextView)view.findViewById(android.R.id.text1);
        checkedTextView.setTextColor((int)Long.parseLong(ColorListPreference.this.getEntryValues()[position].toString(), 16));

        return view;
      }
    };
    builder.setAdapter(adapter, this);

    super.onPrepareDialogBuilder(builder);
  }
}