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
import android.view.animation.AnimationUtils
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
    private var sendList = ArrayList<Message>()
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
                sendList.clear()
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
            message.content == msg!!.toString()
            if(sendList.size > 0)
                message.content = msg!!.toString()
            else
                message.content = "큐봇,${msg!!}"
            chatList.add(message)
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
        sendList.add(msg)
        var messages = RequestMsg(sendList)

        retrofit.llm_chat(messages).enqueue(object : Callback<ResponseMsg> {
            override fun onResponse(call: Call<ResponseMsg>, response: Response<ResponseMsg>) {
                if(response.isSuccessful){
                    Log.e(TAG, "chat : ${response.body().toString()}")
                    var chat = response.body()!!

                    if(chat.response.contains("arguments")) {
                        var gson = Gson()
                        val typeToken = object : TypeToken<List<Device>>() {}.type
                        var datas = gson.fromJson<List<Device>>(chat!!.response.replace("\n", ""), typeToken)

                        if(datas != null) {
                            sendList.add(Message("assistant", gson.toJson(datas)))
                            var jsonArray = JSONArray()
                            datas.forEach { device ->
                                var json = callFunction(device, airCleaner)
                                jsonArray.put(json)
                            }
                            var msg = Message("user", jsonArray.toString())

                            //결과 보내기
                            llmAPI(msg)
                        }
                    }
                    else {
                        var message = Message("assistant", chat.response)
                        chatList.add(message)
                        generateTTS(chat.response)
                    }

//                    chatListAdapter.notifyDataSetChanged()
                    mainBinding.run {
                        chatRecycler.scrollToPosition(chatListAdapter.itemCount -1)
                    }
                    chatListAdapter.updateItemView(chatList.size-1)
                }
            }

            override fun onFailure(call: Call<ResponseMsg>, t: Throwable) {
                Log.e(TAG, "llm failure : ${t.message}}")
            }

        })
    }

    fun generateTTS(text: String) {
        var res_split = text.split("//")
        val tts = RequestTTS(res_split[0], 1.3.toFloat(), "KR", "quber-test")
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
                        if(text.contains("//call_stt"))
                            STTClient().init(applicationContext, this@MainActivity)
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
    fun     setDeviceValue(air: AirCleaner) {
        mainBinding.run {

            //동작여부
            // blink 애니메이션
            val statusAnim = AnimationUtils.loadAnimation(this@MainActivity,R.anim.blink_animation)
            when (air.action) {
                0 -> {
                    devicePowerStatus.background = getDrawable(R.drawable.shape_red_bg)
                    devicePowerStatus.text = "꺼짐"
//                    devicePowerStatus.startAnimation(statusAnim)
                }
                1 ->  {
                    devicePowerStatus.background = getDrawable(R.drawable.shape_green_bg)
                    devicePowerStatus.text = "켜짐"
//                    devicePowerStatus.startAnimation(statusAnim)
                }
                2 -> {
                    devicePowerStatus.background = getDrawable(R.drawable.shape_red_bg)
                    devicePowerStatus.text = "중지"
//                    devicePowerStatus.startAnimation(statusAnim)
                }
            }

            //풍량
            // blink 애니메이션
            val speedAnim = AnimationUtils.loadAnimation(this@MainActivity,R.anim.blink_animation)
            when (air.speed) {
                0 -> {
                    deviceFanStatus.background = getDrawable(R.drawable.shape_blue_bg)
                    deviceFanStatus.text = "자동"
//                    deviceFanStatus.startAnimation(speedAnim)
                }
                1 -> {
                    deviceFanStatus.background = getDrawable(R.drawable.shape_blue_bg)
                    deviceFanStatus.text = "취침"
//                    deviceFanStatus.startAnimation(speedAnim)
                }
                2 -> {
                    deviceFanStatus.background = getDrawable(R.drawable.shape_green_bg)
                    deviceFanStatus.text = "약풍"
//                    deviceFanStatus.startAnimation(speedAnim)
                }
                3 -> {
                    deviceFanStatus.background = getDrawable(R.drawable.shape_green_bg)
                    deviceFanStatus.text = "중풍"
//                    deviceFanStatus.startAnimation(speedAnim)
                }
                4 -> {
                    deviceFanStatus.background = getDrawable(R.drawable.shape_red_bg)
                    deviceFanStatus.text = "강풍"
//                    deviceFanStatus.startAnimation(speedAnim)
                }
                5 -> {
                    deviceFanStatus.background = getDrawable(R.drawable.shape_red_bg)
                    deviceFanStatus.text = "터보"
//                    deviceFanStatus.startAnimation(speedAnim)
                }
            }

            //스캔모드
            val scanAnim = AnimationUtils.loadAnimation(this@MainActivity,R.anim.blink_animation)
            when (air.aqm_call_status) {
                0 -> {
                    deviceScanStatus.background = getDrawable(R.drawable.shape_green_bg)
                    deviceScanStatus.text = "수동"
//                    deviceScanStatus.startAnimation(scanAnim)
                }
                1 -> {
                    deviceScanStatus.background = getDrawable(R.drawable.shape_red_bg)
                    deviceScanStatus.text = "자동"
//                    deviceScanStatus.startAnimation(scanAnim)
                }
            }

            //배터리
            deviceBatteryCapacity.text = String.format(getString(R.string.device_battery_capacity), air.capacity)
            deviceBatteryEfficiency.text = String.format(getString(R.string.device_battery_efficiency), air.efficiency)
            deviceBatteryUse.text = String.format(getString(R.string.device_battery_use_time), air.avrdailyusagetime)
            deviceBatteryPowerTime.text = String.format(getString(R.string.device_battery_power_time), air.avrdailyusagepower)

            //LED 밝기
            val ledAnim = AnimationUtils.loadAnimation(this@MainActivity,R.anim.blink_animation)
            when (air.LED_brightness) {
                0 -> {
                    deviceLedStatus.background = getDrawable(R.drawable.shape_red_bg)
                    deviceLedStatus.text = "100%"
//                    deviceLedStatus.startAnimation(ledAnim)
                }
                1 -> {
                    deviceLedStatus.background = getDrawable(R.drawable.shape_green_bg)
                    deviceLedStatus.text = "50%"
//                    deviceLedStatus.startAnimation(ledAnim)
                }
                2 -> {
                    deviceLedStatus.background = getDrawable(R.drawable.shape_blue_bg)
                    deviceLedStatus.text = "0%"
//                    deviceLedStatus.startAnimation(ledAnim)
                }
            }

            //LCD 밝기
            val lcdAnim = AnimationUtils.loadAnimation(this@MainActivity,R.anim.blink_animation)
            when (air.LCD_brightness) {
                0 -> {
                    deviceLcdStatus.background = getDrawable(R.drawable.shape_red_bg)
                    deviceLcdStatus.text = "100%"
//                    deviceLcdStatus.startAnimation(lcdAnim)
                }
                1 -> {
                    deviceLcdStatus.background = getDrawable(R.drawable.shape_green_bg)
                    deviceLcdStatus.text = "50%"
//                    deviceLcdStatus.startAnimation(lcdAnim)
                }
                2 -> {
                    deviceLcdStatus.background = getDrawable(R.drawable.shape_blue_bg)
                    deviceLcdStatus.text = "0%"
//                    deviceLcdStatus.startAnimation(lcdAnim)
                }
            }

            //LED 공기질 상태 표시
            val ledEnableAnim = AnimationUtils.loadAnimation(this@MainActivity,R.anim.blink_animation)
            if(air.led_enable) {
                deviceAirQualityStatus.background = getDrawable(R.drawable.shape_green_bg)
                deviceAirQualityStatus.text = "ON"
//                deviceAirQualityStatus.startAnimation(ledEnableAnim)
            }
            else {
                deviceAirQualityStatus.background = getDrawable(R.drawable.shape_red_bg)
                deviceAirQualityStatus.text = "OFF"
//                deviceAirQualityStatus.startAnimation(ledEnableAnim)
            }

            //UV 모드
            val uvAnim = AnimationUtils.loadAnimation(this@MainActivity,R.anim.blink_animation)
            if(air.uv_enable) {
                deviceUvStatus.background = getDrawable(R.drawable.shape_green_bg)
                deviceUvStatus.text = "ON"
//                deviceUvStatus.startAnimation(uvAnim)
            }
            else {
                deviceUvStatus.background = getDrawable(R.drawable.shape_red_bg)
                deviceUvStatus.text = "OFF"
//                deviceUvStatus.startAnimation(uvAnim)
            }


            //미디음 사운드
            val mediaAnim = AnimationUtils.loadAnimation(this@MainActivity,R.anim.blink_animation)
            when (air.sound_volume) {
                0 -> {
                    deviceMediaStatus.background = getDrawable(R.drawable.shape_blue_bg)
                    deviceMediaStatus.text = "0%"
//                    deviceMediaStatus.startAnimation(mediaAnim)
                }
                1 -> {
                    deviceMediaStatus.background = getDrawable(R.drawable.shape_blue_bg)
                    deviceMediaStatus.text = "20%"
//                    deviceMediaStatus.startAnimation(mediaAnim)
                }
                2 -> {
                    deviceMediaStatus.background = getDrawable(R.drawable.shape_green_bg)
                    deviceMediaStatus.text = "40%"
//                    deviceMediaStatus.startAnimation(mediaAnim)
                }
                3 -> {
                    deviceMediaStatus.background = getDrawable(R.drawable.shape_green_bg)
                    deviceMediaStatus.text = "60%"
//                    deviceMediaStatus.startAnimation(mediaAnim)
                }
                4 -> {
                    deviceMediaStatus.background = getDrawable(R.drawable.shape_red_bg)
                    deviceMediaStatus.text = "80%"
//                    deviceMediaStatus.startAnimation(mediaAnim)
                }
                5 -> {
                    deviceMediaStatus.background = getDrawable(R.drawable.shape_red_bg)
                    deviceMediaStatus.text = "100%"
//                    deviceMediaStatus.startAnimation(mediaAnim)
                }
            }

            //터치음 사운드
            val touchAnim = AnimationUtils.loadAnimation(this@MainActivity,R.anim.blink_animation)
            when (air.button_volume) {
                0 -> {
                    deviceTouchStatus.background = getDrawable(R.drawable.shape_blue_bg)
                    deviceTouchStatus.text = "0%"
//                    deviceTouchStatus.startAnimation(touchAnim)
                }
                1 -> {
                    deviceTouchStatus.background = getDrawable(R.drawable.shape_blue_bg)
                    deviceTouchStatus.text = "20%"
//                    deviceTouchStatus.startAnimation(touchAnim)
                }
                2 -> {
                    deviceTouchStatus.background = getDrawable(R.drawable.shape_green_bg)
                    deviceTouchStatus.text = "40%"
//                    deviceTouchStatus.startAnimation(touchAnim)
                }
                3 -> {
                    deviceTouchStatus.background = getDrawable(R.drawable.shape_green_bg)
                    deviceTouchStatus.text = "60%"
//                    deviceTouchStatus.startAnimation(touchAnim)
                }
                4 -> {
                    deviceTouchStatus.background = getDrawable(R.drawable.shape_red_bg)
                    deviceTouchStatus.text = "80%"
//                    deviceTouchStatus.startAnimation(touchAnim)
                }
                5 -> {
                    deviceTouchStatus.background = getDrawable(R.drawable.shape_red_bg)
                    deviceTouchStatus.text = "100%"
//                    deviceTouchStatus.startAnimation(touchAnim)
                }
            }

            //음소거 설정
            val muteAnim = AnimationUtils.loadAnimation(this@MainActivity,R.anim.blink_animation)
            if(air.mute){
                deviceMuteStatus.background = getDrawable(R.drawable.shape_green_bg)
                deviceMuteStatus.text = "ON"
//                deviceTouchStatus.startAnimation(muteAnim)
            }
            else {
                deviceMuteStatus.background = getDrawable(R.drawable.shape_red_bg)
                deviceMuteStatus.text = "OFF"
//                deviceTouchStatus.startAnimation(muteAnim)
            }
        }
    }

    fun callFunction(device: Device, air: AirCleaner) : JSONObject {
        var changeStatus = false
        var resultJson = JSONObject()

        when (device.name) {
            //공기청정기 제어
            "setAirCleanerOperation" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        when (key) {
                            "action" -> {
                                var new = value as Int
                                var old = getAirCleanerOperation(key, air)

                                if(new != old) {
                                    air.action = new
                                    var result = setAirCleanerOperation(key, value, air)
                                    resultJson.put(key, result.action)
                                    changeStatus = true
                                }
                            }
                            "ai" -> {
                                var new = value as Boolean
                                var old = getAirCleanerOperation(key, air)

                                if(new != old) {
                                    air.ai = new
                                    var result = setAirCleanerOperation(key, value, air)
                                    resultJson.put(key, result.ai)
                                    changeStatus = true
                                }
                            }
                            "speed" -> {
                                var new = value as Int
                                var old = getAirCleanerOperation(key, air)

                                if(new != old) {
                                    air.speed = new
                                    var result = setAirCleanerOperation(key, value, air)
                                    resultJson.put(key, result.speed)
                                    changeStatus = true
                                }
                            }
                        }
                    }
                }
                if(changeStatus)
                    resultJson.put("change", 1)
                else
                    resultJson.put("change", 0)
            }
            "getAirCleanerOperation" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        var getValue = getAirCleanerOperation(key, air)
                        resultJson.put(key, getValue)
                    }
                }
            }

            //공기청정기 공기질 스캔
            "setAirQualityIndoorInfo" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        when (key) {
                            "aqm_call_status" -> {
                                var new = value as Int
                                var old = getAirQualityIndoorInfo(key, air)

                                if(new != old) {
                                    air.aqm_call_status = new
                                    var result = setAirQualityIndoorInfo(key, value, air)
                                    resultJson.put(key, result.aqm_call_status)
                                    changeStatus = true
                                }
                            }
                        }
                    }
                }
                if(changeStatus)
                    resultJson.put("change", 1)
                else
                    resultJson.put("change", 0)
            }
            "getAirQualityIndoorInfo" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        var getValue = getAirQualityIndoorInfo(key, air)
                        resultJson.put(key, getValue)
                    }
                }
            }

            //배터리 정보
            "getBatteryInfo" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        var getValue = getBatteryInfo(key, air)
                        resultJson.put(key, getValue)
                    }
                }
            }

            //LED 밝기
            "setLedBrightness" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        when (key) {
                            "LED_brightness" -> {
                                var new = value as Int
                                var old = getLedBrightness(key, air)

                                if(new != old) {
                                    air.LED_brightness = new
                                    var result = setLedBrightness(key, value, air)
                                    resultJson.put(key, result.LED_brightness)
                                    changeStatus = true
                                }
                            }
                        }
                    }
                }
                if(changeStatus)
                    resultJson.put("change", 1)
                else
                    resultJson.put("change", 0)
            }
            "getLedBrightness" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        var getValue = getLedBrightness(key, air)
                        resultJson.put(key, getValue)
                    }
                }
            }
            "setLedAirQualityIndicatorEnable" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        when (key) {
                            "enable" -> {
                                var new = value as Boolean
                                var old = getLedAirQualityIndicatorEnable(key, air)

                                if(new != old) {
                                    air.led_enable = new
                                    var result = setLedAirQualityIndicatorEnable(key, value, air)
                                    resultJson.put(key, result.led_enable)
                                    changeStatus = true
                                }
                            }
                        }
                    }
                }
                if(changeStatus)
                    resultJson.put("change", 1)
                else
                    resultJson.put("change", 0)
            }
            "getLedAirQualityIndicatorEnable" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        var getValue = getLedAirQualityIndicatorEnable(key, air)
                        resultJson.put(key, getValue)
                    }
                }
            }

            //LCD
            "setLCDBrightness" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        when (key) {
                            "LCD_brightness" -> {
                                var new = value as Int
                                var old = getLCDBrightness(key, air)

                                if(new != old) {
                                    air.LCD_brightness = new
                                    var result = setLCDBrightness(key, value, air)
                                    resultJson.put(key, result.LCD_brightness)
                                    changeStatus = true
                                }
                            }
                        }
                    }
                }
                if(changeStatus)
                    resultJson.put("change", 1)
                else
                    resultJson.put("change", 0)
            }
            "getLCDBrightness" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        var getValue = getLCDBrightness(key, air)
                        resultJson.put(key, getValue)
                    }
                }
            }

            //UV
            "setUVEnable" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        when (key) {
                            "enable" -> {
                                var new = value as Boolean
                                var old = getUVEnable(key, air)

                                if(new != old) {
                                    air.uv_enable = new
                                    var result = setUVEnable(key, value, air)
                                    resultJson.put(key, result.uv_enable)
                                    changeStatus = true
                                }
                            }
                        }
                    }
                }
                if(changeStatus)
                    resultJson.put("change", 1)
                else
                    resultJson.put("change", 0)
            }
            "getUVEnable" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        var getValue = getUVEnable(key, air)
                        resultJson.put(key, getValue)
                    }
                }
            }

            //sound
            "setSoundVoiceVolume" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        when (key) {
                            "sound_volume" -> {
                                var new = value as Int
                                var old = getSoundVoiceVolume(key, air)

                                if(new != old) {
                                    air.sound_volume = new
                                    var result = setSoundVoiceVolume(key, value, air)
                                    resultJson.put(key, result.sound_volume)
                                    changeStatus = true
                                }
                            }
                        }
                    }
                }
                if(changeStatus)
                    resultJson.put("change", 1)
                else
                    resultJson.put("change", 0)

            }
            "getSoundVoiceVolume" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        var getValue = getSoundVoiceVolume(key, air)
                        resultJson.put(key, getValue)
                    }
                }
            }
            "setSoundEffectVolume" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        when (key) {
                            "button_volume" -> {
                                var new = value as Int
                                var old = getSoundEffectVolume(key, air)

                                if(new != old) {
                                    air.button_volume = new
                                    var result = setSoundEffectVolume(key, value, air)
                                    resultJson.put(key, result.button_volume)
                                    changeStatus = true
                                }
                            }
                        }
                    }
                }
                if(changeStatus)
                    resultJson.put("change", 1)
                else
                    resultJson.put("change", 0)

            }
            "getSoundEffectVolume" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        var getValue = getSoundEffectVolume(key, air)
                        resultJson.put(key, getValue)
                    }
                }
            }
            "setSoundMute" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        when (key) {
                            "mute" -> {
                                var new = value as Boolean
                                var old = getSoundMute(key, air)

                                if(new != old) {
                                    air.mute = new
                                    var result = setSoundMute(key, value, air)
                                    resultJson.put(key, result.mute)
                                    changeStatus = true
                                }
                            }
                        }
                    }
                }
                if(changeStatus)
                    resultJson.put("change", 1)
                else
                    resultJson.put("change", 0)

            }
            "getSoundMute" -> {
                resultJson.put("name", device.name)
                var argument = converterToArgument(device.arguments)
                if(argument.size > 0) {
                    argument.forEach { key, value ->
                        var getValue = getSoundMute(key, air)
                        resultJson.put(key, getValue)
                    }
                }
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
    fun setAirQualityIndoorInfo(key : String, value: Any, air: AirCleaner): AirCleaner {
        when (key){
            "aqm_call_status" -> air.aqm_call_status = value as Int
        }
        return air

    }
    fun getAirQualityIndoorInfo(data : String, air: AirCleaner): Any {
        return when (data) {
            "aqm_call_status" -> air.aqm_call_status
            else -> air
        }
    }

    //배터리
    fun getBatteryInfo(data : String, air: AirCleaner): Any{
        return when (data) {
            "capacity" -> air.capacity
            "efficiency" -> air.efficiency
            "avrdailyusagetime" -> air.avrdailyusagetime
            "avrdailyusagepower" -> air.avrdailyusagepower
            else -> ""
        }
    }

    //LED
    fun setLedBrightness(key : String, value: Any, air: AirCleaner): AirCleaner {
        when (key){
            "LED_brightness" -> air.LED_brightness = value as Int
        }
        return air
    }
    fun getLedBrightness(data : String, air: AirCleaner) : Any {
        return when (data) {
            "LED_brightness" -> air.LED_brightness
            else -> ""
        }
    }
    fun setLedAirQualityIndicatorEnable(key : String, value: Any, air: AirCleaner): AirCleaner {
        when (key){
            "enable" -> air.led_enable = value as Boolean
        }
        return air
    }
    fun getLedAirQualityIndicatorEnable(data : String, air: AirCleaner) : Any {
        return when (data) {
            "enable" -> air.led_enable
            else -> ""
        }
    }

    //LCD
    fun setLCDBrightness(key : String, value: Any, air: AirCleaner): AirCleaner {
        when (key){
            "LCD_brightness" -> air.LCD_brightness = value as Int
        }
        return air
    }
    fun getLCDBrightness(data : String, air: AirCleaner) : Any {
        return when (data) {
            "LCD_brightness" -> air.LCD_brightness
            else -> ""
        }
    }

    //UV
    fun setUVEnable(key : String, value: Any, air: AirCleaner): AirCleaner {
        when (key){
            "enable" -> air.uv_enable = value as Boolean
        }
        return air
    }
    fun getUVEnable(data : String, air: AirCleaner) : Any {
        return when (data) {
            "enable" -> air.uv_enable
            else -> ""
        }
    }

    //sound
    fun setSoundVoiceVolume(key : String, value: Any, air: AirCleaner): AirCleaner {
        when (key){
            "sound_volume" -> air.sound_volume = value as Int
        }
        return air
    }
    fun getSoundVoiceVolume(data : String, air: AirCleaner) : Any {
        return when (data) {
            "sound_volume" -> air.sound_volume
            else -> ""
        }
    }
    fun setSoundEffectVolume(key : String, value: Any, air: AirCleaner): AirCleaner {
        when (key){
            "button_volume" -> air.button_volume = value as Int
        }
        return air
    }
    fun getSoundEffectVolume(data : String, air: AirCleaner) : Any {
        return when (data) {
            "button_volume" -> air.button_volume
            else -> ""
        }
    }
    fun setSoundMute(key : String, value: Any, air: AirCleaner): AirCleaner {
        when (key){
            "mute" -> air.mute = value as Boolean
        }
        return air
    }
    fun getSoundMute(data : String, air: AirCleaner) : Any {
        return when (data) {
            "mute" -> air.mute
            else -> ""
        }
    }

    fun converterToArgument(arguments : Any): HashMap<String, Any> {
        var argument: HashMap<String, Any> = HashMap()
        var json = JSONObject(arguments.toString())

        if(arguments.toString().contains("get_param")) {
            var jsonArray = json.getJSONArray("get_param")
            for(i in 0 until  jsonArray.length()) {
                argument.put(jsonArray.getString(i), "")
                Log.e(TAG, "param: ${jsonArray.get(i)}")
            }
        }
        else {
            if(arguments.toString().contains("action")) {
                var action = json.get("action")
                argument.put("action", (action as Int))
            }
            else if(arguments.toString().contains("ai")) {
                var ai = json.get("ai")
                argument.put("ai", (ai as Boolean))
            }
            else if(arguments.toString().contains("speed")) {
                var speed = json.get("speed")
                argument.put("speed", (speed as Int))
            }
            else if(arguments.toString().contains("aqm_call_status")) {
                var aqm = json.get("aqm_call_status")
                argument.put("aqm_call_status", (aqm as Int))
            }
            else if(arguments.toString().contains("LED_brightness")) {
                var led = json.get("LED_brightness")
                argument.put("LED_brightness", (led as Int))
            }
            else if(arguments.toString().contains("LCD_brightness")) {
                var lcd = json.get("LCD_brightness")
                argument.put("LCD_brightness", (lcd as Int))
            }
            else if(arguments.toString().contains("enable")) {
                var enable = json.get("enable")
                if(enable == "true")
                    argument.put("enable", true)
                else
                    argument.put("enable", false)
            }
            else if(arguments.toString().contains("sound_volume")) {
                var sound = json.get("sound_volume")
                argument.put("sound_volume", (sound as Int))
            }
            else if(arguments.toString().contains("button_volume")) {
                var button = json.get("button_volume")
                argument.put("button_volume", (button as Int))
            }
            else if(arguments.toString().contains("mute")) {
                var mute = json.get("mute")
                if(mute == "true")
                    argument.put("mute", true)
                else
                    argument.put("mute", false)
            }
        }

        return  argument
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
