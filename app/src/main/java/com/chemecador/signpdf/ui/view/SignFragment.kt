package com.chemecador.signpdf.ui.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.chemecador.signpdf.R
import com.chemecador.signpdf.databinding.DialogSignatureBinding
import com.chemecador.signpdf.databinding.FragmentSignBinding
import com.chemecador.signpdf.ui.view.util.DrawingView
import com.chemecador.signpdf.utils.ViewUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException

@AndroidEntryPoint
class SignFragment : Fragment() {

    private var _binding: FragmentSignBinding? = null
    private val binding get() = _binding!!

    private lateinit var pdfRenderer: PdfRenderer
    private var currentPageIndex: Int = 0
    private var totalPages: Int = 0
    private val pageSignatures = mutableMapOf<Int, Pair<Bitmap, Pair<Float, Float>>>()
    private var cursorX: Float = 0f
    private var cursorY: Float = 0f
    private var signSize: Float = 17f
    private val createFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
            uri?.let { savePdfToUri(it) }
        }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignBinding.inflate(inflater, container, false)
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
        activity.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
        }
        activity.title = ""
    }

    private fun setupListeners() {
        binding.btnPrevPage.visibility = INVISIBLE
        binding.btnPrevPage.setOnClickListener { navigateToPage(false) }
        binding.btnNextPage.setOnClickListener { navigateToPage(true) }
        binding.ibHome.setOnClickListener { findNavController().popBackStack() }
        binding.btnSign.setOnClickListener {
            lifecycleScope.launch {

                showSignatureDialog { signatureBitmap, signAllPages ->
                    if (signatureBitmap == null) {
                        return@showSignatureDialog
                    }
                    if (signAllPages) {
                        for (i in 0 until totalPages) {
                            val (pdfX, pdfY) = getPdfCoordinates(cursorX, cursorY)
                            pageSignatures[i] = signatureBitmap to Pair(pdfX, pdfY)
                        }
                    } else {
                        insertSignatureAtCursor(signatureBitmap)
                    }

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
            ViewUtils.show(binding.btnSign)
        }
    }

    private fun showSignatureDialog(onSignatureComplete: (Bitmap?, Boolean) -> Unit) {
        val dialogView =
            LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_signature, binding.root, false)
        val binding = DialogSignatureBinding.bind(dialogView)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setCancelable(false)
            .create()

        binding.tvOptions.setOnClickListener {
            val isCustomizationVisible = binding.linearOptions.isVisible
            binding.linearOptions.isVisible = !isCustomizationVisible
            binding.tvOptions.setCompoundDrawablesWithIntrinsicBounds(
                0,
                0,
                if (isCustomizationVisible) R.drawable.ic_arrow_down else R.drawable.ic_arrow_up,
                0
            )
        }

        binding.colorSelector.addOnButtonCheckedListener { _, checkedId, _ ->
            val selectedColor = when (checkedId) {
                R.id.btn_black -> Color.BLACK
                R.id.btn_red -> Color.RED
                R.id.btn_blue -> Color.BLUE
                else -> Color.BLACK
            }
            binding.drawingView.setSignatureColor(selectedColor)

        }
        binding.drawingView.setOnDrawListener(object : DrawingView.OnDrawListener {
            override fun onDrawStateChanged(hasDrawn: Boolean) {
                if (hasDrawn && !binding.ibDelete.isVisible) {
                    ViewUtils.show(binding.ibDelete)
                } else if (!hasDrawn && binding.ibDelete.isVisible) {
                    ViewUtils.hide(binding.ibDelete)
                }
                if (binding.tvHint.isVisible) {
                    ViewUtils.hide(binding.tvHint)
                    ViewUtils.show(binding.btnCancel)
                    ViewUtils.show(binding.btnFinish)
                }
            }
        })

        binding.sliderSize.addOnChangeListener { _, value, _ ->
            signSize = value
        }

        binding.ibDelete.setOnClickListener {
            binding.drawingView.clearDrawing()
        }

        binding.ibClose.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnFinish.setOnClickListener {
            if (binding.drawingView.isEmpty()) {
                return@setOnClickListener
            }
            val signatureBitmap = binding.drawingView.getBitmap()
            val signAllPages = binding.rbAllPages.isChecked
            dialog.dismiss()
            onSignatureComplete(signatureBitmap, signAllPages)
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
                            page.width * signSize / 100
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

                findNavController().navigate(
                    R.id.action_signFragment_to_showFragment,
                    bundleOf(ARG_URI_PATH to uri.toString())
                )
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
        const val ARG_URI_PATH = "pdfUri"
    }
}
