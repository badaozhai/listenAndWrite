package app.listenandwrite

import android.app.Application
import androidx.room.Room
import app.listenandwrite.data.AppDatabase
import app.listenandwrite.data.SettingsStore
import app.listenandwrite.data.WordEntity
import app.listenandwrite.domain.EnglishCue
import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.ParsedWord
import app.listenandwrite.domain.PlayMode

class ListenApp : Application() {
    lateinit var database: AppDatabase
        private set

    lateinit var settings: SettingsStore
        private set

    val draft = DraftHolder()
    val practice = PracticeHolder()

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(this, AppDatabase::class.java, "listen.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
        settings = SettingsStore(this)
    }
}

data class DraftWord(
    val text: String,
    val lang: Lang,
    val checked: Boolean = true,
)

class DraftHolder {
    var listId: Long = 0
    var original: List<DraftWord> = emptyList()
        private set
    var lines: List<DraftWord> = emptyList()

    var unitName: String = ""

    fun set(listId: Long, parsed: List<ParsedWord>, unitName: String = "") {
        this.listId = listId
        this.unitName = unitName.trim()
        original = parsed.map { DraftWord(it.text, it.lang, checked = true) }
        lines = original
    }
}

class PracticeHolder {
    var listId: Long = 0
    var title: String = ""
    var mode: PlayMode = PlayMode.TIMER
    var words: List<WordEntity> = emptyList()
    var backRoute: String = "subjects"

    /** 本次播放的英语报法。空则用设置里的报法。默写练习传报中文，不改词表设置。 */
    var englishCue: EnglishCue? = null

    /** 默写不在播放时露出答案。认读和语文仍可按住查看。 */
    var hideAnswer: Boolean = false

    fun set(
        listId: Long,
        title: String,
        mode: PlayMode,
        words: List<WordEntity>,
        backRoute: String = "",
        englishCue: EnglishCue? = null,
        hideAnswer: Boolean = false,
    ) {
        this.listId = listId
        this.title = title
        this.mode = mode
        this.words = words
        this.englishCue = englishCue
        this.hideAnswer = hideAnswer
        if (backRoute.isNotEmpty()) this.backRoute = backRoute
    }
}
