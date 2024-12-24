package net.quber.quberchat.data

data class AirCleaner(
    var action: Int = 1,
    var ai: Boolean = true,
    var speed: Int = 0,
    var aqm_call_status: Int = 0,
    var capacity: Int = 0,
    var efficiency: Int = 0,
    var avrdailyusagetime: Int = 0,
    var avrdailyusagepower: Int = 0,
    var LED_brightness: Int = 0,
    var LCD_brightness: Int = 0,
    var led_enable: Boolean = false,
    var uv_enable: Boolean = false,
    var sound_volume: Int = 0,
    var button_volume: Int = 0,
    var mute: Boolean = false




)
