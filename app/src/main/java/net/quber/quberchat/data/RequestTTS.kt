package net.quber.quberchat.data

data class RequestTTS(
    val text: String = "",
    val speed: Float,
    val Speaker_id: String = "",
    val Filename: String = ""
)
