package com.vayunmathur.health.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import androidx.core.content.FileProvider
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.records.MedicalResource
import com.google.fhir.model.r4b.Immunization
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPersonalHealthRecordApi::class)
@Composable
fun ImmunizationsPage(backStack: NavBackStack<Route>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var immunizations by remember { mutableStateOf(listOf<Immunization>()) }
    var showInstallDialog by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            withContext(Dispatchers.IO) {
                immunizations = HealthAPI.allMedicalRecords(MedicalResource.MEDICAL_RESOURCE_TYPE_VACCINES).map {
                    JSON.decodeFromString<Immunization>(it.fhirResource.data)
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
            title = { Text(stringResource(R.string.open_assistant_required)) },
            text = { Text(stringResource(R.string.open_assistant_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/vayun-mathur/Modern-Apps"))
                    context.startActivity(intent)
                    showInstallDialog = false
                }) {
                    Text(stringResource(R.string.view_on_github))
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val immunizationSchema = """
        {
          "type": "object",
          "description": "FHIR Immunization resource schema",
          "properties": {
            "resourceType": { "const": "Immunization" },
            "status": { "enum": ["completed", "entered-in-error", "not-done"] },
            "vaccineCode": {
              "type": "object",
              "properties": {
                "text": { "type": "string" }
              },
              "required": ["text"]
            },
            "patient": {
              "type": "object",
              "properties": {
                "display": { "type": "string" }
              },
              "required": ["display"]
            },
            "occurrenceDateTime": { "type": "string" },
            "lotNumber": { "type": "string" },
            "expirationDate": { "type": "string" }
          },
          "required": ["resourceType", "status", "vaccineCode", "patient"]
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
                            Log.e("ImmunizationsPage", "Failed to write medical record", e)
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
                            putExtra("user_text", "Extract immunization details from these images.")
                            
                            val uris = imagePaths.map { path ->
                                val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
                                context.grantUriPermission("com.vayunmathur.openassistant", u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                u
                            }

                            putParcelableArrayListExtra("image_uris", ArrayList(uris))
                            putExtra("schema", immunizationSchema)
                            putExtra("RECEIVER", resultReceiver)
                        }
                        context.startService(intent)
                    }
                } catch (e: Exception) {
                    Log.e("ImmunizationsPage", "Error processing PDF", e)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_immunizations)) },
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
                immunizations.forEach {
                    ImmunizationCard(it)
                }
            }
        }
    }
}

@Composable
fun ImmunizationCard(immunization: Immunization) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.baseline_favorite_24),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(immunization.vaccineCode.text?.value ?: stringResource(R.string.unknown), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.status_format, immunization.status.value?.getDisplay() ?: stringResource(R.string.unknown)), style = MaterialTheme.typography.bodyMedium)
                
                val occurrenceDisplay = when (val occ = immunization.occurrence) {
                    is Immunization.Occurrence.DateTime -> occ.value.value?.toString()
                    is Immunization.Occurrence.String -> occ.value.value
                }
                if (occurrenceDisplay != null) {
                    Text(stringResource(R.string.date_format_label, occurrenceDisplay), style = MaterialTheme.typography.bodyMedium)
                }
                
                if (immunization.lotNumber?.value != null) {
                    Text(stringResource(R.string.lot_format, immunization.lotNumber!!.value), style = MaterialTheme.typography.bodySmall)
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
        val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        val renderer = PdfRenderer(parcelFileDescriptor)
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val file = File(context.cacheDir, "pdf_page_$i.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.close()
            page.close()
            imagePaths.add(file.absolutePath)
        }
        renderer.close()
        parcelFileDescriptor.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return imagePaths
}
