package com.chemecador.signpdf.ui.view

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.chemecador.signpdf.R
import com.chemecador.signpdf.databinding.FragmentShowPdfBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException

@AndroidEntryPoint
class ShowPDFFragment : Fragment() {

    private var _binding: FragmentShowPdfBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShowPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val filePath = arguments?.getString(ARG_FILE_PATH)
        if (filePath == null) {
            Toast.makeText(requireContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }
        showPdf(filePath)

        binding.btnCancel.setOnClickListener {
            binding.drawingView.clearDrawing()
        }
    }

    private fun showPdf(filePath: String) {
        val file = File(filePath)
        try {
            val fileDescriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)

            if (pdfRenderer.pageCount > 0) {
                val page = pdfRenderer.openPage(0)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                binding.ivPdf.setImageBitmap(bitmap)
            }
            pdfRenderer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_FILE_PATH = "file_path"
    }
}
