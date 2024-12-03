package com.chemecador.signpdf.ui.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.chemecador.signpdf.R
import com.chemecador.signpdf.databinding.FragmentSelectFileBinding

class SelectFileFragment : Fragment() {

    private var _binding: FragmentSelectFileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectFileBinding.inflate(inflater, container, false)
        binding.btnSelectFile.setOnClickListener {
            Toast.makeText(requireContext(), R.string.file_selected, Toast.LENGTH_SHORT).show()
        }
        return binding.root

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}