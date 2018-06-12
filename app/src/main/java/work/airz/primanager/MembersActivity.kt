package work.airz.primanager

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_members.*
import kotlinx.android.synthetic.main.ticket_item.*
import kotlinx.android.synthetic.main.ticket_item.view.*
import work.airz.primanager.db.DBUtil
import work.airz.primanager.qr.QRUtil

class MembersActivity : AppCompatActivity(), IItemsList {
    private lateinit var dbUtil: DBUtil
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_members)
        setSupportActionBar(toolbar)

        dbUtil = DBUtil(applicationContext)

        supportFragmentManager.beginTransaction().replace(R.id.container, TicketListFragment()).commit()

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
        }
        return true
    }

    override fun onItemClick(view: View, position: Int) {
        Toast.makeText(applicationContext, "position $position was tapped", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, SaveUserTicket::class.java).apply {
            putExtra(QRUtil.RAW, QRUtil.stringToByte(view.raw_data.text.toString()))
            putExtra(QRUtil.IS_DUPLICATE, true)
            putExtra(QRUtil.QR_FORMAT, dbUtil.getUser(view.raw_data.text.toString())!!.qrFormat.toString())
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }

    override fun onItemList(): List<TicketUtils.TicketItemFormat> {
        val userList = mutableListOf<TicketUtils.TicketItemFormat>()
        dbUtil.getUserList().forEach {
            userList.add(TicketUtils.TicketItemFormat(it.userName, it.userCardId, it.image, it.raw))
        }
        return userList.toList()
    }

    override fun onDelete(positions: List<Int>) {
    }


}
