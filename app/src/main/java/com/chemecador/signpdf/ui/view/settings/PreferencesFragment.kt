package com.chemecador.signpdf.ui.view.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.chemecador.signpdf.R
import com.chemecador.signpdf.data.datastore.PreferencesKeys
import com.chemecador.signpdf.ui.viewmodel.ViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PreferencesFragment : PreferenceFragmentCompat() {

    private val viewModel: ViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val switchPreference = findPreference<SwitchPreferenceCompat>(PreferencesKeys.SIGN_ALL.name)
        switchPreference?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setSignAllPagesEnabled(newValue as Boolean)
            true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isSignAllPagesEnabled.collect { isEnabled ->
                    switchPreference?.isChecked = isEnabled
                }
            }
        }
    }
}
