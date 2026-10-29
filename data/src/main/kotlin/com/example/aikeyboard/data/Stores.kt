package com.example.aikeyboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// One DataStore instance per file for the whole process.
internal val Context.keyStore: DataStore<Preferences> by preferencesDataStore("api_keys")
internal val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")
internal val Context.modelStore: DataStore<Preferences> by preferencesDataStore("model_cache")
internal val Context.metricsStore: DataStore<Preferences> by preferencesDataStore("metrics")
