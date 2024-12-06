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
    private var pdfRenderer: PdfRenderer? = null
    private var currentPageIndex: Int = 0
    private var totalPages: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShowPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initUI()
        setupListeners()
    }

    private fun initUI() {
        val filePath = arguments?.getString(ARG_FILE_PATH)
        if (filePath == null) {
            Toast.makeText(requireContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }
        openPdf(filePath)
    }

    private fun setupListeners() {
        binding.btnPrevPage.setOnClickListener {
            goToPreviousPage()
        }
        binding.btnNextPage.setOnClickListener {
            goToNextPage()
        }

        binding.btnCancel.setOnClickListener {
            binding.drawingView.clearDrawing()
        }
    }

    private fun openPdf(filePath: String) {
        val file = File(filePath)
        try {
            val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)

            totalPages = pdfRenderer?.pageCount ?: 0
            currentPageIndex = 0

            if (totalPages > 0) {
                showPage(currentPageIndex)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    private fun showPage(pageIndex: Int) {
        val page = pdfRenderer?.openPage(pageIndex)
        val bitmap = Bitmap.createBitmap(page?.width ?: 0, page?.height ?: 0, Bitmap.Config.ARGB_8888)
        page?.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page?.close()

        binding.ivPdf.setImageBitmap(bitmap)
        binding.tvPageInfo.text = getString(R.string.page_info, pageIndex + 1, totalPages)
    }

    private fun goToPreviousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--
            showPage(currentPageIndex)
        }
    }

    private fun goToNextPage() {
        if (currentPageIndex < totalPages - 1) {
            currentPageIndex++
            showPage(currentPageIndex)
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
