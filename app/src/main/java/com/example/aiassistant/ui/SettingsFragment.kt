package com.example.aiassistant.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.example.aiassistant.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
