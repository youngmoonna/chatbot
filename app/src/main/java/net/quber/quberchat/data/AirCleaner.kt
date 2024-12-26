package net.quber.quberchat.data

data class AirCleaner(
    var action: Int = 0,
    var ai: Boolean = true,
    var speed: Int = 0,
    var aqm_call_status: Int = 0,
    var capacity: Int = 65,
    var efficiency: Int = 40,
    var avrdailyusagetime: Int = 2,
    var avrdailyusagepower: Int = 140,
    var LED_brightness: Int = 0,
    var LCD_brightness: Int = 0,
    var led_enable: Boolean = false,
    var uv_enable: Boolean = false,
    var sound_volume: Int = 0,
    var button_volume: Int = 0,
    var mute: Boolean = false




)
