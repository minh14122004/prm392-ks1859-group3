package com.fptu.prm392.he180148.collaborativeteammanagement.repository

import android.content.Context
import android.util.Log
import com.example.trelloclonemaster3.database.BoardDao
import com.example.trelloclonemaster3.database.BoardEntity
import com.example.trelloclonemaster3.database.TrelloDatabase
import com.example.trelloclonemaster3.model.Board
import com.example.trelloclonemaster3.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*

class BoardRepository(context: Context) {

    private val boardDao: BoardDao = TrelloDatabase.getDatabase(context).boardDao()
    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    // Sync public boards from Firestore to local database
    suspend fun syncPublicBoardsFromFirestore(): List<Board> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(Constants.DEBUG_TAG, "Repository: Starting sync from Firestore...")

                // Get data from Firestore
                val firestoreBoards = getPublicBoardsFromFirestore()

                Log.d(
                    Constants.DEBUG_TAG,
                    "Repository: Got ${firestoreBoards.size} boards from Firestore"
                )

                // Convert to entities and save to local database
                val boardEntities = firestoreBoards.map { board ->
                    BoardEntity.fromBoard(board)
                }

                // Clear old public boards and insert new ones
                if (boardEntities.isNotEmpty()) {
                    clearPublicBoards()
                    boardDao.insertBoards(boardEntities)
                    Log.d(
                        Constants.DEBUG_TAG,
                        "Repository: Saved ${boardEntities.size} boards to local database"
                    )
                }

                firestoreBoards
            } catch (e: Exception) {
                Log.e(Constants.DEBUG_TAG, "Repository: Error syncing from Firestore", e)
                // Return data from local database as fallback
                getPublicBoardsFromLocal()
            }
        }
    }

    // Get public boards from Firestore
    private suspend fun getPublicBoardsFromFirestore(): List<Board> {
        return withContext(Dispatchers.IO) {
            try {
                val querySnapshot = firestore.collection(Constants.BOARDS)
                    .whereEqualTo("isPublic", true)
                    .get()
                    .await()

                val boards = mutableListOf<Board>()
                for (document in querySnapshot.documents) {
                    try {
                        val board = document.toObject(Board::class.java)
                        if (board != null) {
                            board.documentId = document.id
                            boards.add(board)
                        }
                    } catch (e: Exception) {
                        Log.e(Constants.DEBUG_TAG, "Error parsing board: ${document.id}", e)
                    }
                }
                boards
            } catch (e: Exception) {
                Log.e(Constants.DEBUG_TAG, "Error getting boards from Firestore", e)
                emptyList()
            }
        }
    }

    // Get public boards from local database (suspend function)
    private suspend fun getPublicBoardsFromLocal(): List<Board> {
        return withContext(Dispatchers.IO) {
            try {
                // Since we can't use Flow easily, we'll query directly
                // This is a simplified approach for now
                val count = boardDao.getPublicBoardsCount()
                Log.d(
                    Constants.DEBUG_TAG,
                    "Repository: Found $count public boards in local database"
                )

                // For now, return empty list as we focus on Firestore
                // You can implement proper local querying later
                emptyList<Board>()
            } catch (e: Exception) {
                Log.e(Constants.DEBUG_TAG, "Error getting boards from local database", e)
                emptyList()
            }
        }
    }

    // Insert sample public boards for testing
    suspend fun insertSamplePublicBoards(currentUserId: String) {
        withContext(Dispatchers.IO) {
            try {
                val assignedUsers = hashMapOf(currentUserId to "Manager")

                val sampleBoards = listOf(
                    Board(
                        "📱 SQLite Public Project 1",
                        "",
                        currentUserId,
                        assignedUsers,
                        "sqlite1",
                        arrayListOf(),
                        true
                    ),
                    Board(
                        "🗄️ Local Community Board",
                        "",
                        currentUserId,
                        assignedUsers,
                        "sqlite2",
                        arrayListOf(),
                        true
                    ),
                    Board(
                        "💾 Database Demo Project",
                        "",
                        currentUserId,
                        assignedUsers,
                        "sqlite3",
                        arrayListOf(),
                        true
                    ),
                    Board(
                        "🔧 SQLite Test Board",
                        "",
                        currentUserId,
                        assignedUsers,
                        "sqlite4",
                        arrayListOf(),
                        true
                    )
                )

                val entities = sampleBoards.map { BoardEntity.fromBoard(it) }
                boardDao.insertBoards(entities)

                Log.d(
                    Constants.DEBUG_TAG,
                    "Repository: Inserted ${entities.size} sample boards into SQLite"
                )
            } catch (e: Exception) {
                Log.e(Constants.DEBUG_TAG, "Repository: Error inserting sample boards", e)
            }
        }
    }

    // Clear all public boards from local database
    private suspend fun clearPublicBoards() {
        withContext(Dispatchers.IO) {
            try {
                boardDao.clearAllBoards()
                Log.d(Constants.DEBUG_TAG, "Repository: Cleared all boards from local database")
            } catch (e: Exception) {
                Log.e(Constants.DEBUG_TAG, "Repository: Error clearing boards", e)
            }
        }
    }

    // Get count of public boards in local database
    suspend fun getPublicBoardsCount(): Int {
        return withContext(Dispatchers.IO) {
            try {
                val count = boardDao.getPublicBoardsCount()
                Log.d(Constants.DEBUG_TAG, "Repository: Public boards count: $count")
                count
            } catch (e: Exception) {
                Log.e(Constants.DEBUG_TAG, "Repository: Error getting boards count", e)
                0
            }
        }
    }

    // Cleanup
    fun cleanup() {
        scope.cancel()
    }
}

// Extension function to await Firestore tasks
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    return withContext(Dispatchers.IO) {
        while (!isComplete) {
            delay(10)
        }
        if (isSuccessful) {
            result
        } else {
            throw exception ?: Exception("Unknown error")
        }
    }
}