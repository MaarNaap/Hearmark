package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyItemDao {
    @Query("SELECT * FROM vocabulary_items ORDER BY createdAt DESC")
    fun getAllVocabularyItemsFlow(): Flow<List<VocabularyItem>>

    @Query("SELECT * FROM vocabulary_items ORDER BY createdAt DESC")
    suspend fun getAllVocabularyItemsDirect(): List<VocabularyItem>

    @Query("SELECT * FROM vocabulary_items WHERE id = :id LIMIT 1")
    suspend fun getVocabularyItemById(id: Long): VocabularyItem?

    @Query("SELECT * FROM vocabulary_items WHERE targetWord = :targetWord LIMIT 1")
    suspend fun getVocabularyItemByWord(targetWord: String): VocabularyItem?

    @Query("SELECT * FROM vocabulary_items WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun getVocabularyItemsForNoteFlow(noteId: Long): Flow<List<VocabularyItem>>

    @Query("SELECT * FROM vocabulary_items WHERE trackId = :trackId ORDER BY createdAt DESC")
    fun getVocabularyItemsForTrackFlow(trackId: Long): Flow<List<VocabularyItem>>

    @Query("SELECT * FROM vocabulary_items WHERE isMastered = 1 ORDER BY lastReviewedAt DESC")
    fun getMasteredVocabularyItemsFlow(): Flow<List<VocabularyItem>>

    @Query("SELECT * FROM vocabulary_items WHERE isMastered = 0 ORDER BY createdAt DESC")
    fun getUnmasteredVocabularyItemsFlow(): Flow<List<VocabularyItem>>

    @Query("SELECT * FROM vocabulary_items WHERE targetWord LIKE '%' || :query || '%' OR meaning LIKE '%' || :query || '%' OR contextSentence LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchVocabularyItemsFlow(query: String): Flow<List<VocabularyItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabularyItem(item: VocabularyItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabularyItems(items: List<VocabularyItem>): List<Long>

    @Update
    suspend fun updateVocabularyItem(item: VocabularyItem)

    @Delete
    suspend fun deleteVocabularyItem(item: VocabularyItem)

    @Query("DELETE FROM vocabulary_items WHERE id = :id")
    suspend fun deleteVocabularyItemById(id: Long)

    @Query("DELETE FROM vocabulary_items WHERE noteId = :noteId")
    suspend fun deleteVocabularyItemsForNote(noteId: Long)

    @Query("UPDATE vocabulary_items SET timesReviewed = timesReviewed + 1, timesCorrect = timesCorrect + :correctDelta, isMastered = CASE WHEN (timesCorrect + :correctDelta) >= 3 THEN 1 ELSE isMastered END, lastReviewedAt = :timestamp WHERE id = :id")
    suspend fun recordReview(id: Long, correctDelta: Int, timestamp: Long = System.currentTimeMillis())
}
