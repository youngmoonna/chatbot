package net.quber.quberchat

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.quber.quberchat.data.AirCleaner
import net.quber.quberchat.data.Device
import net.quber.quberchat.data.Message
import net.quber.quberchat.data.RequestMsg
import net.quber.quberchat.data.RequestTTS
import net.quber.quberchat.data.ResponseDev
import net.quber.quberchat.data.ResponseMsg
import net.quber.quberchat.databinding.ActivityMainBinding
import net.quber.quberchat.network.ChatAPI
import net.quber.quberchat.network.RetrofitInstance
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type


class MainActivity : AppCompatActivity(), STTListener {

    private lateinit var mainBinding: ActivityMainBinding

    private var chatList = ArrayList<Message>()
    private var chatListAdapter = ChatAdapter(this, chatList)

    private var airCleaner = AirCleaner()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Request camera permissions
        if (allPermissionsGranted()) {

        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        mainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainBinding.root)

        mainBinding.run {
            //device init
            setDeviceValue(airCleaner)

            chatRadioText.isChecked = true

            chatRadioGroup.setOnCheckedChangeListener { radioGroup, i ->
                if(i == chatRadioText.id) {
                    chatEditGroup.visibility = View.VISIBLE
                    chatVoiceGroup.visibility = View.GONE
                }
                else {
                    chatEditGroup.visibility = View.GONE
                    chatVoiceGroup.visibility = View.VISIBLE
                    chatVoiceImage.visibility = View.INVISIBLE
                    Glide.with(this@MainActivity).load(R.raw.ring_effect_post_gif).into(chatVoiceImage)
                }
                chatList.clear()
                chatListAdapter.notifyDataSetChanged()
            }

            //chat list
//            var selectListAdapter = SelectListAdapter(selectMenuList)
            chatRecycler.adapter = chatListAdapter
            chatRecycler.layoutManager = LinearLayoutManager(applicationContext)

            //chat text
            chatSend.setOnClickListener {
                var text = chatEdit.text.toString()
                if(TextUtils.isEmpty(text))
                    Toast.makeText(applicationContext, "입력해주세요", Toast.LENGTH_SHORT).show()
                else {
                    var msg = Message()
                    msg.role = "user"
                    msg.content = text
                    chatEdit.text.clear()
                    llmAPI(msg)
                }
            }

            //chat voice
            chatVoiceStart.setOnClickListener {
                STTClient().init(applicationContext, this@MainActivity)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {

            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    //stt listener
    override fun onReady() {
        runOnUiThread{
            mainBinding.chatVoiceImage.visibility = View.VISIBLE
            mainBinding.chatVoiceText.text = "말씀해주세요."
        }
    }

    override fun onPartialResult(text: String?) {
        runOnUiThread{
            mainBinding.chatVoiceText.text = "듣는중..."
        }
    }

    override fun onResult(msg: String?) {
        if(!TextUtils.isEmpty(msg)) {
            runOnUiThread {
                mainBinding.chatVoiceText.text = msg
            }

            var message = Message()
            message.role = "user"
            message.content = msg!!.toString()
            llmAPI(message)
        }
        Handler(Looper.getMainLooper()).postDelayed( {
            mainBinding.chatVoiceImage.visibility = View.INVISIBLE
            mainBinding.chatVoiceText.text = ""
        }, 1000)

    }

    override fun onError(code: Int, msg: String?) {
        mainBinding.chatVoiceText.text = msg
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }


    fun llmAPI(msg: Message) {
        var sendList = ArrayList<Message>()
        chatList.add(msg)

//        //list view update
//        runOnUiThread {
//            chatListAdapter.notifyDataSetChanged()
//            mainBinding.run {
//                chatRecycler.scrollToPosition(chatListAdapter.itemCount -1)
//            }
//        }
        var chat = chatList.last()
        sendList.add(chat)

        var messages = RequestMsg(sendList)

        retrofit.llm_chat(messages).enqueue(object : Callback<ResponseMsg> {
            override fun onResponse(call: Call<ResponseMsg>, response: Response<ResponseMsg>) {
                if(response.isSuccessful){
                    Log.e(TAG, "chat : ${response.body().toString()}")
                    var chat = response.body()
//                    var jsonArray = JSONArray(chat!!.response.toString())
                    var gson = Gson()
                    val typeToken = object : TypeToken<List<Device>>() {}.type
                    var datas = gson.fromJson<List<Device>>(chat!!.response, typeToken)
//                    var datas = gson.fromJson(chat!!.toString(), ResponseDev::class.java)

                    if(datas != null) {
                        chatList.add(Message("assistant", chat!!.toString()))
//                        var jsonArray = JSONArray()
//                        data.devices.forEach {
//                            var json = callFunction(it, airCleaner)
//                            jsonArray.put(json)
//                        }
//                        var msg = Message("user", jsonArray.toString())
                        //결과 보내기
                        llmAPI(msg)
                    }
                    else {
                        var message = Message("assistant", chat!!.response)
                        chatList.add(message)
                        generateTTS(chat!!.response)
                    }
                    chatListAdapter.notifyDataSetChanged()
                    mainBinding.run {
                        chatRecycler.scrollToPosition(chatListAdapter.itemCount -1)
                    }
                }
            }

            override fun onFailure(call: Call<ResponseMsg>, t: Throwable) {
                Log.e(TAG, "llm failure : ${t.message}}")
            }

        })
    }

    fun generateTTS(text: String) {
        val tts = RequestTTS(text, 1.3.toFloat(), "KR", "quber-test")
        retrofit.generate_tts(tts).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(p0: Call<ResponseBody>, p1: Response<ResponseBody>) {
                var voice = p1.body()?.let { writeResponseBodyToDisk(it) }
                if(voice == true) {
                    val filePath = getExternalFilesDir(null).toString() + File.separator + "out.wav"
                    val mediaPlayer: MediaPlayer? = MediaPlayer().apply {
                        setAudioStreamType(AudioManager.STREAM_MUSIC)
                        setDataSource(filePath)
                        prepare()
                        start()
                    }
                    mediaPlayer?.setOnCompletionListener {
                        mediaPlayer.release()
                    }
                }
            }

            override fun onFailure(p0: Call<ResponseBody>, p1: Throwable) {
                Log.e(TAG, "tts failure : ${p1.message}}")
            }

        })
    }


    private fun writeResponseBodyToDisk(body: ResponseBody): Boolean {
        try {
            // todo change the file location/name according to your needs
            val futureStudioIconFile =
                File(getExternalFilesDir(null).toString() + File.separator + "out.wav")

            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null

            try {
                val fileReader = ByteArray(4096)

                val fileSize = body.contentLength()
                var fileSizeDownloaded: Long = 0

                inputStream = body.byteStream()
                outputStream = FileOutputStream(futureStudioIconFile)

                while (true) {
                    val read = inputStream.read(fileReader)

                    if (read == -1) {
                        break
                    }

                    outputStream.write(fileReader, 0, read)

                    fileSizeDownloaded += read.toLong()

                    Log.d(TAG, "file download: $fileSizeDownloaded of $fileSize")
                }

                outputStream.flush()

                return true
            } catch (e: IOException) {
                return false
            } finally {
                inputStream?.close()

                if (outputStream != null) {
                    outputStream.close()
                }
            }
        } catch (e: IOException) {
            return false
        }
    }

    //device init
    fun setDeviceValue(air: AirCleaner) {
        mainBinding.run {
            //동작여부
            when (air.action) {
                0 -> devicePowerStatus.text = "STOP"
                1 -> devicePowerStatus.text = "START"
                2 -> devicePowerStatus.text = "PAUSE"
            }

            //풍량
            when (air.speed) {
                0 -> deviceFanStatus.text = "자동"
                1 -> deviceFanStatus.text = "취침"
                2 -> deviceFanStatus.text = "약풍"
                3 -> deviceFanStatus.text = "중풍"
                4 -> deviceFanStatus.text = "강풍"
                5 -> deviceFanStatus.text = "터보"
            }

            //스캔모드
            when (air.aqm_call_status) {
                0 -> deviceScanStatus.text = "수동"
                1 -> deviceScanStatus.text = "자동"
            }

            //배터리
            deviceBatteryCapacity.text = String.format(getString(R.string.device_battery_capacity), air.capacity)
            deviceBatteryEfficiency.text = String.format(getString(R.string.device_battery_efficiency), air.efficiency)
            deviceBatteryUse.text = String.format(getString(R.string.device_battery_use_time), air.avrdailyusagetime)
            deviceBatteryPowerTime.text = String.format(getString(R.string.device_battery_power_time), air.avrdailyusagepower)

            //LED 밝기
            when (air.LED_brightness) {
                0 -> deviceLedStatus.text = "100%"
                1 -> deviceLedStatus.text = "50%"
                2 -> deviceLedStatus.text = "0%"
            }

            //LCD 밝기
            when (air.LCD_brightness) {
                0 -> deviceLcdStatus.text = "100%"
                1 -> deviceLcdStatus.text = "50%"
                2 -> deviceLcdStatus.text = "0%"
            }

            //LED 공기질 상태 표시
            if(air.led_enable)
                deviceAirQualityStatus.text = "ON"
            else
                deviceAirQualityStatus.text = "OFF"

            //UV 모드
            if(air.uv_enable)
                deviceUvStatus.text = "ON"
            else
                deviceUvStatus.text = "OFF"

            //미디음 사운드
            when (air.sound_volume) {
                0 -> deviceMediaStatus.text = "0%"
                1 -> deviceMediaStatus.text = "20%"
                2 -> deviceMediaStatus.text = "40%"
                3 -> deviceMediaStatus.text = "60%"
                4 -> deviceMediaStatus.text = "80%"
                5 -> deviceMediaStatus.text = "100%"
            }

            //터치음 사운드
            when (air.button_volume) {
                0 -> deviceTouchStatus.text = "0%"
                1 -> deviceTouchStatus.text = "20%"
                2 -> deviceTouchStatus.text = "40%"
                3 -> deviceTouchStatus.text = "60%"
                4 -> deviceTouchStatus.text = "80%"
                5 -> deviceTouchStatus.text = "100%"
            }

            //음소거 설정
            if(air.mute)
                deviceMuteStatus.text = "ON"
            else
                deviceMuteStatus.text = "OFF"



        }
    }

    fun callFunction(device: Device, air: AirCleaner) : JSONObject {
        var changeStatus = false
        var resultJson = JSONObject()

        when (device.name) {
            //공기청정기 제어
            "setAirCleanerOperation" -> {
                resultJson.put("name", device.name)
//                device.arguments.forEach { key, value ->
//                    if(key == "action") {
//                        var new = value as Int
//                        var old = getAirCleanerOperation(key, air)
//
//                        if(new != old) {
//                            air.action = new
//                            var result = setAirCleanerOperation(key, value, air)
//                            resultJson.put(key, result.action)
//                            changeStatus = true
//                        }
//                    }
//                    else if(key == "ai") {
//                        var new = value as Boolean
//                        var old = getAirCleanerOperation(key, air)
//
//                        if(new != old) {
//                            air.ai = new
//                            var result = setAirCleanerOperation(key, value, air)
//                            resultJson.put(key, result.ai)
//                            changeStatus = true
//                        }
//                    }
//                    else {
//                        var new = value as Int
//                        var old = getAirCleanerOperation(key, air)
//
//                        if(new != old) {
//                            air.speed = new
//                            var result = setAirCleanerOperation(key, value, air)
//                            resultJson.put(key, result.speed)
//                            changeStatus = true
//                        }
//                    }
//                }

                if(changeStatus)
                    resultJson.put("change", 1)
                else
                    resultJson.put("change", 0)
            }
            "getAirCleanerOperation" -> {

            }

            //공기청정기 공기질 스캔
            "setAirQualityIndoorInfo" -> {
                var changeStatus = false
                var resultJson = JSONObject()
                resultJson.put("name", device.name)
//                device.arguments.forEach { key, value ->
//
//                }

                if(changeStatus)
                    resultJson.put("change", 1)
                else
                    resultJson.put("change", 0)
            }
            "getAirQualityIndoorInfo" -> {

            }

            //배터리 정보
            "getBatteryInfo" -> {


            }

        }
        //device set
        setDeviceValue(air)

        return  resultJson
    }

    //제어
    fun setAirCleanerOperation(key : String, value: Any, air: AirCleaner) : AirCleaner {
        when (key){
            "action" -> air.action = value as Int
            "ai" -> air.ai = value as Boolean
            "speed" -> air.speed = value as Int
        }
        return air

    }
    fun getAirCleanerOperation(data : String, air: AirCleaner): Any {
        return when (data) {
            "action" -> air.action
            "ai" -> air.ai
            else -> air.speed
        }
    }

    //공기질
    fun setAirQualityIndoorInfo(json: JSONObject) {

    }
    fun getAirQualityIndoorInfo() {

    }

    //배터리
    fun getBatteryInfo() {

    }

    //LED
    fun setLedBrightness() {

    }
    fun getLedBrightness() {

    }

    fun setLedAirQualityIndicatorEnable() {

    }
    fun getLedAirQualityIndicatorEnable() {

    }

    //LCD
    fun setLCDBrightness() {

    }
    fun getLCDBrightness() {

    }

    //UV
    fun setUVEnable() {

    }
    fun getUVEnable() {

    }

    //sound
    fun setSoundVoiceVolume() {

    }
    fun getSoundVoiceVolume() {

    }
    fun setSoundEffectVolume() {

    }
    fun getSoundEffectVolume() {

    }
    fun setSoundMute() {

    }
    fun getSoundMute() {

    }

    companion object {
        private const val TAG = "main"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.RECORD_AUDIO
            ).apply {

            }.toTypedArray()

        //network
        val retrofit = RetrofitInstance.getInstance().create(ChatAPI::class.java)
    }
}
