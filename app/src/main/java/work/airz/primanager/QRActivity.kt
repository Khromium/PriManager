package work.airz.primanager

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultMetadataType
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.android.synthetic.main.activity_qr.*
import work.airz.primanager.db.DBConstants
import work.airz.primanager.db.DBFormat.User
import work.airz.primanager.db.DBUtil
import work.airz.primanager.qr.QRUtil


class QRActivity : AppCompatActivity() {
    private lateinit var qrReaderView: DecoratedBarcodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr)

        readQR()
    }

    /**
     * QRコードの読み取り部分の処理
     * 読み取って詳細データまで取得する
     */
    private fun readQR() {
        qrReaderView = decoratedBarcodeView
        qrReaderView.decodeContinuous(object : BarcodeCallback {
            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {

            }

            override fun barcodeResult(result: BarcodeResult?) {
                if (result == null || result.barcodeFormat != BarcodeFormat.QR_CODE) return

                val bytes = result.resultMetadata[ResultMetadataType.BYTE_SEGMENTS] as? List<*>
                val data = bytes?.get(0) as? ByteArray ?: return
                val strb = StringBuilder()
                data.forEach { strb.append(String.format("%02X ", it)) }
                Log.i("QR DUMP", strb.toString())

                Log.d("maskIndex", result.result.maskIndex.toString())
                Log.d("QRのサイズ", result.rawBytes.size.toString())

                analyzeQR(data, result)
            }
        })
        qrReaderView.resume()
    }

    fun analyzeQR(data: ByteArray, result: BarcodeResult) {
        qrReaderView.pause()

        val dbUtil = DBUtil(applicationContext)
        val rawString = QRUtil.byteToString(data)

        val qrFormat = QRUtil.QRFormat(QRUtil.QRFormat.getStringToErrorCorrectionLevel(result.resultMetadata[ResultMetadataType.ERROR_CORRECTION_LEVEL] as String),
                result.result.maskIndex,
                result.sourceData.isInverted, QRUtil.detectVersionM(result.rawBytes.size))
        Log.d("qrformat", qrFormat.toString())

        var ticketType: QRUtil.TicketType = intent.getSerializableExtra(QRUtil.TICKET_TYPE) as? QRUtil.TicketType
                ?: QRUtil.detectQRFormat(data)

        when (ticketType) {
            QRUtil.TicketType.PRICHAN_FOLLOW -> {
                val followUserID = QRUtil.getFollowUserID(data)
                val followedUsers = dbUtil.getUserList().filter { dbUtil.isFollowed(it, followUserID) }
                val isDuplicate = dbUtil.isDuplicate(DBConstants.FOLLOW_TICKET_TABLE, rawString)
                when {
                    followedUsers.isNotEmpty() -> followedAlert(data, qrFormat, followedUsers, QRUtil.TicketType.PRICHAN_FOLLOW, isDuplicate)
                    isDuplicate -> duplicateDataAlert(data, qrFormat, QRUtil.TicketType.PRICHAN_FOLLOW)
                    else -> saveAlert(data, qrFormat, QRUtil.TicketType.PRICHAN_FOLLOW)
                }
            }
            QRUtil.TicketType.PRICHAN_COORD -> {
                when {
                    dbUtil.isDuplicate(DBConstants.COORD_TICKET_TABLE, rawString) -> duplicateDataAlert(data, qrFormat, QRUtil.TicketType.PRICHAN_COORD)
                    else -> saveAlert(data, qrFormat, QRUtil.TicketType.PRICHAN_COORD)
                }

            }
            QRUtil.TicketType.PRICHAN_MEMBERS -> {
                when {
                    dbUtil.isDuplicate(DBConstants.USER_TABLE, rawString) -> duplicateDataAlert(data, qrFormat, QRUtil.TicketType.PRICHAN_MEMBERS)
                    else -> saveAlert(data, qrFormat, QRUtil.TicketType.PRICHAN_MEMBERS)
                }

            }
            QRUtil.TicketType.OTHERS -> {
                //基本的にプリパラのトモチケは来ない前提で考える
                when {
                    dbUtil.isDuplicate(DBConstants.COORD_TICKET_TABLE, rawString) -> duplicateDataAlert(data, qrFormat, QRUtil.TicketType.OTHERS)
                    else -> othersDataAlert(data, qrFormat, QRUtil.TicketType.OTHERS)//謎データであることを告知する

                }

            }
        }

    }

    /**
     * 新データ保存のときのアラート
     */
    private fun saveAlert(rawData: ByteArray, qrFormat: QRUtil.QRFormat, type: QRUtil.TicketType) {
        AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.new_data))
            setCancelable(false)
            setMessage("${getString(R.string.not_saved_data)}${getString(R.string.question_save)}")
            setPositiveButton(getString(R.string.yes)) { _, _ ->
                intentAdapter(rawData, qrFormat, type, false)
            }
            setNegativeButton(getString(R.string.no)) { dialog, _ ->
                dialog.dismiss()
                qrReaderView.resume()
            }
        }.show()
    }

    /**
     * 謎データが来たときのアラート
     */
    private fun othersDataAlert(rawData: ByteArray, qrFormat: QRUtil.QRFormat, type: QRUtil.TicketType) {
        AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.unknown_data))
            setCancelable(false)
            setMessage("${getString(R.string.jump_to_coord)}${getString(R.string.question_comfirm)}")
            setPositiveButton(getString(R.string.yes)) { _, _ ->
                intentAdapter(rawData, qrFormat, type, false)
            }
            setNegativeButton(getString(R.string.no)) { dialog, _ ->
                dialog.dismiss()
                qrReaderView.resume()
            }
        }.show()
    }

    /**
     * データ重複時のアラート
     */
    private fun duplicateDataAlert(rawData: ByteArray, qrFormat: QRUtil.QRFormat, type: QRUtil.TicketType) {
        AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.data_already_exists))
            setCancelable(false)
            setMessage(getString(R.string.edit_question))
            setPositiveButton(getString(R.string.yes)) { _, _ ->
                intentAdapter(rawData, qrFormat, type, true)
            }
            setNegativeButton(getString(R.string.no)) { dialog, _ ->
                dialog.dismiss()
                qrReaderView.resume()
            }
        }.show()
    }


    /**
     * フォロー済みのときのアラート
     */
    private fun followedAlert(rawData: ByteArray, qrFormat: QRUtil.QRFormat, followdList: List<User>, type: QRUtil.TicketType, isDuplicate: Boolean) {
        val head = if (!isDuplicate) getString(R.string.already_followed_but_not_registered_below) else getString(R.string.already_followed_below)
        val strb = StringBuilder("${head}${getString(R.string.proceed_question)}\n")

        followdList.forEach { strb.append("${it.userName}\n") }
        AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.already_followed))
            setCancelable(false)
            setMessage(strb.toString())
            setPositiveButton(getString(R.string.goahead)) { _, _ ->
                intentAdapter(rawData, qrFormat, type, isDuplicate)
                finish()
            }
            setNegativeButton(R.string.back) { dialog, _ ->
                dialog.dismiss()
                qrReaderView.resume()
            }
        }.show()
    }

    /**
     * 適切なインテントに飛ばす処理
     * @param rawData qrコードのデータ
     * @param qrFormat qrの形式
     * @param type QRのタイプ
     * @param isDuplicate 重複しているか
     */
    private fun intentAdapter(rawData: ByteArray, qrFormat: QRUtil.QRFormat, type: QRUtil.TicketType, isDuplicate: Boolean) {
        val intentClass = when (type) {
            QRUtil.TicketType.PRICHAN_FOLLOW -> SaveFollowTicket::class.java
            QRUtil.TicketType.PRICHAN_COORD -> SaveCoordTicket::class.java
            QRUtil.TicketType.PRICHAN_MEMBERS -> SaveUserTicket::class.java
            QRUtil.TicketType.OTHERS -> SaveCoordTicket::class.java
        }
        doIntent(rawData, qrFormat, type, intentClass, isDuplicate)

        finish()
    }

    /**
     * 各種画面にジャンプ
     * @param rawData qrコードのデータ
     * @param qrFormat qrの形式
     * @param type QRのタイプ
     * @param isDuplicate 重複しているか
     */
    private fun doIntent(rawData: ByteArray, qrFormat: QRUtil.QRFormat, type: QRUtil.TicketType, destClass: Class<*>, isDuplicate: Boolean) {
        startActivity(Intent(this, destClass).apply {
            putExtra(QRUtil.RAW, rawData)
            putExtra(QRUtil.TICKET_TYPE, type)
            putExtra(QRUtil.QR_FORMAT, qrFormat)
            putExtra(QRUtil.IS_DUPLICATE, isDuplicate)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }

    override fun onResume() {
        super.onResume()
        qrReaderView.resume()
    }

    override fun onPause() {
        super.onPause()
        qrReaderView.resume()
    }

    override fun onStop() {
        super.onStop()
        qrReaderView.pause()
    }


}
