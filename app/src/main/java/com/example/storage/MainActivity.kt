package com.example.storage

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.room.*
import com.example.storage.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val TEXT_TO_SAVE_KEY = "TEXT_TO_SAVE_KEY"
        private const val FIRST_INFO = 0
    }

    @Entity(tableName = "InfoTable")
    data class Info(
            @PrimaryKey(autoGenerate = true)
            val id: Int = 0,
            val text: String
    )

    data class OnlyText(
            val text: String
    )

    @Dao
    abstract class InfoDao {
        @Insert
        abstract fun insertInfo(info: Info)

        @Query("SELECT text FROM INFOTABLE")
        abstract fun getInfo(): List<OnlyText>

        @Query("DELETE FROM INFOTABLE")
        abstract fun deleteAll()

        @Query("SELECT COUNT(*) FROM INFOTABLE")
        abstract fun getCountLine(): Int

    }

    @Database(entities = [Info::class], version = 1)
    abstract class InfoDatabase : RoomDatabase() {
        abstract fun getInfoDao(): InfoDao
    }

    @ExperimentalStdlibApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        var prefs: SharedPreferences = getPreferences(Context.MODE_PRIVATE)
        val file = File(filesDir, "InternalSave.txt")


        val db = Room.databaseBuilder(applicationContext, InfoDatabase::class.java, "Info")
                .fallbackToDestructiveMigration()
                .build()

        thread {
            db.getInfoDao().deleteAll()
        }
        prefs.edit().clear()

        binding.clearButton.setOnClickListener {
            binding.textToSave.text.clear()
        }

        binding.prefsSave.setOnClickListener {
            prefs.edit().apply {
                putString(TEXT_TO_SAVE_KEY, binding.textToSave.text.toString())
                apply()
            }
        }

        binding.prefsLoad.setOnClickListener {
            val loadText = prefs.getString(TEXT_TO_SAVE_KEY, "Ooops :( Prefs are empty")
            binding.textToSave.setText("Load - $loadText")
        }

        binding.internalSave.setOnClickListener {
            try {
                val output = file.outputStream()
                output.write(binding.textToSave.text.toString().toByteArray())
                output.close()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        binding.internalLoad.setOnClickListener {
            try {
                val input = file.inputStream()
                val loadText = input.readBytes().decodeToString()
                binding.textToSave.setText("Load - $loadText")
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        binding.externalSave.setOnClickListener {
            startExternalSave()
        }

        binding.externalLoad.setOnClickListener {
            startExternalLoad()
        }

        binding.dbSave.setOnClickListener {
            thread {
                db.getInfoDao().insertInfo(Info(text = binding.textToSave.text.toString()))
            }
        }

        binding.dbLoad.setOnClickListener {
            thread {
                if (db.getInfoDao().getCountLine() > 0) {
                    val loadText = db.getInfoDao().getInfo()[FIRST_INFO].text
                    binding.textToSave.setText("Load - $loadText")
                    db.getInfoDao().deleteAll()
                } else {
                    this.runOnUiThread(Runnable {
                        binding.textToSave.setText("DB is empty")
                    })
                }
            }
        }
    }

    private fun checkPermission(permission: String): CheckPermissionResult {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> CheckPermissionResult.GRANTED

            ContextCompat.checkSelfPermission(
                    this,
                    permission
            ) == PackageManager.PERMISSION_GRANTED -> CheckPermissionResult.GRANTED

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(
                    permission
            ) -> CheckPermissionResult.NEED_TO_EXPLAIN
            else -> CheckPermissionResult.NEED_TO_REQUEST
        }
    }

    @ExperimentalStdlibApi
    private fun handleCheckResult(permission: String, result: CheckPermissionResult) {
        when (result) {
            CheckPermissionResult.GRANTED -> if (permission == WRITE_EXTERNAL_STORAGE) {
                startSaveText()
            } else startLoadText()

            CheckPermissionResult.DENIED -> failedGracefully()
            CheckPermissionResult.NEED_TO_REQUEST -> askForPermission(permission)
            CheckPermissionResult.NEED_TO_EXPLAIN -> showRationale()
        }
    }

    private fun showRationale() {
        AlertDialog.Builder(this)
                .setTitle("Permission")
                .setMessage("Permission is needed to allow this feature work")
                .setPositiveButton("I understand") { _, _ -> askForPermission(WRITE_EXTERNAL_STORAGE) }
                .show()
    }

    private fun failedGracefully() {
        AlertDialog.Builder(this)
                .setTitle("Permission")
                .setMessage("Permission was not granted. We respect your decision")
                .setNegativeButton("I changed my mind") { _, _ -> askForPermission(WRITE_EXTERNAL_STORAGE) }
                .setPositiveButton("Ok", null)
                .show()
    }

    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    startSaveText()
                } else {
                    failedGracefully()
                }
            }

    private fun askForPermission(permission: String) {
        requestPermissionLauncher.launch(permission)
    }

    private fun startSaveText() {
        val file = File(getExternalFilesDir("."), "ExternalSave.txt")
        try {
            val output = FileOutputStream(file)
            output.write(binding.textToSave.text.toString().toByteArray())
            output.close()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @ExperimentalStdlibApi
    private fun startLoadText() {
        val file = File(getExternalFilesDir("."), "ExternalSave.txt")
        try {
            val input = file.inputStream()
            val loadText = input.readBytes().decodeToString()
            binding.textToSave.setText("Load - $loadText")
            file.delete()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @ExperimentalStdlibApi
    private fun startExternalSave() {
        val permission = WRITE_EXTERNAL_STORAGE
        handleCheckResult(permission, checkPermission(permission))
    }

    @ExperimentalStdlibApi
    private fun startExternalLoad() {
        val permission = READ_EXTERNAL_STORAGE
        handleCheckResult(permission, checkPermission(permission))
    }

    enum class CheckPermissionResult {
        GRANTED,
        DENIED,
        NEED_TO_REQUEST,
        NEED_TO_EXPLAIN
    }
}