package net.quber.quberchat;

public interface STTListener {
    void onReady();
    void onPartialResult(String text);
    void onResult(String msg);
    void onError (int code, String msg);
}
