package com.chemecador.signpdf.ui.view

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.chemecador.signpdf.databinding.FragmentSelectFileBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SelectFileFragment : Fragment() {

    private var _binding: FragmentSelectFileBinding? = null
    private val binding get() = _binding!!

    private val getFileResultLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handleSelectedFile(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectFileBinding.inflate(inflater, container, false)

        binding.btnSelectFile.setOnClickListener {
            openFileSelector()
        }

        return binding.root
    }

    private fun openFileSelector() {
        getFileResultLauncher.launch("*/*")
    }

    private fun handleSelectedFile(uri: Uri) {
        val fileName = getFileName(uri)
        Toast.makeText(requireContext(), "Selected file: $fileName", Toast.LENGTH_SHORT).show()
    }

    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex != -1) {
                    fileName = it.getString(columnIndex)
                }
            }
        }
        return fileName
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

