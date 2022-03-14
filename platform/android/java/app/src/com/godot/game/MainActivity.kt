package com.godot.game

import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.annotation.RequiresApi
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

class MainActivity : AppCompatActivity() {
	@RequiresApi(Build.VERSION_CODES.O)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		val textInputUrl = findViewById<EditText>(R.id.editTextUrl)
		val textInputMD5 = findViewById<EditText>(R.id.editTextMD5)


		findViewById<Button>(R.id.buttonPreset1).setOnClickListener {
			textInputUrl.setText("http://192.168.178.78:8080/main.1.com.godot.game.obb")
			textInputMD5.setText("737f01eec5bc1dba925077bdbcd93296")
		}
		findViewById<Button>(R.id.buttonPreset2).setOnClickListener {
			textInputUrl.setText("http://192.168.178.78:8080/main.1.org.godotengine.coloreater.obb")
			textInputMD5.setText("194c8259ddab782a0d6ce36d9ff520e1")
		}

		val activity = this
		findViewById<Button>(R.id.buttonTest).setOnClickListener {
			val fileName = "test.txt"
			val dir = activity.getExternalFilesDir(null)!!.absolutePath
			val file = File("$dir/$fileName")
			file.createNewFile()
			Log.d("HannoDebug", "File exists? ${"$dir/$fileName"} ${file.exists()}")
			file.bufferedReader().useLines { lines ->
				val res = lines.fold("initial") { some, text ->
					"Reading file: $some\n$text"
				}
				Log.d("HannoDebug", "Read file $res")
			}

		}

		findViewById<Button>(R.id.buttonLoad).setOnClickListener {
			val parts = textInputUrl.text.split("/")
			val fileName = parts.last()
			val dir = activity.getExternalFilesDir(null)!!.absolutePath
			val fullPath = "$dir/$fileName"

			Thread {
				Log.d("HannoDebug", "Downloading file to… $fullPath")
				URL(textInputUrl.text.toString()).openStream().use {
					val path = Paths.get(fullPath)
					if (path.exists())
						Files.copy(it, path, StandardCopyOption.REPLACE_EXISTING)
					else
						Files.copy(it, path)

					Log.d("HannoDebug", "Downloaded file!")

					ParameterInstance.URI = fullPath
					ParameterInstance.md5 = textInputMD5.text.toString()
					runOnUiThread {
						activity.startActivity(Intent(activity, GodotApp::class.java))
					}
				}
			}.start()
		}
	}
}
