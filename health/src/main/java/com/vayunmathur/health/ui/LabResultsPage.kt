package com.vayunmathur.health.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.records.MedicalResource
import com.google.fhir.model.r4b.Observation
import com.vayunmathur.health.R
import com.vayunmathur.health.Route
import com.vayunmathur.health.util.HealthAPI
import com.vayunmathur.library.ui.IconNavigation
import com.vayunmathur.library.ui.IconUpload
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.library.util.SecureResultReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPersonalHealthRecordApi::class)
@Composable
fun LabResultsPage(backStack: NavBackStack<Route>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var labResults by remember { mutableStateOf(listOf<Observation>()) }
    var showInstallDialog by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            withContext(Dispatchers.IO) {
                labResults = HealthAPI.allMedicalRecords(MedicalResource.MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS).map {
                    JSON.decodeFromString<Observation>(it.fhirResource.data)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    if (showInstallDialog) {
        AlertDialog(
            onDismissRequest = { showInstallDialog = false },
            title = { Text("OpenAssistant Required") },
            text = { Text("The OpenAssistant app is required for offline, secure, medical document extraction. Please install it from GitHub.") },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW,
                        "https://github.com/vayun-mathur/Modern-Apps".toUri())
                    context.startActivity(intent)
                    showInstallDialog = false
                }) {
                    Text("View on GitHub")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val labSchema = """
        {
          "type": "object",
          "properties": {
            "resourceType": { "const": "Observation" },
            "status": { "enum": ["final", "amended", "corrected"] },
            "category": { 
               "type": "array", 
               "items": { 
                 "type": "object", 
                 "properties": { 
                   "coding": { 
                     "type": "array", 
                     "items": { 
                       "type": "object", 
                       "properties": { 
                         "system": { "const": "http://terminology.hl7.org/CodeSystem/observation-category" },
                         "code": { "const": "laboratory" }
                       },
                       "required": ["system", "code"]
                     } 
                   } 
                 },
                 "required": ["coding"]
               } 
            },
            "code": { 
              "type": "object", 
              "properties": { 
                "text": { "type": "string", "description": "The name of the test (e.g. Glucose)" } 
              }, 
              "required": ["text"] 
            },
            "subject": { 
              "type": "object", 
              "properties": { 
                "display": { "type": "string", "description": "The patient's name" } 
              }, 
              "required": ["display"] 
            },
            "effectiveDateTime": { 
              "type": "string", 
              "pattern": "^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])(T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\\.[0-9]+)?(Z|(\\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00)))?)?)?$",
              "description": "ISO 8601 date-time string. Use ONLY if found." 
            },
            "valueQuantity": { 
              "type": "object", 
              "properties": { 
                "value": { "type": "number" }, 
                "unit": { "type": "string" } 
              }, 
              "required": ["value"] 
            },
            "valueString": { "type": "string" }
          },
          "required": ["resourceType", "status", "category", "code", "subject"],
          "oneOf": [
            { "required": ["valueQuantity"] },
            { "required": ["valueString"] },
            { "not": { "anyOf": [{ "required": ["valueQuantity"] }, { "required": ["valueString"] }] } }
          ],
          "description": "FHIR Observation for Lab Results. Provide EITHER valueQuantity OR valueString."
        }
    """.trimIndent()

    val resultReceiver = remember {
        SecureResultReceiver(null) { resultCode, resultData ->
            if (resultCode == 0) {
                val jsonResult = resultData?.getString("json_result")
                if (jsonResult != null) {
                    scope.launch {
                        try {
                            HealthAPI.writeMedicalRecord(jsonResult)
                            refresh()
                        } catch (e: Exception) {
                            Log.e("LabResultsPage", "Failed to write medical record", e)
                        }
                    }
                }
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val imagePaths = convertPdfToImages(context, uri)
                    if (imagePaths.isNotEmpty()) {
                        val intent = Intent().apply {
                            setClassName("com.vayunmathur.openassistant", "com.vayunmathur.openassistant.util.InferenceService")
                            putExtra("user_text", "Extract laboratory result details from these images.")
                            
                            val uris = imagePaths.map { path ->
                                val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
                                context.grantUriPermission("com.vayunmathur.openassistant", u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                u
                            }

                            putParcelableArrayListExtra("image_uris", ArrayList(uris))
                            putExtra("schema", labSchema)
                            putExtra("RECEIVER", resultReceiver)
                        }
                        context.startService(intent)
                    }
                } catch (e: Exception) {
                    Log.e("LabResultsPage", "Error processing PDF", e)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_lab_results)) },
                navigationIcon = {
                    IconNavigation(backStack)
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (isOpenAssistantInstalled(context)) {
                    pdfLauncher.launch("application/pdf")
                } else {
                    showInstallDialog = true
                }
            }) {
                IconUpload()
            }
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).padding(horizontal = 16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labResults.forEach {
                    ObservationCard(it)
                }
            }
        }
    }
}

@Composable
fun ObservationCard(observation: Observation) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.baseline_location_pin_24), // Using pin for labs
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(observation.code.text?.value ?: "Unknown Test", style = MaterialTheme.typography.titleLarge)
                Text("Status: ${observation.status.value?.getDisplay() ?: "Unknown"}", style = MaterialTheme.typography.bodyMedium)

                val valueDisplay = when (val v = observation.value) {
                    is Observation.Value.Quantity -> {
                        val q = v.value
                        "${q.value?.value ?: ""} ${q.unit?.value ?: ""}"
                    }
                    is Observation.Value.String -> v.value.value
                    else -> null
                }
                if (valueDisplay != null) {
                    Text("Result: $valueDisplay", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }

                val dateDisplay = when (val eff = observation.effective) {
                    is Observation.Effective.DateTime -> eff.value.value?.toString()
                    else -> null
                }
                if (dateDisplay != null) {
                    Text("Date: $dateDisplay", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun isOpenAssistantInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("com.vayunmathur.openassistant", 0)
        true
    } catch (e: Exception) {
        false
    }
}

private fun convertPdfToImages(context: Context, uri: Uri): List<String> {
    val imagePaths = mutableListOf<String>()
    try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { parcelFileDescriptor ->
            val renderer = PdfRenderer(parcelFileDescriptor)
            for (i in 0 until renderer.pageCount) {
                try {
                    Log.d("LabResultsPage", "Rendering PDF page $i")
                    val page = renderer.openPage(i)
                    val bitmap = createBitmap(page.width, page.height)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val file = File(context.cacheDir, "pdf_page_$i.png")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    page.close()
                    imagePaths.add(file.absolutePath)
                } catch (e: Exception) {
                    Log.e("LabResultsPage", "Error rendering PDF page $i", e)
                }
            }
            renderer.close()
        }
    } catch (e: Exception) {
        Log.e("LabResultsPage", "Error opening PDF file descriptor for URI: $uri", e)
    }
    return imagePaths
}
