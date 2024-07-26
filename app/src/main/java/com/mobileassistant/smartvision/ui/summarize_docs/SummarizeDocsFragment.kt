package com.mobileassistant.smartvision.ui.summarize_docs

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import com.mobileassistant.smartvision.MainActivity
import com.mobileassistant.smartvision.R
import com.mobileassistant.smartvision.databinding.FragmentSummarizeDocsBinding
import com.mobileassistant.smartvision.mlkit.textdetector.ACTIVATED_STATUS_TEXT
import com.mobileassistant.smartvision.mlkit.textdetector.DEACTIVATED_STATUS_TEXT
import com.mobileassistant.smartvision.ui.detect_objects.GEMINI_AI_API_KEY
import com.mobileassistant.smartvision.ui.settings.ANNOUNCEMENT_STATUS_KEY
import com.mobileassistant.smartvision.ui.settings.SMART_VISION_PREFERENCES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale


private const val REQUEST_CODE_READ_EXTERNAL_STORAGE = 1
private const val REQUEST_CODE_READ_MEDIA_IMAGES = 2
private const val PDF_TYPE = "application/pdf"
private const val GEMINI_MODEL_PRO = "gemini-1.5-pro"
private const val FIVE_THOUSAND_MILLI_SECONDS = 5000L

class SummarizeDocsFragment : Fragment(), TextToSpeech.OnInitListener {

    //    private lateinit var viewModel: SummarizeDocsViewModel
    private var _binding: FragmentSummarizeDocsBinding? = null
    private lateinit var selectPdfButton: Button
    private lateinit var summarizeAnotherDocButton: Button
    private lateinit var extractedTextView: TextView
    private lateinit var promptEditText: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var allStepsLayout: LinearLayout
    private lateinit var documentSummaryLayout: LinearLayout
    private lateinit var announcementLayout: LinearLayout
    private lateinit var announcementToggleButton: ToggleButton
    private var announcementEnabledStatus: Boolean? = false
    private var sharedPreferences: SharedPreferences? = null
    private var textToSpeech: TextToSpeech? = null
    private var resultLauncher: ActivityResultLauncher<Intent>? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val Context.isConnected: Boolean
        get() {
            return (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo?.isConnected == true
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSummarizeDocsBinding.inflate(inflater, container, false)
        val root: View = binding.root
        selectPdfButton = binding.selectPdfButton
        summarizeAnotherDocButton = binding.summarizeAnotherDocButton
        extractedTextView = binding.extractedTextView
        promptEditText = binding.promptEditText
        progressBar = binding.progressBar
        allStepsLayout = binding.allStepsLayout
        documentSummaryLayout = binding.documentSummaryLayout
        announcementLayout = binding.announcementLayout
        announcementToggleButton = binding.announcementToggleButton
        sharedPreferences = activity?.getSharedPreferences(
            SMART_VISION_PREFERENCES, Context.MODE_PRIVATE
        )
        announcementEnabledStatus = sharedPreferences?.getBoolean(ANNOUNCEMENT_STATUS_KEY, false)
        announcementToggleButton.isChecked = announcementEnabledStatus == true
        textToSpeech = TextToSpeech(context, this)
        resultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data: Intent? = result?.data
            data?.let {
                val uri = data.data
                progressBar.visibility = View.VISIBLE
                extractPDF(uri)
            }
        }

        selectPdfButton.setOnClickListener {
            if (context?.isConnected == true) {
                onSelectPdfClick()
            } else {
                Toast.makeText(
                    context, getString(R.string.internet_not_working_msg), Toast.LENGTH_SHORT
                ).show()
            }
        }

        promptEditText.doOnTextChanged { text, _, _, _ ->
            if (text.toString().trim().isEmpty()) {
                with(promptEditText) {
                    error = getString(R.string.prompt_text_empty_msg)
                    requestFocus()
                }
                selectPdfButton.isEnabled = false
            } else {
                selectPdfButton.isEnabled = true
            }
        }

        announcementToggleButton.setOnCheckedChangeListener { _, isChecked ->
            announcementEnabledStatus = isChecked
            announceTextToUserIfEnabled()
        }

        summarizeAnotherDocButton.setOnClickListener {
            allStepsLayout.visibility = View.VISIBLE
            documentSummaryLayout.visibility = View.GONE
        }
        return root
    }

    private fun onSelectPdfClick() {
        context?.let {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                // For Android 12 and lower
                if (ContextCompat.checkSelfPermission(
                        it, Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        activity as MainActivity,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                        REQUEST_CODE_READ_EXTERNAL_STORAGE
                    )
                } else {
                    selectPdfFromDeviceStorage()
                }
            } else {
                // For Android13 and higher
                if (ContextCompat.checkSelfPermission(
                        it, Manifest.permission.READ_MEDIA_IMAGES
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        activity as MainActivity,
                        arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                        REQUEST_CODE_READ_MEDIA_IMAGES
                    )
                } else {
                    selectPdfFromDeviceStorage()
                }
            }
        }
    }

    private fun selectPdfFromDeviceStorage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.setType(PDF_TYPE)
        resultLauncher?.launch(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if ((requestCode == REQUEST_CODE_READ_EXTERNAL_STORAGE || requestCode == REQUEST_CODE_READ_MEDIA_IMAGES) && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            selectPdfFromDeviceStorage()
        } else {
            Toast.makeText(context, getString(R.string.permission_denied), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun extractPDF(uri: Uri?) {
        try {
            val inputStream = uri?.let { context?.contentResolver?.openInputStream(it) }
            var extractedText = ""
            val reader = PdfReader(inputStream)
            val n = reader.numberOfPages

            for (i in 0 until n) {
                extractedText = extractedText + PdfTextExtractor.getTextFromPage(reader, i + 1)
                    .trim { it <= ' ' } + "\n"
            }
            summarizeExtractedText(extractedText)
            reader.close()
        } catch (e: Exception) {
            extractedTextView.text = getString(R.string.error_extracting_the_file, e)
            progressBar.visibility = View.GONE
        }
    }

    private fun summarizeExtractedText(extractedText: String) {
        lifecycleScope.launch(IO) {
            val generativeModel = GenerativeModel(
                modelName = GEMINI_MODEL_PRO, apiKey = GEMINI_AI_API_KEY
            )

            val inputContent = content {
                text(promptEditText.text.toString())
                text(extractedText)
            }

            val generateContent = generativeModel.generateContent(inputContent)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                extractedTextView.text = generateContent.text
                allStepsLayout.visibility = View.GONE
                documentSummaryLayout.visibility = View.VISIBLE
                announcementLayout.visibility = View.VISIBLE
                announceTextToUserIfEnabled()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech!!.setLanguage(Locale.US)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language not supported!")
                Toast.makeText(
                    context, getString(R.string.language_not_supported), Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        textToSpeech?.let {
            it.stop()
            it.shutdown()
        }
    }

    private fun announceTextToUserIfEnabled() {
        lifecycleScope.launch(IO) {
            if (announcementEnabledStatus == true) {
                textToSpeech?.let {
                    val titleString =
                        ACTIVATED_STATUS_TEXT + getString(R.string.document_summarized_msg)
                    it.speak(titleString, TextToSpeech.QUEUE_ADD, null, "")
                    Thread.sleep(FIVE_THOUSAND_MILLI_SECONDS)
                    beep()
                    it.speak(
                        extractedTextView.text.toString(), TextToSpeech.QUEUE_ADD, null, ""
                    )
                }
            } else {
                textToSpeech?.speak(DEACTIVATED_STATUS_TEXT, TextToSpeech.QUEUE_FLUSH, null, "")
            }
        }
    }

    private fun beep() {
        val toneG = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
    }
}