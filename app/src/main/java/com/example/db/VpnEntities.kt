package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "connection_sessions")
data class ConnectionSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serverName: String,
    val countryCode: String,
    val connectTime: Long = System.currentTimeMillis(),
    val durationSecs: Long,
    val bytesUploaded: Long,
    val bytesDownloaded: Long
)

@Entity(tableName = "bypassed_apps")
data class BypassedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isBypassed: Boolean = true
)

@Dao
interface VpnDao {
    @Query("SELECT * FROM connection_sessions ORDER BY connectTime DESC")
    fun getAllSessions(): Flow<List<ConnectionSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ConnectionSession)

    @Query("DELETE FROM connection_sessions")
    suspend fun clearAllSessions()

    @Query("SELECT * FROM bypassed_apps")
    fun getBypassedApps(): Flow<List<BypassedApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBypassedApp(app: BypassedApp)

    @Delete
    suspend fun deleteBypassedApp(app: BypassedApp)

    @Query("DELETE FROM bypassed_apps WHERE packageName = :packageName")
    suspend fun deleteBypassedAppByPackage(packageName: String)
}

@Database(entities = [ConnectionSession::class, BypassedApp::class], version = 1, exportSchema = false)
abstract class VpnDatabase : RoomDatabase() {
    abstract fun vpnDao(): VpnDao
}

class VpnRepository(private val vpnDao: VpnDao) {
    val allSessions: Flow<List<ConnectionSession>> = vpnDao.getAllSessions()
    val bypassedApps: Flow<List<BypassedApp>> = vpnDao.getBypassedApps()

    suspend fun insertSession(session: ConnectionSession) {
        vpnDao.insertSession(session)
    }

    suspend fun clearHistory() {
        vpnDao.clearAllSessions()
    }

    suspend fun addBypassedApp(app: BypassedApp) {
        vpnDao.insertBypassedApp(app)
    }

    suspend fun removeBypassedApp(packageName: String) {
        vpnDao.deleteBypassedAppByPackage(packageName)
    }
}
