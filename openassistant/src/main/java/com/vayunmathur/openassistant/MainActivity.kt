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
import com.vayunmathur.openassistant.data.database.MessageDatabase
import com.vayunmathur.openassistant.ui.ConversationListScreen
import com.vayunmathur.openassistant.ui.ConversationScreen
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.codeshipping.llamakotlin.LlamaModel
import java.io.File


class LLamaAPI(context: Context) {
    var model: LlamaModel?

    init {
        runBlocking {
            model = LlamaModel.load(File(context.getExternalFilesDir(null), "phi3.gguf").absolutePath)
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
                    Triple("https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf", "phi3.gguf", "model weights")
                )) {
                    LaunchedEffect(Unit) {
                        LLamaAPI.getInstance(this@MainActivity)
                    }
                    Navigation(viewModel, ds)
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
fun Navigation(viewModel: DatabaseViewModel, ds: DataStoreUtils) {
    val backStack = rememberNavBackStack(Route.ConversationList, Route.Conversation(0))
    MainNavigation(backStack) {
        entry<Route.ConversationList>(metadata = ListPage{
            ConversationScreen(backStack, viewModel, ds, 0)
        }) { ConversationListScreen(backStack, viewModel) }
        entry<Route.Conversation>(metadata = ListDetailPage()) { ConversationScreen(backStack, viewModel, ds, it.conversationID) }
    }
}