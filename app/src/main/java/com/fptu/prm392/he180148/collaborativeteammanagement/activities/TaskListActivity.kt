package com.fptu.prm392.he180148.collaborativeteammanagement.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.trelloclonemaster3.R
import com.example.trelloclonemaster3.adapters.TaskListItemAdapter
import com.example.trelloclonemaster3.firebase.FirestoreClass
import com.example.trelloclonemaster3.model.Board
import com.example.trelloclonemaster3.model.Card
import com.example.trelloclonemaster3.model.Tasks
import com.example.trelloclonemaster3.model.TaskStatus
import com.example.trelloclonemaster3.model.User
import com.example.trelloclonemaster3.utils.Constants
import kotlin.collections.forEachIndexed
import kotlin.jvm.java
import kotlin.text.contains
import kotlin.text.lowercase

@Suppress("DEPRECATION")
class TaskListActivity : BaseActivity() {

    internal lateinit var mBoardDetails: Board
    private lateinit var mDocumentId: String

    lateinit var mMembersDetailList: ArrayList<User>

    companion object{
        const val MEMBERS_REQUEST_CODE: Int = 13
        const val CARD_DETAILS_REQUEST_CODE = 14
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list)

        if(Activity.getIntent.hasExtra(Constants.DOCUMENT_ID)){
            mDocumentId = Activity.getIntent.getStringExtra(Constants.DOCUMENT_ID)!!
        }

        showCustomProgressBar()
        FirestoreClass().getBoardDetails(this,mDocumentId)
    }

    private fun setupActionBar(){
        val toolbar = findViewById<Toolbar>(R.id.toolbar_task_list_activity)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.title = mBoardDetails.name
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_members,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.action_members -> {
                val intent = Intent(this,MembersActivity::class.java)
                intent.putExtra(Constants.BOARD_DETAILS,mBoardDetails)
                startActivityForResult(intent, MEMBERS_REQUEST_CODE)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == MEMBERS_REQUEST_CODE || requestCode == CARD_DETAILS_REQUEST_CODE){
            showCustomProgressBar()
            FirestoreClass().getBoardDetails(this,mDocumentId)
        }else{
            Log.e("Members","Canceled")
        }
    }

    fun boardDetails(board: Board){

        mBoardDetails = board

        hideCustomProgressDialog()
        setupActionBar()

        showCustomProgressBar()
        FirestoreClass().getAssignedMembersList(this,mBoardDetails.assignedTo.keys)
    }

    fun addUpdateTaskLIstSuccess(){
        hideCustomProgressDialog()

        showCustomProgressBar()
        FirestoreClass().getBoardDetails(this, mBoardDetails.documentId!!)
    }

    // NEW: Check if current user has write permissions
    private fun hasWritePermission(): Boolean {
        val currentUserId = FirestoreClass().getCurrentUserID()
        val userStatus = mBoardDetails.assignedTo[currentUserId]

        // Only allow write access for Members and Managers, not for Pending users
        return userStatus == "Member" || userStatus == "Manager"
    }

    // NEW: Show permission denied message
    private fun showPermissionDeniedMessage() {
        Toast.makeText(
            this,
            "You don't have permission to modify this project. Your join request is still pending approval.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun createTaskList(taskListName: String) {
        // FIXED: Check permissions before allowing task list creation
        if (!hasWritePermission()) {
            showPermissionDeniedMessage()
            return
        }

        val task = Tasks(taskListName, FirestoreClass().getCurrentUserID())
        mBoardDetails.taskList.add(0,task)
        mBoardDetails.taskList.removeAt(mBoardDetails.taskList.size-1)

        showCustomProgressBar()
        FirestoreClass().addUpdateTaskList(this,mBoardDetails)
    }

    fun updateTaskList(position: Int, listName: String, model: Tasks){
        // FIXED: Check permissions before allowing task list updates
        if (!hasWritePermission()) {
            showPermissionDeniedMessage()
            return
        }

        val task = Tasks(listName,model.createdBy)

        mBoardDetails.taskList[position] = task
        mBoardDetails.taskList.removeAt(mBoardDetails.taskList.size-1)

        showCustomProgressBar()
        FirestoreClass().addUpdateTaskList(this,mBoardDetails)
    }

    fun deleteTaskList(position: Int){
        // FIXED: Check permissions before allowing task list deletion
        if (!hasWritePermission()) {
            showPermissionDeniedMessage()
            return
        }

        mBoardDetails.taskList.removeAt(position)
        mBoardDetails.taskList.removeAt(mBoardDetails.taskList.size-1)

        showCustomProgressBar()
        FirestoreClass().addUpdateTaskList(this,mBoardDetails)
    }

    fun addCardToArrayList(position: Int,cardName: String){
        // FIXED: Check permissions before allowing card creation
        if (!hasWritePermission()) {
            showPermissionDeniedMessage()
            return
        }

        mBoardDetails.taskList.removeAt(mBoardDetails.taskList.size-1)

        val cardAssignedUserList: ArrayList<String> = kotlin.collections.ArrayList()
        cardAssignedUserList.add(FirestoreClass().getCurrentUserID())

        val card = Card(
            name = cardName,
            createdBy = FirestoreClass().getCurrentUserID(),
            assignedTo = cardAssignedUserList,
            labelColor = "",
            dueDate = 0L
        )

        val cardList = mBoardDetails.taskList[position].cards
        cardList.add(card)

        val task = Tasks(
                mBoardDetails.taskList[position].title,
                mBoardDetails.taskList[position].createdBy,
                cardList
        )

        mBoardDetails.taskList[position] = task

        showCustomProgressBar()
        FirestoreClass().addUpdateTaskList(this,mBoardDetails)
    }

    fun cardDetails(taskListPosition: Int,cardListPosition: Int){
        val intent = Intent(this,CardDetailsActivity::class.java)
        intent.putExtra(Constants.BOARD_DETAILS,mBoardDetails)
        intent.putExtra(Constants.TASK_LIST_ITEM_POSITION,taskListPosition)
        intent.putExtra(Constants.CARD_LIST_ITEM_POSITION,cardListPosition)
        intent.putExtra(Constants.BOARDS_MEMBERS_LIST,mMembersDetailList)
        startActivityForResult(intent, CARD_DETAILS_REQUEST_CODE)
    }

    fun membersDetailList(list: ArrayList<User>){
        mMembersDetailList = list

        hideCustomProgressDialog()

        val addTaskList = Tasks(resources.getString(R.string.add_list))
        mBoardDetails.taskList.add(addTaskList)

        val rvTaskList = findViewById<RecyclerView>(R.id.rv_task_list)
        rvTaskList.layoutManager = LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL,false)
        rvTaskList.setHasFixedSize(true)

        val adapter =  TaskListItemAdapter(this,mBoardDetails.taskList)
        rvTaskList.adapter = adapter
    }

    fun updateCardsInTaskList(taskListPosition: Int, cards: ArrayList<Card>){
        // FIXED: Check permissions before allowing card updates
        if (!hasWritePermission()) {
            showPermissionDeniedMessage()
            return
        }

        mBoardDetails.taskList.removeAt(mBoardDetails.taskList.size-1)
        mBoardDetails.taskList[taskListPosition].cards = cards

        showCustomProgressBar()
        FirestoreClass().addUpdateTaskList(this,mBoardDetails)
    }

    // Debug method to log current board state
    fun logBoardState(context: String) {
        Log.d("BoardState", "=== BOARD STATE ($context) ===")
        Log.d("BoardState", "Total task lists: ${mBoardDetails.taskList.size}")
        mBoardDetails.taskList.forEachIndexed { index, taskList ->
            if (index < mBoardDetails.taskList.size - 1) { // Skip "Add List"
                Log.d(
                    "BoardState",
                    "Column $index: '${taskList.title}' has ${taskList.cards.size} cards"
                )
                taskList.cards.forEachIndexed { cardIndex, card ->
                    Log.d("BoardState", "  Card $cardIndex: '${card.name}' (${card.status})")
                }
            }
        }
        Log.d("BoardState", "=== END BOARD STATE ===")
    }

    // New method for moving cards between task lists
    fun moveCardBetweenLists(
        fromListPosition: Int,
        toListPosition: Int, 
        cardPosition: Int,
        targetPosition: Int
    ) {
        // FIXED: Check permissions before allowing card moves
        if (!hasWritePermission()) {
            showPermissionDeniedMessage()
            return
        }

        val card = mBoardDetails.taskList[fromListPosition].cards.removeAt(cardPosition)
        
        // Update card status based on target column
        val updatedCard = updateCardStatusBasedOnColumn(card, toListPosition)
        
        mBoardDetails.taskList[toListPosition].cards.add(targetPosition, updatedCard)
        
        // Remove the "Add List" item before updating
        mBoardDetails.taskList.removeAt(mBoardDetails.taskList.size-1)
        
        showCustomProgressBar()
        FirestoreClass().addUpdateTaskList(this, mBoardDetails)
    }
    
    private fun updateCardStatusBasedOnColumn(card: Card, columnPosition: Int): Card {
        val taskListTitle = mBoardDetails.taskList[columnPosition].title?.lowercase() ?: ""
        
        val newStatus = when {
            taskListTitle.contains("pending") || taskListTitle.contains("to do") || taskListTitle.contains("cần làm") -> TaskStatus.PENDING
            taskListTitle.contains("progress") || taskListTitle.contains("đang tiến hành") || taskListTitle.contains("doing") -> TaskStatus.IN_PROGRESS
            taskListTitle.contains("completed") || taskListTitle.contains("done") || taskListTitle.contains("hoàn thành") -> TaskStatus.COMPLETED
            taskListTitle.contains("review") || taskListTitle.contains("đang xem xét") -> TaskStatus.IN_PROGRESS // Treat review as in progress
            else -> card.status // Keep original status if column doesn't match known patterns
        }
        
        return Card(
            name = card.name,
            createdBy = card.createdBy,
            assignedTo = card.assignedTo,
            labelColor = card.labelColor,
            dueDate = card.dueDate,
            status = newStatus
        )
    }

    // Method for context menu based card movement
    fun moveCardToColumn(fromListPosition: Int, cardPosition: Int, toListPosition: Int) {
        // FIXED: Check permissions before allowing card moves
        if (!hasWritePermission()) {
            showPermissionDeniedMessage()
            return
        }

        try {
            if (fromListPosition == toListPosition) {
                Log.d("TaskListActivity", "Same column, no move needed")
                return
            }

            // Kiểm tra vị trí hợp lệ
            if (fromListPosition < 0 || fromListPosition >= mBoardDetails.taskList.size - 1 ||
                toListPosition < 0 || toListPosition >= mBoardDetails.taskList.size - 1 ||
                cardPosition < 0 || cardPosition >= mBoardDetails.taskList[fromListPosition].cards.size
            ) {
                Log.e(
                    "TaskListActivity",
                    "Invalid positions: from=$fromListPosition, to=$toListPosition, card=$cardPosition"
                )
                return
            }

            Log.d(
                "TaskListActivity",
                "Moving card from column $fromListPosition to $toListPosition"
            )

            // Lấy card từ cột nguồn
            val card = mBoardDetails.taskList[fromListPosition].cards.removeAt(cardPosition)

            // Cập nhật status dựa trên cột đích
            val updatedCard = updateCardStatusBasedOnColumn(card, toListPosition)

            // Thêm card vào cột đích (ở cuối danh sách)
            mBoardDetails.taskList[toListPosition].cards.add(updatedCard)

            // Loại bỏ item "Add List" trước khi cập nhật
            mBoardDetails.taskList.removeAt(mBoardDetails.taskList.size - 1)

            Log.d("TaskListActivity", "Card moved successfully, updating Firestore...")
            showCustomProgressBar()
            FirestoreClass().addUpdateTaskList(this, mBoardDetails)

        } catch (e: Exception) {
            Log.e("TaskListActivity", "Error moving card: ${e.message}", e)
            hideCustomProgressDialog()
        }
    }

    // Method for drag and drop between columns
    fun moveCardBetweenColumns(
        fromColumn: Int,
        toColumn: Int,
        cardPosition: Int,
        targetPosition: Int
    ) {
        // FIXED: Check permissions before allowing card drag and drop
        if (!hasWritePermission()) {
            showPermissionDeniedMessage()
            return
        }

        try {
            Log.d("TaskListActivity", "=== MOVE CARD BETWEEN COLUMNS ===")
            Log.d(
                "TaskListActivity",
                "From: $fromColumn, To: $toColumn, CardPos: $cardPosition, TargetPos: $targetPosition"
            )

            // Log board state before move
            logBoardState("BEFORE MOVE")

            if (fromColumn == toColumn) {
                Log.d("TaskListActivity", "Same column drag, no cross-column move needed")
                return
            }

            // Kiểm tra vị trí hợp lệ
            if (fromColumn < 0 || fromColumn >= mBoardDetails.taskList.size - 1 ||
                toColumn < 0 || toColumn >= mBoardDetails.taskList.size - 1 ||
                cardPosition < 0 || cardPosition >= mBoardDetails.taskList[fromColumn].cards.size
            ) {
                Log.e(
                    "TaskListActivity",
                    "Invalid drag positions: from=$fromColumn, to=$toColumn, card=$cardPosition"
                )
                Log.e("TaskListActivity", "Available columns: ${mBoardDetails.taskList.size - 1}")
                Log.e(
                    "TaskListActivity",
                    "Cards in source column: ${mBoardDetails.taskList[fromColumn].cards.size}"
                )
                logBoardState("INVALID POSITIONS")
                return
            }

            // Lấy card từ cột nguồn
            val card = mBoardDetails.taskList[fromColumn].cards.removeAt(cardPosition)
            Log.d(
                "TaskListActivity",
                "Moved card: '${card.name}' from column '${mBoardDetails.taskList[fromColumn].title}'"
            )

            // Cập nhật status dựa trên cột đích
            val updatedCard = updateCardStatusBasedOnColumn(card, toColumn)
            Log.d(
                "TaskListActivity",
                "Updated card status to: ${updatedCard.status} for column '${mBoardDetails.taskList[toColumn].title}'"
            )

            // Thêm card vào cột đích tại vị trí chỉ định
            val safeTargetPosition =
                if (targetPosition > mBoardDetails.taskList[toColumn].cards.size) {
                    mBoardDetails.taskList[toColumn].cards.size
                } else {
                    targetPosition
                }
            mBoardDetails.taskList[toColumn].cards.add(safeTargetPosition, updatedCard)
            Log.d(
                "TaskListActivity",
                "Added card to column '${mBoardDetails.taskList[toColumn].title}' at position $safeTargetPosition"
            )

            // Log board state after move
            logBoardState("AFTER MOVE")

            // Loại bỏ item "Add List" trước khi cập nhật
            mBoardDetails.taskList.removeAt(mBoardDetails.taskList.size - 1)

            Log.d("TaskListActivity", "Card drag moved successfully, updating Firestore...")
            showCustomProgressBar()
            FirestoreClass().addUpdateTaskList(this, mBoardDetails)

        } catch (e: Exception) {
            Log.e("TaskListActivity", "Error drag moving card: ${e.message}", e)
            hideCustomProgressDialog()
            logBoardState("ERROR STATE")
        }
    }
}