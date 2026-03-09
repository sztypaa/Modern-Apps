package com.vayunmathur.maps

import android.app.DownloadManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.vayunmathur.library.ui.DynamicTheme
import com.vayunmathur.library.util.DataStoreUtils
import com.vayunmathur.library.util.MainNavigation
import com.vayunmathur.library.util.rememberNavBackStack
import com.vayunmathur.maps.data.AmenityDatabase
import com.vayunmathur.maps.data.buildAmenityDatabase
import com.vayunmathur.maps.ui.DownloadedMapsPage
import com.vayunmathur.maps.ui.MapPage
import com.vayunmathur.maps.ui.SearchPage
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.io.File

fun ensurePmtilesReady(context: Context): String {
    val fileName = "world_z0-6.pmtiles"
    val outFile = File(context.filesDir, fileName)

    if (!outFile.exists()) {
        context.assets.open(fileName).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
    return "pmtiles://file://${outFile.absolutePath}"
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val ds = DataStoreUtils.getInstance(this)
        ensurePmtilesReady(this)
        setContent {
            DynamicTheme {
                val dbSetup by ds.booleanFlow("dbSetupComplete").collectAsState(false)
                if(dbSetup) {
                    val db = remember { buildAmenityDatabase(this@MainActivity) }
                    Navigation(ds, db)
                } else {
                    DatabaseSetup(ds)
                }
            }
        }
    }
}

@Composable
fun DatabaseSetup(ds: DataStoreUtils) {
    val context = LocalContext.current
    val progress by ds.doubleFlow("downloadProgress").collectAsState(0.0)

    LaunchedEffect(Unit) {
        // Define all the files needed for the routing engine and amenities
        val filesToDownload = listOf(
            Triple("https://data.vayunmathur.com/amenities.db", "amenities.db", "Downloading Amenity Database..."),
            Triple("https://data.vayunmathur.com/nodes_lookup.bin", "nodes_lookup.bin", "Downloading Routing Nodes..."),
            Triple("https://data.vayunmathur.com/nodes_spatial.bin", "nodes_spatial.bin", "Downloading Spatial Index..."),
            Triple("https://data.vayunmathur.com/edges.bin", "edges.bin", "Downloading Routing Graph..."),
            Triple("https://data.vayunmathur.com/edge_index.bin", "edge_index.bin", "Downloading Edge Index...")
        )

        // Enqueue all downloads and collect their DownloadManager IDs
        val downloadIds = filesToDownload.map { (url, fileName, desc) ->
            downloadMapData(context, url, fileName, desc)
        }

        // Poll DownloadManager until aggregate progress is 1.0 (100%)
        while (true) {
            val currentProgress = getAggregateProgress(context, downloadIds)
            ds.setDouble("downloadProgress", currentProgress)

            if (currentProgress >= 1.0) break
            delay(500) // Check twice a second
        }

        ds.setBoolean("dbSetupComplete", true)
    }

    Scaffold { paddingValues ->
        Box(
            Modifier.padding(paddingValues).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(progress = { progress.toFloat() })
                Spacer(modifier = Modifier.height(16.dp))
                Text("Downloading Offline Map Data: ${String.format("%.1f", progress * 100)}%")
                Text("Please keep the app open.", modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Serializable
sealed interface Route: NavKey {
    @Serializable
    data object MapPage: Route
    @Serializable
    data object DownloadedMapsPage: Route

    @Serializable
    data class SearchPage(val idx: Int?, val east: Double, val west: Double, val north: Double, val south: Double): Route
}

@Composable
fun Navigation(ds: DataStoreUtils, db: AmenityDatabase, viewModel: SelectedFeatureViewModel = viewModel()) {
    val backStack = rememberNavBackStack<Route>(Route.MapPage)
    MainNavigation(backStack) {
        entry<Route.MapPage> {
            MapPage(backStack, viewModel, ds, db)
        }
        entry<Route.DownloadedMapsPage> {
            DownloadedMapsPage(backStack)
        }
        entry<Route.SearchPage> {
            SearchPage(backStack, viewModel, db, it.idx, it.east, it.west, it.north, it.south)
        }
    }
}

fun getAggregateProgress(context: Context, downloadIds: List<Long>): Double {
    if (downloadIds.isEmpty()) return 1.0

    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val query = DownloadManager.Query().setFilterById(*downloadIds.toLongArray())
    val cursor = downloadManager.query(query) ?: return 0.0

    var totalBytes = 0L
    var downloadedBytes = 0L
    var allCompleted = true

    while (cursor.moveToNext()) {
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

        if (status != DownloadManager.STATUS_SUCCESSFUL) {
            allCompleted = false
        }

        if (bytesTotal > 0) {
            totalBytes += bytesTotal
            downloadedBytes += bytesDownloaded
        }
    }
    cursor.close()

    // If totalBytes is still 0 (e.g., pending connection), report 0%
    if (totalBytes == 0L) return if (allCompleted) 1.0 else 0.0

    // Ensure we return exactly 1.0 if completed, otherwise a precise ratio
    return if (allCompleted) 1.0 else (downloadedBytes.toDouble() / totalBytes)
}

fun downloadMapData(context: Context, url: String, fileName: String, description: String): Long {
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    // 1. Check if the specific file is already downloading or completed
    val query = DownloadManager.Query()
    val cursor = downloadManager.query(query)

    if (cursor != null) {
        while (cursor.moveToNext()) {
            val title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

            // Check by the specific fileName so we don't mix up the routing binaries
            if (title == fileName) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))

                // If it's running, pending, or successful, return the existing ID
                if (status == DownloadManager.STATUS_RUNNING ||
                    status == DownloadManager.STATUS_PENDING ||
                    status == DownloadManager.STATUS_SUCCESSFUL) {
                    cursor.close()
                    return id
                }
            }
        }
        cursor.close()
    }

    // 2. If no active/completed download found for this file, start a new one
    val request = DownloadManager.Request(url.toUri())
        .setTitle(fileName)
        .setDescription(description)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, null, fileName)
        .setAllowedOverMetered(false) // Wait for WiFi for these massive files
        .setRequiresCharging(false)

    return downloadManager.enqueue(request)
}