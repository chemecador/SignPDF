package com.chemecador.signpdf.ui.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.chemecador.signpdf.R
import com.chemecador.signpdf.databinding.DialogSignatureBinding
import com.chemecador.signpdf.databinding.FragmentShowPdfBinding
import com.chemecador.signpdf.ui.view.util.DrawingView
import com.chemecador.signpdf.utils.ViewUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
    private val pageSignatures = mutableMapOf<Int, Pair<Bitmap, Pair<Float, Float>>>()
    private var cursorX: Float = 0f
    private var cursorY: Float = 0f
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
        setupListeners()
        setupMenu()
        initUI()
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
        binding.btnFinish.setOnClickListener {
            lifecycleScope.launch {
                showSignatureDialog { signatureBitmap ->
                    insertSignatureAtCursor(signatureBitmap ?: return@showSignatureDialog)
                    val originalFilePath = requireArguments().getString(ARG_FILE_PATH)!!
                    val originalFileName = File(originalFilePath).nameWithoutExtension
                    val defaultFileName = "${originalFileName}_signed.pdf"
                    createFileLauncher.launch(defaultFileName)


                }

            }
        }
        binding.ivPdf.setOnPhotoTapListener { view, x, y ->
            cursorX = x * view.width
            cursorY = y * view.height
            showCursor()
        }
    }

    private fun insertSignatureAtCursor(signatureBitmap: Bitmap) {
        val currentPage = currentPageIndex
        val (pdfX, pdfY) = getPdfCoordinates(cursorX, cursorY)
        pageSignatures[currentPage] = signatureBitmap to Pair(pdfX, pdfY)
    }

    private fun getPdfCoordinates(imageViewX: Float, imageViewY: Float): Pair<Float, Float> {
        val pdfWidth = 595f
        val pdfHeight = 842f
        val imageViewWidth = binding.ivPdf.width.toFloat()
        val imageViewHeight = binding.ivPdf.height.toFloat()
        val pdfX = (imageViewX / imageViewWidth) * pdfWidth
        val pdfY = (imageViewY / imageViewHeight) * pdfHeight
        return Pair(pdfX, pdfY)
    }

    private fun showCursor() {
        val displayRect = binding.ivPdf.displayRect ?: return

        val scaledX = displayRect.left + (cursorX * displayRect.width() / binding.ivPdf.width)
        val scaledY = displayRect.top + (cursorY * displayRect.height() / binding.ivPdf.height)

        binding.ivPencil.apply {
            x = scaledX + binding.ivPencil.height * 0.05f
            y = scaledY - binding.ivPencil.height
            isVisible = true
        }

        if (binding.tvHint.isVisible) {
            ViewUtils.hide(binding.tvHint)
            ViewUtils.show(binding.btnCancel)
            ViewUtils.show(binding.btnFinish)
        }
    }


    private fun showSignatureDialog(onSignatureComplete: (Bitmap?) -> Unit) {
        val dialogView =
            LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_signature, binding.root, false)
        val binding = DialogSignatureBinding.bind(dialogView)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setCancelable(false)
            .create()

        binding.drawingView.setOnDrawListener(object : DrawingView.OnDrawListener {
            override fun onDrawStateChanged(hasDrawn: Boolean) {
                if (hasDrawn && !binding.ibDelete.isVisible) {
                    ViewUtils.show(binding.ibDelete)
                } else if (!hasDrawn && binding.ibDelete.isVisible) {
                    ViewUtils.hide(binding.ibDelete)
                }
            }
        })

        binding.ibDelete.setOnClickListener {
            binding.drawingView.clearDrawing()
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
            onSignatureComplete(null)
        }

        binding.btnFinish.setOnClickListener {
            val signatureBitmap = binding.drawingView.getBitmap()
            dialog.dismiss()
            onSignatureComplete(signatureBitmap)
        }

        dialog.show()
    }

    private fun navigateToPage(goToNextPage: Boolean) {
        lifecycleScope.launch {
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

                    pageSignatures[i]?.let { (signature, position) ->
                        val (pdfX, pdfY) = position

                        val signatureWidth =
                            page.width * 0.18f
                        val signatureHeight = signature.height * (signatureWidth / signature.width)

                        val scaledSignature = Bitmap.createScaledBitmap(
                            signature,
                            signatureWidth.toInt(),
                            signatureHeight.toInt(),
                            true
                        )

                        val paint = Paint().apply {
                            isAntiAlias = true
                            isFilterBitmap = true
                            isDither = true
                        }
                        val centeredX = pdfX - (scaledSignature.width / 2)
                        val centeredY = pdfY - (scaledSignature.height / 2)
                        canvas.drawBitmap(scaledSignature, centeredX, centeredY, paint)
                        scaledSignature.recycle()
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
