package com.salihturkoglu.artbookkotlin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PathPermission
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.salihturkoglu.artbookkotlin.databinding.ActivityArtBinding
import java.io.ByteArrayOutputStream
import java.sql.SQLNonTransientException
import java.util.jar.Manifest

class ArtActivity : AppCompatActivity() {
    private lateinit var binding: ActivityArtBinding
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>//galeriden değer döndermek için kullanıcaz
    private lateinit var permissionLauncher: ActivityResultLauncher<String>//izin için kullanıcaz
    var selectedBitmap : Bitmap? = null
    private lateinit var database : SQLiteDatabase


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArtBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        database = this.openOrCreateDatabase("Arts", Context.MODE_PRIVATE,null)


        registerLauncher()

        val intent = intent

        val info = intent.getStringExtra("info")

        if (info.equals("new")) {
            binding.artNameText.setText("")
            binding.artistNameText.setText("")
            binding.yearText.setText("")
            binding.saveButton.visibility = View.VISIBLE

            val selectedImageBackground = BitmapFactory.decodeResource(applicationContext.resources,R.drawable.selecticon)
            binding.imageView.setImageBitmap(selectedImageBackground)

        } else {
            binding.saveButton.visibility = View.INVISIBLE
            val selectedId = intent.getIntExtra("id",1)

            val cursor = database.rawQuery("SELECT * FROM arts WHERE id = ?", arrayOf(selectedId.toString()))

            val artNameIx = cursor.getColumnIndex("artname")
            val artistNameIx = cursor.getColumnIndex("artistname")
            val yearIx = cursor.getColumnIndex("year")
            val imageIx = cursor.getColumnIndex("image")

            while (cursor.moveToNext()) {
                binding.artNameText.setText(cursor.getString(artNameIx))
                binding.artistNameText.setText(cursor.getString(artistNameIx))
                binding.yearText.setText(cursor.getString(yearIx))

                val byteArray = cursor.getBlob(imageIx)
                val bitmap = BitmapFactory.decodeByteArray(byteArray,0,byteArray.size)
                binding.imageView.setImageBitmap(bitmap)

            }

            cursor.close()

        }


    }
    fun saveButton(view: View){
        val artname = binding.artNameText.text.toString()
        val artistname = binding.artistNameText.text.toString()
        val year = binding.yearText.text.toString()

        if(selectedBitmap!=null){
            val smallBitmap=makeSmallerBitmap(selectedBitmap!!,300)

            val outputStream= ByteArrayOutputStream()
            smallBitmap.compress(Bitmap.CompressFormat.PNG,50,outputStream)
            val byteArray=outputStream.toByteArray()

            try {
                val database = this.openOrCreateDatabase("Arts", MODE_PRIVATE,null)
                database.execSQL("CREATE TABLE IF NOT EXISTS arts(id INTEGER PRIMARY KEY,artname VARCHAR,artistname VARCHAR,year VARCHAR,image BLOB)")
                val sqlString= "INSERT INTO arts (artname, artistname, year, image) VALUES (?, ?, ?, ?)"
                val statement = database.compileStatement(sqlString)
                statement.bindString(1,artname)
                statement.bindString(2,artistname)
                statement.bindString(3,year)
                statement.bindBlob(4,byteArray)
                statement.execute()


            }catch (e:Exception){
                e.printStackTrace()
            }
            val intent= Intent(this@ArtActivity,MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)//bundan önceki tüm activityleri kapatır
            startActivity(intent)


        }


    }

    private fun makeSmallerBitmap(image:Bitmap,maxSize:Int):Bitmap{
        var width = image.width
        var height = image.height

        val bitmapRatio : Double = width.toDouble() / height.toDouble()
        if (bitmapRatio > 1) {
            width = maxSize
            val scaledHeight = width / bitmapRatio
            height = scaledHeight.toInt()
        } else {
            height = maxSize
            val scaledWidth = height * bitmapRatio
            width = scaledWidth.toInt()
        }
        return Bitmap.createScaledBitmap(image,width,height,true)    }
    fun selectedImage(view: View){
        //galeri izini varmı diye bakıcak
        if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.READ_EXTERNAL_STORAGE) !=  PackageManager.PERMISSION_GRANTED){
            //izin yok ise --> izin tekrar isticez
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,android.Manifest.permission.READ_EXTERNAL_STORAGE)){
                //izin istemek için snackbar kullanıcaz çünkü tıklama yapılıcak
                Snackbar.make(view,"Galeriye Gitmek İçin İzin Ver",Snackbar.LENGTH_INDEFINITE).setAction("İzin Ver",View.OnClickListener {
                    //request permission
                    permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)

                }).show()
            }else{
                //izin verirse burası çalışıcak
                //request permission
                permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }


        }else{
            //izin verilmiş ise buraya giricek
            val intentToGallery=Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            activityResultLauncher.launch(intentToGallery)

            //intent

        }

    }
    private fun registerLauncher(){
        activityResultLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
            //result değerinde galeriden dönen data var
            if (result.resultCode== RESULT_OK){
                val intentFromResult=result.data
                //gelen data null mu diye kontrol ediyoruz çünkü naneble
                if(intentFromResult!=null){
                    val imageData = intentFromResult.data//uri değeri aldık
                    //binding.imageView.setImageURI(imageData) bitmape dönüştürüp küçültüp kaydedicez
                    if (imageData!=null){
                        try {
                            if(Build.VERSION.SDK_INT >=28){
                                val source= ImageDecoder.createSource(this@ArtActivity.contentResolver,imageData)
                                selectedBitmap = ImageDecoder.decodeBitmap(source)
                                binding.imageView.setImageBitmap(selectedBitmap)
                            }else{
                                selectedBitmap=MediaStore.Images.Media.getBitmap(contentResolver,imageData)//28den aşağı sürümleri için kullanma şekli
                                binding.imageView.setImageBitmap(selectedBitmap)
                            }




                        }catch (e:Exception){
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        //izin isteme
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){ result->//boolean
            if (result){
                //izin verildi ise (permison granted)
                val intentToGallery=Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                activityResultLauncher.launch(intentToGallery)
            }else{
                //izin verilmedi ise (permison denied)
                Toast.makeText(this@ArtActivity,"İzin vermeniz gerek",Toast.LENGTH_LONG).show()

            }

        }




    }
}