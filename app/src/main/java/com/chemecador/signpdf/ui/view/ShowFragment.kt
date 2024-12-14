package com.chemecador.signpdf.ui.view

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.chemecador.signpdf.R
import com.chemecador.signpdf.databinding.FragmentShowBinding
import com.chemecador.signpdf.ui.view.SignFragment.Companion.ARG_URI_PATH
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException

@AndroidEntryPoint
class ShowFragment : Fragment() {

    private var _binding: FragmentShowBinding? = null
    private val binding get() = _binding!!
    private lateinit var pdfRenderer: PdfRenderer
    private var currentPageIndex: Int = 0
    private var totalPages: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        initUI()
    }

    private fun initUI() {
        val pdfUriString = arguments?.getString(ARG_URI_PATH)
        if (pdfUriString == null) {
            Toast.makeText(requireContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        val pdfUri = Uri.parse(pdfUriString) // Convertimos el String a Uri
        openPdf(pdfUri)
    }

    private fun setupListeners() {
        binding.btnPrevPage.visibility = INVISIBLE
        binding.btnPrevPage.setOnClickListener { navigateToPage(false) }
        binding.btnNextPage.setOnClickListener { navigateToPage(true) }
        binding.btnSignAnother.setOnClickListener {
            findNavController().navigate(R.id.action_showFragment_to_selectFileFragment)
        }
        binding.btnSignAgain.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.btnShare.setOnClickListener {
            val pdfUri = Uri.parse(arguments?.getString(ARG_URI_PATH) ?: return@setOnClickListener)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
        }

    }

    private fun openPdf(pdfUri: Uri) {
        try {
            val fileDescriptor = requireContext().contentResolver.openFileDescriptor(pdfUri, "r")
            if (fileDescriptor != null) {
                pdfRenderer = PdfRenderer(fileDescriptor)
                totalPages = pdfRenderer.pageCount
                currentPageIndex = 0
                if (totalPages > 0) {
                    showPage(currentPageIndex)
                }
            } else {
                Toast.makeText(requireContext(), R.string.error_file, Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(requireContext(), R.string.error_file, Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }


    private fun showPage(pageIndex: Int) {
        val page = pdfRenderer.openPage(pageIndex)
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        binding.pdfView.setImageBitmap(bitmap)

        binding.tvPageInfo.text = getString(R.string.page_info, pageIndex + 1, totalPages)
        binding.btnPrevPage.visibility = if (pageIndex == 0) INVISIBLE else VISIBLE
        binding.btnNextPage.visibility = if (pageIndex == totalPages - 1) INVISIBLE else VISIBLE
    }

    private fun navigateToPage(goToNextPage: Boolean) {
        if (goToNextPage && currentPageIndex < totalPages - 1) {
            currentPageIndex++
            showPage(currentPageIndex)
        } else if (!goToNextPage && currentPageIndex > 0) {
            currentPageIndex--
            showPage(currentPageIndex)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        if (::pdfRenderer.isInitialized) {
            pdfRenderer.close()
        }
    }
}
