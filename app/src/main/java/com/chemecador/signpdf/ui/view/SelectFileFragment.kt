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
import androidx.navigation.fragment.findNavController
import com.chemecador.signpdf.R
import com.chemecador.signpdf.databinding.FragmentSelectFileBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

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
        val file = getFileFromUri(uri)
        navigateToShowPDFFragment(file.absolutePath)
    }
    private fun getFileFromUri(uri: Uri): File {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val file = File(requireContext().cacheDir, "selected.pdf")
        file.outputStream().use { outputStream ->
            inputStream?.copyTo(outputStream)
        }
        return file
    }

    private fun navigateToShowPDFFragment(filePath: String) {
        val bundle = Bundle().apply {
            putString(ShowPDFFragment.ARG_FILE_PATH, filePath)
        }

        findNavController().navigate(R.id.action_selectFileFragment_to_showPDFFragment, bundle)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

