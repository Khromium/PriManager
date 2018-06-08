package work.airz.primanager

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import kotlinx.android.synthetic.main.activity_save_coord_ticket.*
import work.airz.primanager.qr.QRUtil
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SaveCoordTicket : AppCompatActivity(), View.OnClickListener {
    private lateinit var coordList: HashMap<String, CoordDetail>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_save_coord_ticket)
        save.setOnClickListener(this)
        destruction.setOnClickListener(this)
        continuation.setOnClickListener(this)
        get_data.setOnClickListener(this)
        display_qr.setOnClickListener(this)

        coordList = getPrichanCoordData()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.save -> {
                saveData()
                finish()
            }
            R.id.destruction -> {
                finish()
            }
            R.id.continuation -> {
                saveData()
                startActivity(Intent(this, QRActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP })
                finish()
            }
            R.id.get_data -> {
                id.setText(id.text.toString().toUpperCase())
                val detail = coordList[id.text.toString()]
                if (detail != null) {
                    setImage(detail.imageUrl)
                    name.setText(detail.name)
                    rarity.setText(detail.rarity)
                    color.setText(detail.color)
                    category.setText(detail.category)
                    genre.setText(detail.genre)
                    like.setText(detail.like)
                    brand.setText(detail.brand)
                    arcade_series.setText("プリ☆チャン")

                }
            }
        }
    }

    private fun setImage(url: String) {
        object : MyAsyncTask() {
            override fun doInBackground(vararg params: Void): Bitmap? {
                val url = URL(url)
                val urlConnection = url.openConnection()  as? HttpURLConnection ?: return null
                urlConnection.readTimeout = 5000
                urlConnection.connectTimeout = 7000
                urlConnection.requestMethod = "GET"
                urlConnection.doInput = true
                urlConnection.connect()
                return BitmapFactory.decodeStream(urlConnection.inputStream)
            }

            override fun onPostExecute(result: Bitmap?) {
                super.onPostExecute(result)
                result ?: return
                thumbnail.setImageBitmap(result)
            }
        }.execute()
    }

    fun saveData() {

    }

    private fun getPrichanCoordData(): HashMap<String, CoordDetail> {
        val coordHash = HashMap<String, CoordDetail>()
        resources.openRawResource(R.raw.prichan).bufferedReader().use {
            it.readLines().withIndex().forEach {
                if (it.index == 0) return@forEach
                coordHash[it.value.split(",")[0]] = CoordDetail(it.value)
            }
        }
        return coordHash
    }

    private class CoordDetail(val csvdata: String) {
        val name: String
        val imageUrl: String
        val category: String
        val color: String
        val brand: String
        val genre: String
        val rarity: String
        val like: String
        val note: String

        init {
            val splitData = csvdata.split(",")
            name = splitData[1]
            imageUrl = splitData[2]
            category = splitData[3]
            color = splitData[4]
            brand = splitData[5]
            genre = splitData[6]
            rarity = splitData[7]
            like = splitData[8]
            note = splitData[9]
        }
    }

    private open class MyAsyncTask : AsyncTask<Void, Void, Bitmap>() {
        override fun doInBackground(vararg params: Void): Bitmap? {
            return null
        }

        override fun onPostExecute(result: Bitmap?) {
            super.onPostExecute(result)
            result ?: return
        }
    }

}
