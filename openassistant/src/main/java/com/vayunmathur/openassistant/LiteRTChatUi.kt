package com.vayunmathur.openassistant

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.google.ai.edge.litertlm.*
import com.vayunmathur.library.ui.IconAdd
import com.vayunmathur.library.ui.IconClose
import com.vayunmathur.library.ui.IconStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.util.*
import java.util.regex.Pattern

/**
 * Data class for Chat Messages supporting multiple images
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val imageUris: List<Uri> = emptyList()
)

/**
 * ToolSet implementation
 */
class AssistantToolSet(private val context: Context) : ToolSet {
    @Tool(description = "Get the current date and time in the local timezone")
    fun getLocalCurrentDateTime(): String {
        val now = LocalDateTime.now()
        val tz = TimeZone.getDefault().id
        return "$tz: $now"
    }

    @SuppressLint("QueryPermissionsNeeded")
    @Tool(description = "Get a list of installed apps on the device")
    fun getListOfApps(): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.map { it.loadLabel(pm).toString() }.toString()
    }

    @Tool(description = "Open an app given its package id")
    fun openApp(@ToolParam(description = "package id") packageId: String): String {
        val intent = context.packageManager.getLaunchIntentForPackage(packageId)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Success: Opened $packageId"
        } else "Error: App not found"
    }

    @Tool(description = "Send a message")
    fun sendMessage(recipient: String, message: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "smsto:$recipient".toUri()
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened messaging app."
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    @Tool(description = "Make a phone call")
    fun makePhoneCall(recipient: String): String {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = "tel:$recipient".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened dialer."
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    @Tool(description = "Get weather")
    fun getWeather(latitude: Double, longitude: Double): String = "Weather: 22°C, Sunny."
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalApi::class)
@Composable
fun LiteRTChatUi(modelFile: File) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var engine by remember { mutableStateOf<Engine?>(null) }
    var conversation by remember { mutableStateOf<Conversation?>(null) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    var isInitializing by remember { mutableStateOf(true) }

    // State for multiple images
    val selectedImageUris = remember { mutableStateListOf<Uri>() }
    val selectedImageFiles = remember { mutableStateListOf<File>() }

    var activeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val isGenerating by remember { derivedStateOf { activeJob?.isActive == true } }

    val assistantTools = remember { AssistantToolSet(context) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Support multiple picks
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        uris.forEach { uri ->
            selectedImageUris.add(uri)
            scope.launch(Dispatchers.IO) {
                val file = copyUriToFile(context, uri)
                withContext(Dispatchers.Main) { selectedImageFiles.add(file) }
            }
        }
    }

    LaunchedEffect(modelFile) {
        withContext(Dispatchers.IO) {
            try {
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    visionBackend = Backend.GPU(),
                    //audioBackend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
                ExperimentalFlags.enableConversationConstrainedDecoding = false
                engine = Engine(config).apply { initialize() }

                val systemPrompt = """
                    You are a helpful Android assistant. Use tool calls:
                    <start_function_call>tool_name(arg1="val1")<end_function_call>
                    Interpret tool results conversationally.
                """.trimIndent()

                conversation = engine?.createConversation(ConversationConfig(
                    systemInstruction = Contents.of(systemPrompt),
                    tools = listOf(tool(assistantTools)),
                    automaticToolCalling = false
                ))
                isInitializing = false
            } catch (e: Exception) { isInitializing = false }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Open Assistant", fontWeight = FontWeight.Bold) }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // MULTI-IMAGE PRE-SEND PREVIEW
                if (selectedImageUris.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(bottom = 8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            items(selectedImageUris) { uri ->
                                Box(modifier = Modifier.size(80.dp)) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Selected image",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = {
                                            val index = selectedImageUris.indexOf(uri)
                                            if (index != -1) {
                                                selectedImageUris.removeAt(index)
                                                if (index < selectedImageFiles.size) selectedImageFiles.removeAt(index)
                                            }
                                        }
                                    ) {
                                        IconClose()
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    tonalElevation = 3.dp,
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        IconButton(onClick = { imagePickerLauncher.launch("image/*") }, enabled = !isGenerating) { IconAdd() }

                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            enabled = !isGenerating,
                            placeholder = { Text("Message...") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )

                        if (isGenerating) {
                            IconButton(onClick = { activeJob?.cancel(); activeJob = null }) { IconStop(MaterialTheme.colorScheme.error) }
                        } else {
                            val canSend = (inputText.isNotBlank() || selectedImageFiles.isNotEmpty())
                            IconButton(
                                enabled = canSend,
                                onClick = {
                                    sendMessageManual(
                                        scope, conversation, assistantTools, messages,
                                        inputText, selectedImageFiles.toList(), selectedImageUris.toList(),
                                        onComplete = {
                                            inputText = ""; selectedImageFiles.clear(); selectedImageUris.clear()
                                        },
                                        onJobFinished = { activeJob = null },
                                        onJobStarted = { activeJob = it }
                                    )
                                }
                            ) {
                                Icon(painterResource(android.R.drawable.ic_menu_send), contentDescription = "Send", tint = if (canSend) MaterialTheme.colorScheme.primary else Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isInitializing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(messages) { msg -> ChatBubble(msg) }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        val bubbleColor = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
        val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
        val shape = if (message.isUser) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)

        Surface(
            color = bubbleColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(if (message.imageUris.isNotEmpty()) 4.dp else 12.dp)) {
                if (message.imageUris.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        message.imageUris.forEach { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "User attached image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = textColor,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(
                            horizontal = if (message.imageUris.isNotEmpty()) 8.dp else 0.dp,
                            vertical = if (message.imageUris.isNotEmpty()) 8.dp else 0.dp
                        )
                    )
                }
            }
        }
    }
}

private fun sendMessageManual(
    scope: kotlinx.coroutines.CoroutineScope,
    conversation: Conversation?,
    assistantTools: AssistantToolSet,
    messages: MutableList<ChatMessage>,
    text: String,
    imageFiles: List<File>,
    imageUris: List<Uri>,
    onComplete: () -> Unit,
    onJobFinished: () -> Unit,
    onJobStarted: (kotlinx.coroutines.Job) -> Unit
) {
    messages.add(ChatMessage(
        text = text,
        isUser = true,
        imageUris = imageUris
    ))

    val aiMessageIndex = messages.size
    messages.add(ChatMessage("", false))

    val job = scope.launch {
        val initialParts = mutableListOf<Content>()
        imageFiles.forEach { initialParts.add(Content.ImageFile(it.absolutePath)) }
        if (text.isNotBlank()) initialParts.add(Content.Text(text))

        var nextInput: Any = Contents.of(initialParts)
        var isLooping = true

        while (isLooping) {
            var fullResponseText = ""
            var displayedText = ""
            var tagBuffer = ""
            var insideTag = false
            var insideFunctionBlock = false

            val stream = if (nextInput is Contents) conversation?.sendMessageAsync(nextInput)
            else conversation?.sendMessageAsync(nextInput as Message)

            if (stream == null) break

            stream.catch { e ->
                messages[aiMessageIndex] = ChatMessage("Error: ${e.message}", false)
                isLooping = false
            }.collect { chunk ->
                val chunkText = chunk.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                fullResponseText += chunkText

                for (char in chunkText) {
                    if (char == '<') { insideTag = true; tagBuffer = "<" }
                    else if (insideTag) {
                        tagBuffer += char
                        if (char == '>') {
                            insideTag = false
                            if (tagBuffer.contains("start_function_call")) insideFunctionBlock = true
                            else if (tagBuffer.contains("end_function_call")) insideFunctionBlock = false
                            tagBuffer = ""
                        }
                    } else if (!insideFunctionBlock) {
                        displayedText += char
                    }
                }
                if (displayedText.isNotBlank()) messages[aiMessageIndex] = ChatMessage(displayedText, false)
            }

            val toolRegex = Pattern.compile("<start_function_call>(.*?)<end_function_call>", Pattern.DOTALL)
            val matcher = toolRegex.matcher(fullResponseText)

            if (matcher.find()) {
                val callBody = matcher.group(1)?.trim() ?: ""
                val toolResult = processManualCallBody(callBody, assistantTools)
                withContext(Dispatchers.Main) { messages[aiMessageIndex] = ChatMessage("Thinking...", false) }
                nextInput = Message.tool(Contents.of(listOf(Content.ToolResponse("manual_action", toolResult))))
            } else {
                isLooping = false
            }
        }
        onJobFinished()
    }
    onJobStarted(job)
    onComplete()
}

private fun processManualCallBody(body: String, tools: AssistantToolSet): String {
    return try {
        val name = body.substringBefore("(").trim()
        val argsString = body.substringAfter("(").substringBeforeLast(")")
        val argsMap = mutableMapOf<String, String>()
        if (argsString.isNotBlank()) {
            argsString.split(",").forEach { pair ->
                val parts = pair.split("=")
                if (parts.size == 2) {
                    argsMap[parts[0].trim()] = parts[1].trim().removeSurrounding("\"").removeSurrounding("'")
                }
            }
        }
        when (name) {
            "get_local_current_date_time" -> tools.getLocalCurrentDateTime()
            "get_list_of_apps" -> tools.getListOfApps()
            "open_app" -> tools.openApp(argsMap["packageId"] ?: "")
            "send_message" -> tools.sendMessage(argsMap["recipient"] ?: "", argsMap["message"] ?: "")
            "make_phone_call" -> tools.makePhoneCall(argsMap["recipient"] ?: "")
            "get_weather" -> tools.getWeather(argsMap["latitude"]?.toDoubleOrNull() ?: 0.0, argsMap["longitude"]?.toDoubleOrNull() ?: 0.0)
            else -> "Error: Unknown tool $name"
        }
    } catch (e: Exception) { "Error: ${e.message}" }
}

private fun copyUriToFile(context: Context, uri: Uri): File {
    val tempFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
    }
    return tempFile
}