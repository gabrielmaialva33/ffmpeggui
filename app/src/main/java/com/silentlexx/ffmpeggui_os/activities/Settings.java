package com.silentlexx.ffmpeggui_os.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.MenuItem;
import android.widget.ListView;

import com.silentlexx.ffmpeggui_os.R;

public class Settings extends PreferenceActivity
implements SharedPreferences.OnSharedPreferenceChangeListener {

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getPreferenceManager().setSharedPreferencesName(
           Gui.SHARED_PREFS_NAME);
    addPreferencesFromResource(R.xml.sets);
    setTitle(R.string.settings);
    if (getActionBar() != null) {
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }
    protectPreferenceListFromActionBar();
    getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(
            this);
    Preference aiSettings = findPreference("ai_settings");
    if (aiSettings != null) {
        aiSettings.setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(Settings.this, AiSettings.class));
            return true;
        });
    }
    /*
    CheckBoxPreference pref = (CheckBoxPreference)findPreference("forcelegacy");
    if(Config.isNewSDK()){
        pref.setChecked(false);
        pref.setEnabled(false);
    } else if(Config.isLegacy()){
        pref.setChecked(true);
        pref.setEnabled(false);
    }
    */

  // 	pref.setEnabled(new Root().isDeviceRooted());
}

private void protectPreferenceListFromActionBar() {
    final ListView list = getListView();
    if (list == null) {
        return;
    }
    list.post(() -> {
        if (isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
            return;
        }
        int actionBarHeight = getActionBar() == null ? 0 : getActionBar().getHeight();
        if (actionBarHeight <= 0 || list.getPaddingTop() >= actionBarHeight) {
            return;
        }
        list.setPadding(list.getPaddingLeft(), actionBarHeight,
                list.getPaddingRight(), list.getPaddingBottom());
    });
}

@Override
public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
        finish();
        return true;
    }
    return super.onOptionsItemSelected(item);
}

@Override
protected void onResume() {
    super.onResume();
}

@Override
protected void onDestroy() {
    getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
            this);
    super.onDestroy();
}

public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
        String key) {
   // new Config(this);
}
}
