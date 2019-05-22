package com.astratech.chinesereader;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;

public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
  private boolean changed = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);
    PreferenceManager.setDefaultValues(this, R.xml.preferences,
        false);
    for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
      initSummary(getPreferenceScreen().getPreference(i));
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Set up a listener whenever a key changes
    getPreferenceScreen().getSharedPreferences()
        .registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  protected void onPause() {
    super.onPause();
    // Unregister the listener whenever a key changes
    getPreferenceScreen().getSharedPreferences()
        .unregisterOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    changed = true;
    updatePrefSummary(findPreference(key));
  }

  private void initSummary(Preference p) {
    if (p instanceof PreferenceCategory) {
      PreferenceCategory pCat = (PreferenceCategory) p;
      for (int i = 0; i < pCat.getPreferenceCount(); i++) {
        initSummary(pCat.getPreference(i));
      }
    } else {
      updatePrefSummary(p);
    }
  }

  private void updatePrefSummary(Preference p) {
    if (p instanceof ListPreference) {
      ListPreference listPref = (ListPreference) p;
      p.setSummary(listPref.getEntry());
    }
    if (p instanceof EditTextPreference) {
      EditTextPreference editTextPref = (EditTextPreference) p;
      p.setSummary(editTextPref.getText());
    }
    if (p instanceof SeekBarPreference) {
      SeekBarPreference seekBarPref = (SeekBarPreference) p;
      p.setSummary(Integer.toString(seekBarPref.getProgress()) + "%");
    }
  }
  @Override
  public void onBackPressed() {
    Intent resultIntent = new Intent();
    setResult(AnnotationActivity.RESULT_SETTINGS_CHANGED, resultIntent);
    finish();
  }
}