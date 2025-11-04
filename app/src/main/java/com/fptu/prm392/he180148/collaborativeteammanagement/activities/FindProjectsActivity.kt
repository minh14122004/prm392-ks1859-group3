package com.fptu.prm392.he180148.collaborativeteammanagement.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import com.example.trelloclonemaster3.R
import com.example.trelloclonemaster3.adapters.JoinableProjectsAdapter
import com.example.trelloclonemaster3.firebase.FirestoreClass
import com.example.trelloclonemaster3.model.Board
import com.example.trelloclonemaster3.repository.BoardRepository
import com.example.trelloclonemaster3.utils.Constants
import com.example.trelloclonemaster3.utils.DataSyncManager
import com.example.trelloclonemaster3.utils.SyncResult
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlin.collections.forEachIndexed
import kotlin.collections.set
import kotlin.jvm.java
import kotlin.jvm.javaClass
import kotlin.text.contains
import kotlin.text.isEmpty
import kotlin.text.isNotEmpty
import kotlin.text.lowercase
import kotlin.text.trim
import kotlin.to
import kotlin.toString

class FindProjectsActivity : BaseActivity() {

    private lateinit var etSearchProjects: TextInputEditText
    private lateinit var pbLoading: ProgressBar
    private lateinit var rvProjectsList: RecyclerView
    private lateinit var tvNoProjectsAvailable: TextView
    private lateinit var tvNoSearchResults: TextView

    private lateinit var adapter: JoinableProjectsAdapter
    private var allProjects = kotlin.collections.ArrayList<Board>()
    private var filteredProjects = kotlin.collections.ArrayList<Board>()

    // Add Firestore instance for debug functions
    private val mFireStore = FirebaseFirestore.getInstance()

    // Add SQLite Repository and DataSyncManager
    private lateinit var boardRepository: BoardRepository
    private lateinit var dataSyncManager: DataSyncManager

    // For double-click detection
    private var clickCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ADD AUTHENTICATION CHECK FIRST
        val currentUserId = getCurrentUserId()
        Log.d(Constants.DEBUG_TAG, "=== FindProjectsActivity onCreate ===")
        Log.d(Constants.DEBUG_TAG, "User ID: $currentUserId")
        Log.d(Constants.DEBUG_TAG, "Is authenticated: ${currentUserId.isNotEmpty()}")

        if (currentUserId.isEmpty()) {
            Log.e(Constants.DEBUG_TAG, "❌ User not authenticated! Redirecting to SignIn")
            Toast.makeText(this, "Please sign in to continue", Toast.LENGTH_SHORT).show()

            // Redirect to sign in instead of letting app crash
            Activity.startActivity(Intent(this, SignInActivity::class.java))
            Activity.finish()
            return
        }

        setContentView(R.layout.activity_find_projects)

        // Initialize clickCount for double-click detection
        clickCount = 0

        // Initialize repository and sync manager
        boardRepository = BoardRepository(this)
        dataSyncManager = DataSyncManager(this)

        setupActionBar()
        setupUI()
        setupSearchFunctionality()

        // Debug: Log current user and authentication status
        debugFirebaseConnection()

        // Load projects with improved sync
        loadPublicProjectsWithImprovedSync()
    }

    override fun onDestroy() {
        super.onDestroy()
        boardRepository.cleanup()
        dataSyncManager.stopSync()
    }

    private fun setupActionBar() {
        val toolbar =
            findViewById<Toolbar>(R.id.toolbar_find_projects_activity)
        setSupportActionBar(toolbar)

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setHomeAsUpIndicator(R.drawable.ic_white_color_back_24dp)
        }

        toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupUI() {
        etSearchProjects = findViewById(R.id.et_search_projects)
        pbLoading = findViewById(R.id.pb_loading)
        rvProjectsList = findViewById(R.id.rv_projects_list)
        tvNoProjectsAvailable = findViewById(R.id.tv_no_projects_available)
        tvNoSearchResults = findViewById(R.id.tv_no_search_results)

        // Setup RecyclerView
        rvProjectsList.layoutManager = LinearLayoutManager(this)
        rvProjectsList.setHasFixedSize(true)

        adapter = JoinableProjectsAdapter(this, filteredProjects, getCurrentUserId())
        rvProjectsList.adapter = adapter

        adapter.setOnRequestJoinListener(object : JoinableProjectsAdapter.OnRequestJoinListener {
            override fun onRequestJoin(position: Int, board: Board) {
                requestToJoinProject(position, board)
            }
        })
    }

    private fun setupSearchFunctionality() {
        etSearchProjects.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                filterProjects(s.toString())
            }
        })

        // Debug: Long press on search field to create sample SQLite data
        etSearchProjects.setOnLongClickListener {
            Log.d(Constants.DEBUG_TAG, "Long press detected - creating SQLite sample data")
            Toast.makeText(this, "Creating sample boards in SQLite database...", Toast.LENGTH_LONG)
                .show()

            lifecycleScope.launch {
                createSampleSQLiteBoards()
                // Also create a test board in Firestore with proper isPublic field
                createTestFirestoreBoard()
                // Reload after creating sample data
                loadPublicProjectsWithImprovedSync()
            }

            true // Consume the long press event
        }
    }

    private fun filterProjects(query: String) {
        filteredProjects.clear()

        if (query.trim().isEmpty()) {
            filteredProjects.addAll(allProjects)
        } else {
            for (project in allProjects) {
                if (project.name?.lowercase()?.contains(query.lowercase()) == true) {
                    filteredProjects.add(project)
                }
            }
        }

        adapter.notifyDataSetChanged()
        updateUIVisibility()
    }

    // New method: Load public boards using improved DataSyncManager
    private fun loadPublicProjectsWithImprovedSync() {
        Log.d(Constants.DEBUG_TAG, "Loading public projects with DataSyncManager...")
        showProgressBar()

        lifecycleScope.launch {
            try {
                // Initialize database if empty
                dataSyncManager.initializeDatabaseIfEmpty(getCurrentUserId())

                // Sync with retry mechanism
                val syncResult = dataSyncManager.syncPublicBoardsWithRetry()

                when (syncResult) {
                    is SyncResult.Success -> {
                        Log.d(Constants.DEBUG_TAG, "Sync successful: ${syncResult.message}")
                        // Get the actual boards from repository
                        val boards = boardRepository.syncPublicBoardsFromFirestore()
                        Activity.runOnUiThread {
                            populatePublicProjectsList(kotlin.collections.ArrayList(boards))
                        }
                    }

                    is SyncResult.Failed -> {
                        Log.w(Constants.DEBUG_TAG, "Sync failed: ${syncResult.error}")
                        // Use fallback data
                        val boards = boardRepository.syncPublicBoardsFromFirestore()
                        Activity.runOnUiThread {
                            populatePublicProjectsList(kotlin.collections.ArrayList(boards))
                            Toast.makeText(
                                this@FindProjectsActivity,
                                syncResult.fallbackMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(Constants.DEBUG_TAG, "Error in sync process", e)
                Activity.runOnUiThread {
                    hideProgressBar()
                    Toast.makeText(
                        this@FindProjectsActivity,
                        "Error loading projects: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Create sample boards in SQLite for testing
    private suspend fun createSampleSQLiteBoards() {
        try {
            boardRepository.insertSamplePublicBoards(getCurrentUserId())
            Log.d(Constants.DEBUG_TAG, "Sample SQLite boards created successfully")

            Activity.runOnUiThread {
                Toast.makeText(this, "Sample boards created in local database!", Toast.LENGTH_SHORT)
                    .show()
            }
        } catch (e: Exception) {
            Log.e(Constants.DEBUG_TAG, "Error creating sample boards", e)
            Activity.runOnUiThread {
                Toast.makeText(
                    this,
                    "Error creating sample boards: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Keep the original method for Firestore-only loading (as backup)
    private fun loadPublicProjects() {
        Log.d("FindProjects", "Loading public projects from Firestore only...")
        showProgressBar()
        FirestoreClass().getPublicProjects(this)
    }

    // For backwards compatibility, if loadPublicProjectsWithSQLite was referenced
    private fun loadPublicProjectsWithSQLite() {
        lifecycleScope.launch {
            try {
                val boards = boardRepository.syncPublicBoardsFromFirestore()
                Activity.runOnUiThread {
                    populatePublicProjectsList(kotlin.collections.ArrayList(boards))
                }
            } catch (e: Exception) {
                Log.e(Constants.DEBUG_TAG, "Error loading SQLite boards", e)
                Activity.runOnUiThread {
                    hideProgressBar()
                    Toast.makeText(
                        this@FindProjectsActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun requestToJoinProject(position: Int, board: Board) {
        showProgressBar()
        FirestoreClass().requestToJoinProject(this, board, position)
    }

    fun populatePublicProjectsList(projectsList: ArrayList<Board>) {
        hideProgressBar() // Đảm bảo luôn ẩn progress bar

        Log.d("FindProjects", "Received ${projectsList.size} projects")

        allProjects.clear()
        allProjects.addAll(projectsList)

        // Log chi tiết từng project nhận được
        projectsList.forEachIndexed { index, board ->
            Log.d(
                "FindProjects",
                "Project $index: ${board.name}, isPublic: ${board.isPublic}, createdBy: ${board.createdBy}"
            )
        }

        // Reset search và hiển thị tất cả projects
        etSearchProjects.setText("")
        filteredProjects.clear()
        filteredProjects.addAll(allProjects)

        adapter.notifyDataSetChanged()
        updateUIVisibility()

        Log.d("FindProjects", "UI updated with ${filteredProjects.size} projects displayed")
    }

    fun onJoinRequestSuccess(position: Int) {
        hideProgressBar()
        Toast.makeText(this, Context.getString(R.string.request_sent_successfully), Toast.LENGTH_SHORT)
            .show()

        // Update the adapter to reflect the new status
        adapter.updateProjectStatus(position, "Pending")
    }

    fun onJoinRequestFailure() {
        hideProgressBar()
        Toast.makeText(this, Context.getString(R.string.request_failed), Toast.LENGTH_SHORT).show()
    }

    // Debug function to create sample public boards for testing
    private fun createTestPublicBoardsIfNeeded() {
        Log.d(Constants.DEBUG_TAG, "Creating test public boards for debugging...")

        val currentUserId = getCurrentUserId()
        val assignedUsers: HashMap<String, String> = kotlin.collections.HashMap()
        assignedUsers[currentUserId] set "Manager"

        // Create a few test public boards
        val testBoards = listOf(
            Board(
                "Public Project 1",
                "",
                currentUserId,
                assignedUsers,
                "",
                kotlin.collections.ArrayList(),
                true
            ),
            Board(
                "Public Project 2",
                "",
                currentUserId,
                assignedUsers,
                "",
                kotlin.collections.ArrayList(),
                true
            ),
            Board(
                "Test Public Board",
                "",
                currentUserId,
                assignedUsers,
                "",
                kotlin.collections.ArrayList(),
                true
            )
        )

        testBoards.forEachIndexed { index, board ->
            mFireStore.collection(Constants.BOARDS)
                .add(board)
                .addOnSuccessListener { documentReference ->
                    Log.d(
                        Constants.DEBUG_TAG,
                        "Test board $index created with ID: ${documentReference.id}"
                    )
                }
                .addOnFailureListener { e ->
                    Log.e(Constants.DEBUG_TAG, "Failed to create test board $index", e)
                }
        }

        Toast.makeText(this, "Creating test public boards for debugging...", Toast.LENGTH_SHORT)
            .show()
    }

    // Create a test board directly in Firestore to ensure proper field types
    private fun createTestFirestoreBoard() {
        Log.d(Constants.DEBUG_TAG, "Creating test board in Firestore...")

        val currentUserId = getCurrentUserId()
        val assignedUsers = hashMapOf<String, String>()
        assignedUsers[currentUserId] set "Manager"

        val testBoard = hashMapOf(
            "name" to "🧪 TEST PUBLIC BOARD ${System.currentTimeMillis()}",
            "image" to "",
            "createdBy" to currentUserId,
            "assignedTo" to assignedUsers,
            "taskList" to arrayListOf<String>(),
            "isPublic" to true  // Ensure this is saved as boolean true
        )

        mFireStore.collection(Constants.BOARDS)
            .add(testBoard)
            .addOnSuccessListener { documentReference ->
                Log.d(Constants.DEBUG_TAG, "✅ Test board created with ID: ${documentReference.id}")
                Log.d(Constants.DEBUG_TAG, "✅ Board data: $testBoard")
                Toast.makeText(this, "Test board created in Firestore!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(Constants.DEBUG_TAG, "❌ Failed to create test board", e)
                Toast.makeText(
                    this,
                    "Failed to create test board: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showProgressBar() {
        pbLoading.visibility = View.VISIBLE
        rvProjectsList.visibility = View.GONE
        tvNoProjectsAvailable.visibility = View.GONE
        tvNoSearchResults.visibility = View.GONE
    }

    private fun hideProgressBar() {
        pbLoading.visibility = View.GONE
    }

    private fun updateUIVisibility() {
        val searchQuery = etSearchProjects.text.toString().trim()

        when {
            filteredProjects.isEmpty() && allProjects.isEmpty() -> {
                // No projects available at all
                rvProjectsList.visibility = View.GONE
                tvNoProjectsAvailable.visibility = View.VISIBLE
                tvNoSearchResults.visibility = View.GONE

                // Add double-tap to fix missing isPublic fields using flag and handler
                tvNoProjectsAvailable.setOnClickListener { view ->
                    clickCount++
                    val doubleClickDelay = 400L // milliseconds

                    if (clickCount == 1) {
                        // Single click - show hint
                        Toast.makeText(
                            this,
                            "Double-tap to fix missing isPublic fields",
                            Toast.LENGTH_SHORT
                        ).show()
                        view.postDelayed({ clickCount = 0 }, doubleClickDelay)
                    } else if (clickCount == 2) {
                        // Double click - fix the issue
                        Toast.makeText(
                            this,
                            "Fixing missing isPublic fields...",
                            Toast.LENGTH_SHORT
                        ).show()
                        fixMissingIsPublicField()
                        clickCount = 0
                    }
                }
            }

            filteredProjects.isEmpty() && searchQuery.isNotEmpty() -> {
                // No search results
                rvProjectsList.visibility = View.GONE
                tvNoProjectsAvailable.visibility = View.GONE
                tvNoSearchResults.visibility = View.VISIBLE
            }

            else -> {
                // Show projects
                rvProjectsList.visibility = View.VISIBLE
                tvNoProjectsAvailable.visibility = View.GONE
                tvNoSearchResults.visibility = View.GONE
            }
        }
    }

    // --- DEBUGGING FUNCTIONS ---

    private fun debugFirebaseConnection() {
        val currentUser = getCurrentUserId()
        Log.d(Constants.DEBUG_TAG, "=== Debug Firebase Connection ===")
        Log.d(Constants.DEBUG_TAG, "Current User ID: $currentUser")
        Log.d(Constants.DEBUG_TAG, "Is user authenticated: ${currentUser.isNotEmpty()}")

        // Test basic Firestore connectivity
        mFireStore.collection(Constants.BOARDS).limit(1).get()
            .addOnSuccessListener { querySnapshot ->
                Log.d(
                    Constants.DEBUG_TAG,
                    "✅ Firestore connection OK - Found ${querySnapshot.documents.size} documents"
                )

                // Count all boards and debug isPublic field variations
                mFireStore.collection(Constants.BOARDS).get()
                    .addOnSuccessListener { allQuerySnapshot ->
                        val totalBoards = allQuerySnapshot.documents.size
                        var publicBoards = 0

                        Log.d(
                            Constants.DEBUG_TAG,
                            "=== DEBUGGING ALL BOARDS FOR isPublic FIELD ==="
                        )

                        for (document in allQuerySnapshot.documents) {
                            val docId = document.id
                            val name = document.getString("name") ?: "unnamed"

                            // Check all possible isPublic variations - FIX THE TYPE CASTING
                            val isPublicBoolean = document.getBoolean("isPublic")
                            // Remove this line that causes the crash - isPublic is boolean, not string
                            // val isPublicString = document.getString("isPublic") 
                            val isPublicRaw = document.get("isPublic")

                            Log.d(Constants.DEBUG_TAG, "📋 Board: $name (ID: $docId)")
                            Log.d(Constants.DEBUG_TAG, "   - isPublic (Boolean): $isPublicBoolean")
                            Log.d(
                                Constants.DEBUG_TAG,
                                "   - isPublic (Raw Type): ${isPublicRaw?.javaClass?.simpleName}"
                            )
                           // Log.d(Constants.DEBUG_TAG, "   - isPublic (Long): $isPublicLong")
                            Log.d(
                                Constants.DEBUG_TAG,
                                "   - isPublic (Raw Value): $isPublicRaw"
                            )

                            // Count based on boolean value
                            if (isPublicBoolean == true) {
                                publicBoards++
                                Log.d(
                                    Constants.DEBUG_TAG,
                                    "   ✅ This board IS PUBLIC (boolean true)"
                                )
                            } else if (isPublicRaw == "true") {
                                publicBoards++
                                Log.d(
                                    Constants.DEBUG_TAG,
                                    "   ✅ This board IS PUBLIC (string 'true')"
                                )
                            } else if (isPublicRaw == null) {
                                Log.d(Constants.DEBUG_TAG, "   ⚠️ This board has NO isPublic field")
                            } else {
                                Log.d(Constants.DEBUG_TAG, "   ❌ This board is NOT public")
                            }

                            Log.d(Constants.DEBUG_TAG, "   ---")
                        }

                        Log.d(Constants.DEBUG_TAG, "=== SUMMARY ===")
                        Log.d(
                            Constants.DEBUG_TAG,
                            "Summary: $publicBoards public boards out of $totalBoards total"
                        )

                        if (publicBoards == 0) {
                            Log.w(Constants.DEBUG_TAG, "⚠️ NO PUBLIC BOARDS FOUND!")
                            Log.i(
                                Constants.DEBUG_TAG,
                                "💡 Tip: Check if isPublic field exists and has correct data type"
                            )
                        }

                        // Test different query variations
                        testDifferentQueries()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(Constants.DEBUG_TAG, "❌ Firestore connection FAILED", e)
            }
    }

    // Test different query variations to find the right one
    private fun testDifferentQueries() {
        Log.d(Constants.DEBUG_TAG, "=== TESTING DIFFERENT QUERY VARIATIONS ===")

        // Test 1: Boolean true
        mFireStore.collection(Constants.BOARDS)
            .whereEqualTo("isPublic", true)
            .get()
            .addOnSuccessListener { querySnapshot ->
                Log.d(
                    Constants.DEBUG_TAG,
                    "Query 1 (boolean true): Found ${querySnapshot.documents.size} documents"
                )
            }

        // Test 2: String "true"  
        mFireStore.collection(Constants.BOARDS)
            .whereEqualTo("isPublic", "true")
            .get()
            .addOnSuccessListener { querySnapshot ->
                Log.d(
                    Constants.DEBUG_TAG,
                    "Query 2 (string 'true'): Found ${querySnapshot.documents.size} documents"
                )
            }

        // Test 3: Long 1
        mFireStore.collection(Constants.BOARDS)
            .whereEqualTo("isPublic", 1L)
            .get()
            .addOnSuccessListener { querySnapshot ->
                Log.d(
                    Constants.DEBUG_TAG,
                    "Query 3 (long 1): Found ${querySnapshot.documents.size} documents"
                )
            }
    }

    // NEW: Fix missing isPublic field for all existing boards
    private fun fixMissingIsPublicField() {
        Log.d(Constants.DEBUG_TAG, "=== FIXING MISSING isPublic FIELD FOR ALL BOARDS ===")

        mFireStore.collection(Constants.BOARDS).get()
            .addOnSuccessListener { querySnapshot ->
                val batch = mFireStore.batch()
                var updateCount = 0

                for (document in querySnapshot.documents) {
                    val isPublic = document.get("isPublic")

                    if (isPublic == null) {
                        // Add isPublic field with default value false
                        val docRef = mFireStore.collection(Constants.BOARDS).document(document.id)
                        batch.update(docRef, "isPublic", false)
                        updateCount++

                        Log.d(
                            Constants.DEBUG_TAG,
                            "Will update board: ${document.getString("name")} (${document.id}) -> isPublic: false"
                        )
                    }
                }

                if (updateCount > 0) {
                    batch.commit()
                        .addOnSuccessListener {
                            Log.d(
                                Constants.DEBUG_TAG,
                                "✅ Successfully updated $updateCount boards with isPublic field"
                            )
                            Toast.makeText(
                                this@FindProjectsActivity,
                                "Updated $updateCount boards with isPublic field",
                                Toast.LENGTH_LONG
                            ).show()

                            // Reload projects after fixing
                            loadPublicProjectsWithImprovedSync()
                        }
                        .addOnFailureListener { e ->
                            Log.e(Constants.DEBUG_TAG, "❌ Failed to update boards", e)
                            Toast.makeText(
                                this@FindProjectsActivity,
                                "Failed to fix boards: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                } else {
                    Log.d(Constants.DEBUG_TAG, "✅ All boards already have isPublic field")
                    Toast.makeText(
                        this@FindProjectsActivity,
                        "All boards already have isPublic field",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(Constants.DEBUG_TAG, "❌ Failed to get boards for fixing", e)
            }
    }

    private fun debugAllBoards() {
        // Simplified version - just log the query we're going to make
        Log.d(Constants.DEBUG_TAG, "=== About to query public boards ===")
        Log.d(
            Constants.DEBUG_TAG,
            "Query: collection('${Constants.BOARDS}').whereEqualTo('isPublic', true)"
        )
    }
}