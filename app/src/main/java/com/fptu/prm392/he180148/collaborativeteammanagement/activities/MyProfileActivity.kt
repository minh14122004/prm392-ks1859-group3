package com.fptu.prm392.he180148.collaborativeteammanagement.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.trelloclonemaster3.R
import com.example.trelloclonemaster3.firebase.FirestoreClass
import com.example.trelloclonemaster3.model.User
import com.example.trelloclonemaster3.utils.Constants
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.IOException
import android.os.Build
import androidx.appcompat.widget.Toolbar
import kotlin.collections.isNotEmpty
import kotlin.collections.set
import kotlin.isInitialized
import kotlin.let
import kotlin.text.isEmpty
import kotlin.text.isNotEmpty
import kotlin.text.toLong
import kotlin.toString

@Suppress("DEPRECATION")
class MyProfileActivity : BaseActivity() {


    private var mStorageImageUri : Uri? = null
    private lateinit var mUserDetails: User
    private var mProfileUserImage : String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_profile)

        setupActionBar()

        FirestoreClass().loadUserData(this)

        val userImage = findViewById<ImageView>(R.id.iv_profile_user_image)
        userImage.setOnClickListener {
            // Kiểm tra phiên bản Android để sử dụng quyền phù hợp
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 (API 33) trở lên
                Manifest.permission.READ_MEDIA_IMAGES
            } else { // Android 12 (API 32) trở xuống
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                // Quyền đã được cấp, mở trình chọn ảnh
                Constants.showImagePicker(this)
            } else {
                // Quyền chưa được cấp, yêu cầu quyền từ người dùng
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permission), // Yêu cầu quyền phù hợp với phiên bản Android
                    Constants.READ_STORAGE_PERMISSION_CODE
                )
            }
        }

        val btnUpdate = findViewById<Button>(R.id.btn_update_my_profile)
        btnUpdate.setOnClickListener {
            showCustomProgressBar()
            if(mStorageImageUri != null){
                uploadImageOnStorage()
            }else{
                updateUserProfileData()
            }
        }
    }

    private fun setupActionBar(){
        val toolbar = findViewById<Toolbar>(R.id.toolbar_my_profile_activity)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == Constants.READ_STORAGE_PERMISSION_CODE ){
            if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Constants.showImagePicker(this)
            }else{
                Toast.makeText(this,"Permission is required to change image",Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == Activity.RESULT_OK && requestCode == Constants.PICK_IMAGE_REQUEST_CODE && data!!.data != null){
            mStorageImageUri = data.data

            try {
                val userImage = findViewById<ImageView>(R.id.iv_profile_user_image)
                Glide
                        .with(this)
                        .load(mStorageImageUri)
                        .centerCrop()
                        .placeholder(R.drawable.ic_user_place_holder)
                        .into(userImage)
            }catch(e: IOException){
                e.printStackTrace()
                Log.e("message","Failed")
                Toast.makeText(this,"Something Went Wrong Please try again later",Toast.LENGTH_SHORT).show()
            }
        }
    }


    fun setUserDataInUi(user: User){

        mUserDetails = user

        val userImage = findViewById<ImageView>(R.id.iv_profile_user_image)
        val etName = findViewById<TextView>(R.id.et_name_my_profile)
        val etMail = findViewById<TextView>(R.id.et_email)
        val etMobile = findViewById<TextView>(R.id.et_mobile_my_profile)

        Log.e("user Image Url: ", user.image.toString())

        Glide
            .with(this@MyProfileActivity)
            .load(user.image)
            .centerCrop()
            .placeholder(R.drawable.ic_user_place_holder)
            .into(userImage)

        etName.text = user.name
        etMail.text = user.email
        if(user.mobile != 0L){
            etMobile.text = user.mobile.toString()
        }
    }

    private fun uploadImageOnStorage(){

        if(mStorageImageUri != null) {
            val sRef: StorageReference = FirebaseStorage.getInstance().reference.child(
                "USER_NAME" + System.currentTimeMillis() + "." + Constants.getFileExtension(this,mStorageImageUri)
            )

            sRef.putFile(mStorageImageUri!!).addOnSuccessListener {
                taskSnapshot ->
                Toast.makeText(this, "Image Saved Successfully", Toast.LENGTH_SHORT).show()
                taskSnapshot.metadata!!.reference!!.downloadUrl.addOnSuccessListener {
                    uri ->
                    Log.e("Image Uri", uri.toString())
                    mProfileUserImage = uri.toString()
                    updateUserProfileData()
                }
            }.removeOnFailureListener {
                exception ->
                hideCustomProgressDialog()
                Toast.makeText(this,"Couldn't saveImage try again later ",Toast.LENGTH_SHORT).show()
            }
        }
        hideCustomProgressDialog()
    }



    private fun updateUserProfileData(){
        // Lấy tham chiếu đến TextView một cách an toàn hơn
        val etName = findViewById<TextView>(R.id.et_name_my_profile)
        val etMobile = findViewById<TextView>(R.id.et_mobile_my_profile)

        // Kiểm tra xem mUserDetails đã được khởi tạo chưa
        // Đây là điều kiện tiên quyết để update profile
        if (!::mUserDetails.isInitialized) {
            hideCustomProgressDialog() // Hàm ẩn progress bar của bạn
            Toast.makeText(this, "Thông tin người dùng chưa được tải. Vui lòng thử lại.", Toast.LENGTH_LONG).show()
            return
        }

        val userHashMap = kotlin.collections.HashMap<String, Any>()

        // Sử dụng .let để xử lý mProfileUserImage một cách an toàn
        mProfileUserImage?.let { image ->
            // Đoạn code này chỉ chạy nếu mProfileUserImage không null.
            // Bên trong này, 'image' là một biến non-nullable (String).
            if (image.isNotEmpty() && image != mUserDetails.image) {
                Log.e("Message", "Image Worked")
                userHashMap[Constants.IMAGE] set image
            }
        }


        // Lấy dữ liệu từ EditText một cách an toàn và so sánh với dữ liệu cũ
        val nameFromEditText = etName?.text?.toString() ?: "" // Sử dụng ?. và Elvis operator để tránh NPE
        if(nameFromEditText != mUserDetails.name){
            Log.e("Message","Name Text Worked")
            userHashMap[Constants.NAME] set nameFromEditText
        }

        // Lấy dữ liệu từ Mobile EditText một cách an toàn
        val mobileFromEditText = etMobile?.text?.toString() ?: "" // Sử dụng ?. và Elvis operator
        if (mobileFromEditText.isNotEmpty() && mobileFromEditText != mUserDetails.mobile.toString()){
            Log.e("Message","Mobile Text Worked")
            try {
                userHashMap[Constants.MOBILE] set mobileFromEditText.toLong()
            } catch (e: NumberFormatException) {
                hideCustomProgressDialog()
                Toast.makeText(this, "Số điện thoại không hợp lệ. Vui lòng nhập số hợp lệ.", Toast.LENGTH_LONG).show()
                return // Dừng lại nếu số điện thoại không hợp lệ
            }
        }
        // Trường hợp người dùng xóa số điện thoại
        else if (mobileFromEditText.isEmpty() && mUserDetails.mobile != 0L) {
            userHashMap[Constants.MOBILE] set 0L // Gán 0L nếu người dùng xóa số điện thoại
        }


        // Chỉ gọi update lên Firestore nếu có thay đổi
        if(userHashMap.isNotEmpty()){
            FirestoreClass().updateUserProfileData(this@MyProfileActivity,userHashMap)
        } else {
            hideCustomProgressDialog()
            Toast.makeText(this, "Không có thay đổi nào để cập nhật.", Toast.LENGTH_SHORT).show()
            Activity.setResult(Activity.RESULT_OK) // Có thể coi là thành công dù không có thay đổi
            Activity.finish()
        }
    }

    fun profileUpdateSuccess(){
        hideCustomProgressDialog()

        Activity.setResult(Activity.RESULT_OK)
        Activity.finish()
    }
}