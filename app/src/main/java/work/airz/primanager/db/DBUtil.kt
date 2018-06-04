package work.airz.primanager.db

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.jetbrains.anko.db.*
import java.io.*
import kotlin.math.absoluteValue

/**
 * TODO: 関数の整理
 * TODO: 会員証の作り直しでQRのみ変わる場合の関数を作る
 */
private class DBUtil(private val context: Context) {
    private val database: MyDatabaseOpenHelper
        get() = MyDatabaseOpenHelper.getInstance(context)

    companion object {
        private lateinit var userList: List<User> //毎回アクセスすると時間がかかるのでインスタンス化したときにRAM上に保持する
    }

    init {
        userList = getUserList()
    }

    /**
     * ユーザリストの取得のみで他クラスで書き換え不可にする
     */
    fun getUserList(): List<User> {
        return userList.toList()
    }

    /**
     * フォローデータのリスト取得用
     * @return フォローチケットのリスト
     */
    fun getFollowTicketList(): List<FollowTicket> {
        return database.use {
            select(DBConstants.FOLLOW_TICKET_TABLE).exec {
                parseList(rowParser { raw: String, userId: String, userName: String, date: String, follow: Int, follower: Int, coordinate: String, arcade_series: String, image: ByteArray, memo: String ->
                    FollowTicket(raw, userId, userName, date, follow, follower, coordinate, arcade_series, byteArrayToBitmap(image), memo)
                })
            }
        }
    }

    /**
     * フォロチケデータ追加
     * 更新も同様にできる
     */
    fun addFollowTicketData(followTicket: FollowTicket) {
        database.use {
            replace(DBConstants.FOLLOW_TICKET_TABLE,
                    DBConstants.RAW to followTicket.raw,
                    DBConstants.USER_ID to followTicket.userId,
                    DBConstants.USER_NAME to followTicket.userName,
                    DBConstants.DATE to followTicket.date,
                    DBConstants.FOLLOW to followTicket.follow,
                    DBConstants.FOLLOWER to followTicket.follower,
                    DBConstants.COORDINATE to followTicket.coordinate,
                    DBConstants.ARCADE_SERIES to followTicket.arcade_series,
                    DBConstants.IMAGE to bitmapToByteArray(followTicket.image),
                    DBConstants.MEMO to followTicket.memo)
        }
    }

    /**
     * フォロチケデータの削除
     * @param followTicket 削除するフォロチケ
     */
    fun removeFollowTicketData(followTicket: FollowTicket) {
        database.use {
            delete(DBConstants.FOLLOW_TICKET_TABLE, "${DBConstants.RAW} = {arg}", "arg" to followTicket.raw)
        }
    }

    /**
     * ユーザの追加
     * @param user 追加するユーザ
     */
    fun addUser(user: User) {
        checkEnpty(user.raw, user.followTableName)

        database.use {
            replace(DBConstants.USER_TABLE,
                    DBConstants.RAW to user.raw,
                    DBConstants.USER_NAME to user.userName,
                    DBConstants.USER_CARD_ID to user.userCardId,
                    DBConstants.FOLLOWS_TABLE_NAME to user.followTableName)

            //動的にフォローユーザのテーブルを作る
            createTable(user.followTableName, true,
                    DBConstants.USER_ID to TEXT + PRIMARY_KEY,
                    DBConstants.USER_NAME to TEXT,
                    DBConstants.DATE to TEXT,
                    DBConstants.MEMO to TEXT)
            updateUsers() // thread safe
        }
    }

    /**
     * ユーザ削除
     * @param user 削除するユーザ
     */
    fun removeUser(user: User) {
        database.use {
            delete(DBConstants.USER_TABLE, "${DBConstants.RAW} = {arg}", "arg" to user.raw)
            delete(user.followTableName)
            updateUsers() // thread safe
        }
    }

    /**
     * コーデチケットのリスト取得用
     * @return コーデチケットのリスト
     */
    fun getCoordTicketList(): List<CoordTicket> {
        return database.use {
            select(DBConstants.COORD_TICKET_TABLE).exec {
                parseList(rowParser { raw: String, coordId: String, coordName: String, rarity: String, brand: String, color: String, arcadeSeries: String, date: String, whichAccount: String, image: ByteArray, memo: String ->
                    CoordTicket(raw, coordId, coordName, rarity, brand, color, arcadeSeries, date, whichAccount, byteArrayToBitmap(image), memo)
                })
            }
        }
    }

    /**
     * コーデチケット追加
     * 更新も同様にできる
     */
    fun addCoordTicketData(coodTicket: CoordTicket) {
        database.use {
            replace(DBConstants.COORD_TICKET_TABLE,
                    DBConstants.RAW to coodTicket.raw,
                    DBConstants.COORD_ID to coodTicket.coordId,
                    DBConstants.COORD_NAME to coodTicket.coordName,
                    DBConstants.RARITY to coodTicket.rarity,
                    DBConstants.BRAND to coodTicket.brand,
                    DBConstants.COLOR to coodTicket.color,
                    DBConstants.ARCADE_SERIES to coodTicket.arcadeSeries,
                    DBConstants.DATE to coodTicket.date,
                    DBConstants.WHICH_ACCOUNT to coodTicket.whichAccount,
                    DBConstants.IMAGE to bitmapToByteArray(coodTicket.image),
                    DBConstants.MEMO to coodTicket.memo)
        }
    }

    /**
     * コーデデータの削除
     */
    fun removeCoordTicketData(coordTicket: CoordTicket) {
        database.use {
            delete(DBConstants.COORD_TICKET_TABLE, "${DBConstants.RAW} = {arg}", "arg" to coordTicket.raw)
        }
    }

    /**
     * テーブルが存在するかの確認
     */
    fun isTableExists(tableName: String): Boolean {
        return database.use {
            select("sqlite_master", "name")
                    .whereArgs("type = {argtype} AND name = {argname}", "argtype" to "table", "argname" to tableName).exec {
                        parseList(rowParser { _: String ->
                            true
                        })
                    }
        }.isNotEmpty()
    }

    fun getFollowList(myUserRawData: String): List<UserFollow> {
        return database.use {
            select(getUserTableName(myUserRawData)).exec {
                parseList(rowParser { userId: String, userName: String, date: String, memo: String ->
                    UserFollow(userId, userName, date, memo)
                })
            }
        }
    }

    /**
     * ユーザのフォロー用
     * ユーザの更新も同時にします
     * @param my フォローするユーザのデータ
     * @param target フォローするユーザデータ
     */
    fun followUser(my: User, target: UserFollow) {
//        if (isFollowed(myUserRawData, target.userId)) return
        database.use {
            replace(my.followTableName,
                    DBConstants.USER_ID to target.userId,
                    DBConstants.USER_NAME to target.userName,
                    DBConstants.DATE to target.date,
                    DBConstants.MEMO to target.memo)
        }
    }

    /**
     * ユーザデータを参照して対象の会員を既にフォローしているかチェックする
     * @param my ユーザデータ
     */
    fun isFollowed(my: User, targetUserId: String): Boolean {
        return database.use {
            select(my.followTableName, DBConstants.USER_ID).whereArgs("${DBConstants.USER_ID} = {arg}", "arg" to targetUserId).exec {
                parseList(rowParser { _: String ->
                    true
                })
            }.isNotEmpty()
        }
    }

    /**
     * 対象のデータがすでにあるかの確認用
     * @return true:ある　false:ない
     */
    fun isDuplicate(table: String, primaryKeyData: String): Boolean {
        return database.use {
            select(table, DBConstants.RAW).whereArgs("${DBConstants.RAW} = {arg}", "arg" to primaryKeyData).exec {
                parseList(rowParser { _: String -> })
            }.isNotEmpty()
        }
    }

    /**
     * ユーザ数のカウント。
     */
    fun countUsers(): Int {
        return database.use {
            select(DBConstants.USER_TABLE).column("count(${DBConstants.RAW})").exec {
                parseSingle(rowParser { count: Int ->
                    count
                })
            }
        }
    }

    /**
     * テーブル名作成用。
     * ユーザのQR情報からハッシュ値を作成してそれをテーブル名にする
     * "-"文字が入る関係で正の数のみ
     */
    fun getUserHashString(user: User): String {
        return user.raw.hashCode().absoluteValue.toString()
    }

    /**
     * ユーザのテーブル名を取得するやつ
     * ユーザ登録とかで色々使うかも
     */
    private fun getUserTableName(myUserRawData: String): String {
        val tableName = database.use {
            select(DBConstants.USER_TABLE, DBConstants.RAW, DBConstants.FOLLOWS_TABLE_NAME)
                    .whereArgs("${DBConstants.RAW} = {arg}", "arg" to myUserRawData).exec {
                        parseSingle(rowParser { _: String, tableName: String ->
                            tableName
                        })
                    }

        }
        if (tableName.isEmpty()) throw IllegalArgumentException("user not found!!")
        return tableName
    }


    /**
     * 画像をByteArrayへ変換
     * DB保存用
     * @param bitmap サムネイル画像
     * @return 変換済みデータ
     */
    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }

    /**
     * ByteArray形式になっている画像データをBitmapに直す
     * DB読み込み用
     * @param byteArray 画像データ
     * @return 変換済みデータ
     */
    private fun byteArrayToBitmap(byteArray: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    /**
     * 値の空白チェック用
     */
    private fun checkEnpty(vararg checkString: String): Boolean {
        if (checkString.any { it.isEmpty() }) throw IllegalArgumentException("should set values.")
        return true
    }

    /**
     * ユーザ一覧を返す
     */
    private fun getUsers(): List<User> {
        return database.use {
            select(DBConstants.USER_TABLE).exec {
                parseList(rowParser { raw: String, userName: String, userCardId: String, follows: String ->
                    User(raw, userName, userCardId, follows)
                })
            }
        }
    }

    /**
     * ユーザデータの更新
     */
    @Synchronized
    private fun updateUsers() {
        userList = getUsers()
    }


    class FollowTicket(
            val raw: String,
            val userId: String,
            val userName: String,
            val date: String,
            val follow: Int,
            val follower: Int,
            val coordinate: String,
            val arcade_series: String,
            val image: Bitmap,
            val memo: String)

    class CoordTicket(
            val raw: String,
            val coordId: String,
            val coordName: String,
            val rarity: String,
            val brand: String,
            val color: String,
            val arcadeSeries: String,
            val date: String,
            val whichAccount: String,
            val image: Bitmap,
            val memo: String)

    class User(
            val raw: String,
            val userName: String,
            val userCardId: String,
            val followTableName: String)

    class UserFollow(
            val userId: String,
            val userName: String,
            val date: String,
            val memo: String)
}