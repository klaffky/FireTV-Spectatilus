package de.klaffky.spectatilus.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.klaffky.spectatilus.data.Item
import de.klaffky.spectatilus.data.PublicUser

/** Bildschirme im Rückwärts-Stapel. Jeder hält seinen eigenen Zustand, damit „Zurück“ nichts neu lädt. */
sealed class Screen {
    class Boot : Screen()
    class Connect(val firstRun: Boolean) : Screen()
    class Offline(val message: String) : Screen()
    class Login(val serverName: String) : Screen() {
        var users by mutableStateOf<List<PublicUser>>(emptyList())
        var quickConnect by mutableStateOf(false)
        val focus = FocusMemory()
    }
    class Main : Screen()
    class Details(val itemId: String) : Screen() {
        var item by mutableStateOf<Item?>(null)
        var error by mutableStateOf<String?>(null)
        var seasons by mutableStateOf<List<Item>>(emptyList())
        var selectedSeason by mutableStateOf<String?>(null)
        var episodes by mutableStateOf<List<Item>>(emptyList())
        var nextUp by mutableStateOf<Item?>(null)
        var similar by mutableStateOf<List<Item>>(emptyList())
        val listState = LazyListState()
        val episodeState = LazyListState()
        val seasonState = LazyListState()
        val castState = LazyListState()
        val similarState = LazyListState()
        val focus = FocusMemory()
        var loadedAt = 0L
    }
    class Library(val title: String, parentId: String?, types: String?, recursive: Boolean = true, extra: String = "") : Screen() {
        val state = LibraryState(types, parentId, recursive, extra)
    }
}

class HomeState {
    var loaded by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hero by mutableStateOf<List<Item>>(emptyList())
    var shelves by mutableStateOf<List<Shelf>>(emptyList())
    var libraries by mutableStateOf<List<Item>>(emptyList())
    val listState = LazyListState()
    val rowStates = mutableMapOf<String, LazyListState>()
    val focus = FocusMemory()
    var lastRefresh = 0L
    var heroIndex by mutableStateOf(0)
    var featured by mutableStateOf<Item?>(null)

    fun rowState(key: String) = rowStates.getOrPut(key) { LazyListState() }
}

data class Shelf(val key: String, val title: String, val items: List<Item>, val landscape: Boolean)

class LibraryState(val types: String?, val parentId: String?, val recursive: Boolean = true, val extra: String = "") {
    val items = mutableStateListOf<Item>()
    var total by mutableStateOf(-1)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var sort by mutableStateOf(0)
    var unwatchedOnly by mutableStateOf(false)
    var gridState by mutableStateOf(LazyGridState())
    val focus = FocusMemory()

    fun reset() {
        items.clear()
        total = -1
        error = null
        gridState = LazyGridState()
    }
}

class SearchState {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Item>>(emptyList())
    var searching by mutableStateOf(false)
    val gridState = LazyGridState()
    val focus = FocusMemory()
}

/** Anfrage für die Bildschirmtastatur-Eingabe. */
class TextRequest(
    val title: String,
    val initial: String,
    val password: Boolean = false,
    val hint: String? = null,
    val returnTo: androidx.compose.ui.focus.FocusRequester? = null,
    val onDone: (String) -> Unit,
)

class AppState {
    val stack = mutableStateListOf<Screen>(Screen.Boot())
    var tab by mutableStateOf(0)
    var focusTabs by mutableStateOf(false)
    var textRequest by mutableStateOf<TextRequest?>(null)
    var toast by mutableStateOf<String?>(null)

    var home = HomeState()
    var movies = LibraryState("Movie", null)
    var shows = LibraryState("Series", null)
    var search = SearchState()
    val settingsFocus = FocusMemory()

    fun memoryForTab(i: Int): FocusMemory = when (i) {
        0 -> home.focus
        1 -> movies.focus
        2 -> shows.focus
        3 -> search.focus
        else -> settingsFocus
    }

    val current: Screen get() = stack.last()

    fun push(s: Screen) {
        stack.add(s)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun reset(s: Screen) {
        stack.clear()
        stack.add(s)
    }

    /** Nach Login/Server-Wechsel alle Inhalte verwerfen. */
    fun clearContent() {
        home = HomeState()
        movies = LibraryState("Movie", null)
        shows = LibraryState("Series", null)
        search = SearchState()
        tab = 0
    }

    fun ask(request: TextRequest) {
        textRequest = request
    }
}
