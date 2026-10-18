package app.listenandwrite.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DictationDao {
    @Query("SELECT * FROM word_lists ORDER BY updatedAt DESC")
    fun observeLists(): Flow<List<WordListEntity>>

    @Query("SELECT * FROM word_lists")
    suspend fun allLists(): List<WordListEntity>

    @Query("SELECT * FROM word_lists WHERE subject = :subject ORDER BY id ASC LIMIT 1")
    suspend fun findSubjectList(subject: String): WordListEntity?

    @Query("SELECT listId, COUNT(*) AS cnt FROM words GROUP BY listId")
    fun observeCounts(): Flow<List<ListCount>>

    @Query("SELECT * FROM word_lists WHERE id = :id")
    fun observeList(id: Long): Flow<WordListEntity?>

    @Query("SELECT * FROM words WHERE listId = :listId ORDER BY position ASC, id ASC")
    fun observeWords(listId: Long): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE listId = :listId ORDER BY position ASC, id ASC")
    suspend fun words(listId: Long): List<WordEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM words WHERE listId = :listId")
    suspend fun maxPosition(listId: Long): Int

    @Insert
    suspend fun insertList(list: WordListEntity): Long

    @Query("UPDATE word_lists SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, title: String, now: Long)

    @Query("DELETE FROM word_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Insert
    suspend fun insertWords(words: List<WordEntity>): List<Long>

    @Query(
        "UPDATE words SET text = :text, lang = :lang, meaning = :meaning, contrast = :contrast, focus = :focus, unitName = :unitName WHERE id = :id",
    )
    suspend fun updateWord(
        id: Long,
        text: String,
        lang: String,
        meaning: String,
        contrast: String,
        focus: String,
        unitName: String,
    )

    @Query("SELECT * FROM mistakes WHERE lang = :lang ORDER BY updatedAt DESC")
    fun observeMistakes(lang: String): Flow<List<MistakeEntity>>

    @Query("SELECT COUNT(*) FROM mistakes WHERE lang = :lang")
    fun observeMistakeCount(lang: String): Flow<Int>

    @Query(
        "SELECT * FROM mistakes WHERE lang = :lang AND CASE WHEN lang = 'EN' THEN lower(text) ELSE text END = :key LIMIT 1",
    )
    suspend fun mistake(lang: String, key: String): MistakeEntity?

    @Insert
    suspend fun insertMistake(item: MistakeEntity): Long

    @Query("UPDATE mistakes SET wrongCount = wrongCount + 1, updatedAt = :now WHERE id = :id")
    suspend fun bumpMistake(id: Long, now: Long)

    @Query("UPDATE mistakes SET wrongCount = :count, updatedAt = :now WHERE id = :id")
    suspend fun setMistakeCount(id: Long, count: Int, now: Long)

    @Query("DELETE FROM mistakes WHERE id = :id")
    suspend fun deleteMistake(id: Long)

    @Query(
        "UPDATE mistakes SET text = :text, meaning = :meaning, contrast = :contrast, focus = :focus, updatedAt = :now WHERE id = :id",
    )
    suspend fun updateMistake(id: Long, text: String, meaning: String, contrast: String, focus: String, now: Long)

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun deleteWord(id: Long)

    @Query("UPDATE word_lists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE words SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("UPDATE words SET listId = :listId, unitName = :unitName WHERE id = :id")
    suspend fun moveWord(id: Long, listId: Long, unitName: String)

    @Transaction
    suspend fun reorder(listId: Long, orderedIds: List<Long>) {
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id -> updatePosition(id, index) }
        touch(listId, now)
    }
}
