package com.chemecador.signpdf.ui.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.chemecador.signpdf.R
import com.chemecador.signpdf.databinding.FragmentShowPdfBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.io.File
import java.io.IOException

@AndroidEntryPoint
class ShowPDFFragment : Fragment() {

    private var _binding: FragmentShowPdfBinding? = null
    private val binding get() = _binding!!
    private lateinit var pdfRenderer: PdfRenderer
    private var currentPageIndex: Int = 0
    private var totalPages: Int = 0
    private val pageSignatures = mutableMapOf<Int, Bitmap?>()
    private val createFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
            uri?.let { savePdfToUri(it) }
        }


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
        setupMenu()
        val filePath = arguments?.getString(ARG_FILE_PATH)
        if (filePath == null) {
            Toast.makeText(requireContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }
        openPdf(filePath)
    }

    private fun setupMenu() {
        val activity = requireActivity() as AppCompatActivity
        activity.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().navigateUp()
                        true
                    }

                    R.id.action_settings -> {
                        findNavController().navigate(R.id.action_showPDFFragment_to_settingsFragment)
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner)

        activity.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
        }
        activity.title = ""
    }

    private fun setupListeners() {
        binding.btnPrevPage.visibility = INVISIBLE
        binding.btnPrevPage.setOnClickListener { navigateToPage(false) }
        binding.btnNextPage.setOnClickListener { navigateToPage(true) }
        binding.btnCancel.setOnClickListener { binding.drawingView.clearDrawing() }
        binding.btnFinish.setOnClickListener {
            if (!binding.drawingView.isEmpty()) {
                val bitmap = Bitmap.createBitmap(
                    binding.drawingView.width,
                    binding.drawingView.height,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                binding.drawingView.draw(canvas)
                pageSignatures[currentPageIndex] = bitmap
                binding.drawingView.clearDrawing()
            }

            val originalFilePath = requireArguments().getString(ARG_FILE_PATH)!!
            val originalFileName = File(originalFilePath).nameWithoutExtension
            val defaultFileName = "${originalFileName}_signed.pdf"

            showSaveDialog(defaultFileName) { newFileName ->
                createFileLauncher.launch(newFileName)
            }
        }

    }

    private fun navigateToPage(goToNextPage: Boolean) {
        if (!binding.drawingView.isEmpty()) {
            val bitmap = Bitmap.createBitmap(
                binding.ivPdf.width,
                binding.ivPdf.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            binding.drawingView.exportBitmap().let { canvas.drawBitmap(it, 0f, 0f, null) }
            pageSignatures[currentPageIndex] = bitmap
            binding.drawingView.clearDrawing()
        }


        if (goToNextPage && currentPageIndex < totalPages - 1) {
            currentPageIndex++
        } else if (!goToNextPage && currentPageIndex > 0) {
            currentPageIndex--
        }
        showPage(currentPageIndex)
        binding.btnPrevPage.visibility = if (currentPageIndex == 0) INVISIBLE else VISIBLE
        binding.btnNextPage.visibility =
            if (currentPageIndex == totalPages - 1) INVISIBLE else VISIBLE
    }

    private fun openPdf(filePath: String) {
        val file = File(filePath)
        try {
            val fileDescriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)

            totalPages = pdfRenderer.pageCount
            currentPageIndex = 0

            if (totalPages > 0) {
                showPage(currentPageIndex)
            }
        } catch (e: IOException) {
            Timber.e(e, "Error al abrir el archivo PDF")
            Toast.makeText(requireContext(), R.string.error_file, Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    private fun showPage(pageIndex: Int) {
        val page = pdfRenderer.openPage(pageIndex)
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        binding.ivPdf.setImageBitmap(bitmap)
        binding.tvPageInfo.text = getString(R.string.page_info, pageIndex + 1, totalPages)
        binding.drawingView.setBackgroundBitmap(null)
        pageSignatures[pageIndex]?.let { signature ->
            binding.drawingView.setBackgroundBitmap(signature)
        }
        binding.drawingView.clearDrawing()
    }

    private fun showSaveDialog(defaultFileName: String, onSave: (String) -> Unit) {
        val editText = EditText(requireContext()).apply {
            setText(defaultFileName)
            setSelectAllOnFocus(true)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.save_pdf))
            .setView(editText)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val fileName = editText.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    onSave(fileName)
                } else {
                    editText.error = getString(R.string.error_empty_name)
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun savePdfToUri(uri: Uri) {
        try {
            val originalFilePath = arguments?.getString(ARG_FILE_PATH) ?: return
            val fileDescriptor = ParcelFileDescriptor.open(
                File(originalFilePath),
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            val pdfRenderer = PdfRenderer(fileDescriptor)

            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                val document = PdfDocument()

                for (i in 0 until pdfRenderer.pageCount) {
                    val page = pdfRenderer.openPage(i)
                    val pageBitmap =
                        Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(pageBitmap)
                    page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    pageSignatures[i]?.let { signature ->
                        val scaledSignature = Bitmap.createScaledBitmap(
                            signature,
                            page.width,
                            page.height,
                            false
                        )
                        canvas.drawBitmap(scaledSignature, 0f, 0f, null)
                    }

                    val pageInfo =
                        PdfDocument.PageInfo.Builder(pageBitmap.width, pageBitmap.height, i + 1)
                            .create()
                    val pdfPage = document.startPage(pageInfo)
                    pdfPage.canvas.drawBitmap(pageBitmap, 0f, 0f, null)
                    document.finishPage(pdfPage)

                    pageBitmap.recycle()
                }

                document.writeTo(outputStream)
                document.close()
                pdfRenderer.close()

                Toast.makeText(
                    requireContext(),
                    getString(R.string.file_saved, uri.toString()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.error_saving_file),
                Toast.LENGTH_SHORT
            ).show()
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
