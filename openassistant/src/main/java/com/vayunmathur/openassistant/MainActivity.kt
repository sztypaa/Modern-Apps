package com.vayunmathur.openassistant

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavKey
import com.vayunmathur.library.ui.DynamicTheme
import com.vayunmathur.library.ui.InitialDownloadChecker
import com.vayunmathur.library.util.DataStoreUtils
import com.vayunmathur.library.util.DatabaseViewModel
import com.vayunmathur.library.util.IntentLauncher
import com.vayunmathur.library.util.ListDetailPage
import com.vayunmathur.library.util.ListPage
import com.vayunmathur.library.util.MainNavigation
import com.vayunmathur.library.util.buildDatabase
import com.vayunmathur.library.util.rememberNavBackStack
import com.vayunmathur.openassistant.data.Conversation
import com.vayunmathur.openassistant.data.Message
import com.vayunmathur.openassistant.data.ToolCall
import com.vayunmathur.openassistant.data.database.MessageDatabase
import com.vayunmathur.openassistant.ui.ConversationListScreen
import com.vayunmathur.openassistant.ui.ConversationScreen
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import org.pytorch.executorch.extension.llm.LlmModuleConfig
import java.io.File

class LLamaAPI(context: Context) {

    enum class LLMState {
        READY,
        THINKING,
        GENERATING
    }

    val llmModule = LlmModule(File(context.getExternalFilesDir(null)!!, "model.pte").absolutePath, File(context.getExternalFilesDir(null)!!, "tokenizer.json").absolutePath, 0.8f)

    fun run(content: String) = callbackFlow {
        _isAvailable.value = LLMState.THINKING
        llmModule.generate(content, 32768, object : LlmCallback {
            override fun onResult(result: String) {
                _isAvailable.value = LLMState.GENERATING
                trySend(result)
                if(result.contains("<|im_end|>")) {
                    llmModule.stop()
                }
            }
            override fun onStats(stats: String) {
                println(stats)
                close()
            }
        }, false)
        awaitClose {
            _isAvailable.value = LLMState.READY
        }
    }

    private var _isAvailable = MutableStateFlow(LLMState.READY)
    val isAvailable: StateFlow<LLMState> = _isAvailable

    val toolCallRegex = Regex("""<tool_call>\s*(.*?)\s*</tool_call>""", RegexOption.DOT_MATCHES_ALL)

    fun getResponse(content: String, initialAssistantMessage: Message): Flow<Message> {
        var updatedMessage = initialAssistantMessage
        var fullResponse = ""
        return run(content).map { chunk ->
            fullResponse += chunk

            // Extract all current tool calls from the accumulating response
            val matches = toolCallRegex.findAll(fullResponse)
            val toolCalls = matches.mapNotNull { match ->
                val jsonString = match.groupValues[1]
                runCatching {
                    Json.decodeFromString<ToolCall>(jsonString)
                }.getOrNull()
            }.toList()

            // Update message with parsed tool calls and clean text
            updatedMessage = updatedMessage.copy(
                textContent = fullResponse,
                toolCalls = toolCalls // Assuming your Message class has this field
            )
            updatedMessage
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: LLamaAPI? = null

        fun getInstance(context: Context): LLamaAPI {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LLamaAPI(context).also { INSTANCE = it }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = buildDatabase<MessageDatabase>()
        val viewModel = DatabaseViewModel(db, Message::class to db.messageDao(), Conversation::class to db.conversationDao())
        val ds = DataStoreUtils.getInstance(this)
        intentLauncher = IntentLauncher(this)

        setContent {
            DynamicTheme {
                InitialDownloadChecker(ds, listOf(
                    Triple("https://data.vayunmathur.com/ai/model.pte", "model.pte", "Model"),
                    Triple("https://data.vayunmathur.com/ai/tokenizer.json", "tokenizer.json", "Tokenizer")
                )) {
                    LaunchedEffect(Unit) {
                        LLamaAPI.getInstance(this@MainActivity)
                    }
                    Navigation(viewModel)
                }
            }
        }
    }
}

lateinit var intentLauncher: IntentLauncher

@Serializable
sealed interface Route: NavKey {
    @Serializable
    data class Conversation(val conversationID: Long) : Route
    @Serializable
    data object ConversationList : Route
}

@Composable
fun Navigation(viewModel: DatabaseViewModel) {
    val backStack = rememberNavBackStack(Route.ConversationList, Route.Conversation(0))
    MainNavigation(backStack) {
        entry<Route.ConversationList>(metadata = ListPage{
            ConversationScreen(backStack, viewModel, 0)
        }) { ConversationListScreen(backStack, viewModel) }
        entry<Route.Conversation>(metadata = ListDetailPage()) { ConversationScreen(backStack, viewModel, it.conversationID) }
    }
}