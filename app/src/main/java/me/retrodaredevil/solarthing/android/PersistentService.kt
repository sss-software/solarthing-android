package me.retrodaredevil.solarthing.android

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.os.IBinder
import me.retrodaredevil.solarthing.android.notifications.NotificationChannels
import me.retrodaredevil.solarthing.android.notifications.NotificationHandler
import me.retrodaredevil.solarthing.android.request.DataRequest
import me.retrodaredevil.solarthing.android.request.DataRequester
import me.retrodaredevil.solarthing.android.request.DatabaseDataRequester
import me.retrodaredevil.solarthing.packet.fx.OperationalMode
import java.text.DateFormat
import java.util.*

const val UPDATE_PERIOD: Long = 1000 * 24
const val NOTIFICATION_ID: Int = 1
const val GENERATOR_NOTIFICATION_ID: Int = 2

class PersistentService: Service(){
    private var timer: Timer? = null
    private var task: AsyncTask<*, *, *>? = null

    private var lastGeneratorNotification: Long? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(timer == null){
            timer = Timer()
        }
        setToLoadingNotification()
        timer?.scheduleAtFixedRate(object : TimerTask(){
            var successfulDataRequest: DataRequest? = null
            override fun run(){
                if(task?.status == AsyncTask.Status.RUNNING){
                    val nullableDataRequest = successfulDataRequest
                    if(nullableDataRequest != null){
                        doNotify(nullableDataRequest, getTimedOutSummary())
                    } else {
                        setToTimedOut()
                    }
                }
                task?.cancel(true)

                task = DataUpdaterTask(DatabaseDataRequester { GlobalData.connectionProperties }) { dataRequest ->
                    val usedRequest: DataRequest?
                    val summary: String
                    if(dataRequest.successful) {
                        println("[123]Got successful data request")
                        successfulDataRequest = dataRequest
                        usedRequest = dataRequest
                        summary = getConnectedSummary()
                    } else {
                        println("[123]Got unsuccessful data request")
                        usedRequest = successfulDataRequest
                        summary = getFailedSummary()
                    }
                    if(usedRequest != null) {
                        doNotify(usedRequest, summary)
                    } else {
                        setToFailedNotification(dataRequest)
                    }
                }.execute()
            }
        }, 1000L, UPDATE_PERIOD)
        return START_STICKY
    }
    private fun doNotify(request: DataRequest, summary: String){
        if(request.packetCollectionList.isEmpty()){
            setToNoData()
            return
        }
        val currentInfo = PacketInfo(request.packetCollectionList.last())
        var floatModeActivatedInfo: PacketInfo? = null
        for(packetCollection in request.packetCollectionList.asReversed()){ // go through latest packets first
            val info = PacketInfo(packetCollection)
            if(info.fxMap.values.none { OperationalMode.FLOAT.isActive(it.operatingMode) }){
                break
            }
            floatModeActivatedInfo = info
        }
        val notification = NotificationHandler.createStatusNotification(
            this@PersistentService,
            currentInfo,
            summary,
            floatModeActivatedInfo,
            GlobalData.generatorFloatTimeMillis
        )
        notify(notification)

        if(floatModeActivatedInfo != null){
            // check to see if we should send a notification
            val generatorFloatTimeMillis = GlobalData.generatorFloatTimeMillis
            val now = System.currentTimeMillis()
            if(floatModeActivatedInfo.dateMillis + generatorFloatTimeMillis < now) { // should it be turned off?
                val last = lastGeneratorNotification
                if (last == null || last + GlobalData.generatorNotifyIntervalMillis < now) {
                    getManager().notify(
                        GENERATOR_NOTIFICATION_ID,
                        NotificationHandler.createGeneratorAlert(
                            this@PersistentService,
                            floatModeActivatedInfo, currentInfo, generatorFloatTimeMillis
                        )
                    )
                    lastGeneratorNotification = now
                }
            } else {
                cancelGenerator()
            }
        } else {
            // reset the generator notification because the generator is either off or not in float mode
            cancelGenerator()
        }
    }
    private fun getTimedOutSummary() = "timed out at ${getTimeString()}"
    private fun getConnectedSummary() = "last connection success"
    private fun getFailedSummary() = "failed at ${getTimeString()}"

    private fun setToNoData(){
        val notification = getBuilder()
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.solar_panel)
            .setContentText("Connection successful, but no data.")
            .setSubText(getConnectedSummary())
            .build()
        notify(notification)
    }
    private fun setToFailedNotification(request: DataRequest){
        val notification = getBuilder()
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.solar_panel)
            .setContentTitle("Failed to load solar data. Will Try again.")
            .setContentText("Error: ${request.simpleStatus}")
            .setSubText(getFailedSummary())
            .build()
        notify(notification)
    }
    private fun setToTimedOut(){
        val notification = getBuilder()
            .setOngoing(true)
            .setSmallIcon(R.drawable.solar_panel)
            .setContentText("Last request timed out. Will try again.")
            .setSubText(getFailedSummary())
            .build()
        notify(notification)
    }
    private fun setToLoadingNotification(){
        val notification = getBuilder()
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.solar_panel)
            .setContentText("Loading Solar Data")
            .setSubText("started loading at ${getTimeString()}")
            .setProgress(2, 1, true)
            .build()
        notify(notification)
    }
    private fun notify(notification: Notification){
        getManager().notify(NOTIFICATION_ID, notification)
        startForeground(NOTIFICATION_ID, notification)
    }
    private fun getBuilder(): Notification.Builder {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            return Notification.Builder(this, NotificationChannels.PERSISTENT_STATUS.id)
        }
        return Notification.Builder(this)
    }
    private fun getManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onDestroy() {
        println("[123]Stopping persistent service")
        timer?.cancel() // stop the timer from calling the code any more times
        task?.cancel(true) // stop the code from running if it's running
        getManager().cancel(NOTIFICATION_ID)
        cancelGenerator()
    }
    private fun cancelGenerator(){
        getManager().cancel(GENERATOR_NOTIFICATION_ID)
        lastGeneratorNotification = null
    }
}
private class DataUpdaterTask(
    private val dataRequester: DataRequester,
    private val updateNotification: (dataRequest: DataRequest) -> Unit
) : AsyncTask<Void, Void, DataRequest>() {
    override fun doInBackground(vararg params: Void?): DataRequest {
        return dataRequester.requestData()
    }

    override fun onPostExecute(result: DataRequest?) {
        if(result == null){
            throw NullPointerException("result is null!")
        }
        println("Received result: $result")
        updateNotification(result)
    }

}

private fun getTimeString() = DateFormat.getTimeInstance().format(Calendar.getInstance().time)
