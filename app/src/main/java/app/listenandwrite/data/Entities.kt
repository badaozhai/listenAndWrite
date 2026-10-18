package app.listenandwrite.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.StudyGuide

@Entity(tableName = "word_lists")
data class WordListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(
            entity = WordListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("listId")],
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val text: String,
    val lang: String,
    val position: Int,
    val meaning: String = "",
    val contrast: String = "",
    val focus: String = "",
    val unitName: String = "",
)

fun studiedWord(listId: Long, text: String, lang: Lang, position: Int, unitName: String = ""): WordEntity {
    val hit = StudyGuide.lookup(text, lang)
    return WordEntity(
        listId = listId,
        text = text,
        lang = lang.name,
        position = position,
        meaning = hit?.meaning.orEmpty(),
        contrast = hit?.contrast.orEmpty(),
        focus = hit?.focus.orEmpty(),
        unitName = unitName.trim(),
    )
}

@Entity(
    tableName = "mistakes",
    indices = [Index(value = ["lang", "text"], unique = true)],
)
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val lang: String,
    val meaning: String = "",
    val contrast: String = "",
    val focus: String = "",
    val wrongCount: Int = 1,
    val updatedAt: Long = 0,
)

data class ListCount(
    val listId: Long,
    val cnt: Int,
)
