package net.quber.quberchat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.TrafficStats;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.Opcode;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class STTClient extends WebSocketClient {
    private static final String TAG = STTClient.class.getSimpleName();

    static final int frameSize = 640;
//    static final String serverURI = "wss://0a84f206-5661-420f-9e1f-835a8fbd2c63.api.kr-central-1.kakaoi.io/" +
//            "ai/speech-to-text/ws/long?signature=f5dd6fd6a9b34521a511493d17f9349b&x-api-key=e71fb5af350022a412679312614992df";

    static final String serverURI = "http://221.165.27.100:7846/ws";


    AudioRecord audioRecord;
    STTListener STTListener;
    Thread recordThread;
    Thread socketThread;

    WebSocket webSocket;

    boolean isRecord;
    boolean status = true;
    StringBuffer resultBuffer;

    /**
     * kakao 권장 오디오 포맷
     * 비트 뎁스: 16bit
     * 채널: 1ch (mono)
     * 코덱: RAWPCM, MP3
     * 샘플레이트: 8kHz, 16kHz
     */

    //오디오포맷
    int audioSource = MediaRecorder.AudioSource.DEFAULT;
    int audioChannel = AudioFormat.CHANNEL_IN_MONO;
    int sampleRate = 16000;
    int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    int minBufSize = AudioRecord.getMinBufferSize(sampleRate, audioChannel, audioFormat);

    //음성인식 응답 대기시간 : sec
    int timeOut = 3;


    public STTClient() {
        super(URI.create(serverURI));
    }

    public void init(Context context, STTListener listener) {
        Log.e(TAG, "init!!!");

        resultBuffer = new StringBuffer();

//        stop();

        socketThread = new Thread(new Runnable() {
            @Override
            public void run() {
                webSocket = getConnection();
                TrafficStats.setThreadStatsTag((int) Thread.currentThread().getId());
                STTClient.this.run();
            }
        });

        recordThread = new Thread(new Runnable() {
            @Override
            public void run() {
                isRecord = true;

                try {
                    if (audioRecord != null) {
                        audioRecord.startRecording();
                        Log.i(TAG, " started !!!");

                        byte[] buffer = new byte[frameSize];
                        int byteRead;
                        while (isRecord) {
                            byteRead = audioRecord.read(buffer, 0, buffer.length);
                            ByteBuffer bb = ByteBuffer.wrap(buffer, 0, byteRead);
                            if (getConnection().isOpen()) {
                                sendFragmentedFrame(Opcode.BINARY, bb, true);
                            }
                            Thread.sleep(20);
                        }
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "InterruptedException e : " + e.getMessage());
                }
            }
        });

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) context, Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(context, "마이크 사용 권한이 없습니다. 설정에서 마이크 사용 권한을 허용으로 바꿔야 음성인식이 가능합니다.", Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            }
            Log.e(TAG, "fail to PermissionUtils.checkAudioRecordPermission !!!");
            if (STTListener != null) {
                STTListener.onError(1400, "fail to PermissionUtils.checkAudioRecordPermission");
            }
        } else {
            this.STTListener = listener;
            audioRecord = new AudioRecord(audioSource, sampleRate, audioChannel, audioFormat, minBufSize);
        }

        socketThread.start();

        Log.i(TAG, "finished init !!!");
    }

    //음성인식 요청 메세지
    //showFinalOnly , showExtraInfo 값에 따라 서버 응답메세지 형태 변함
    @Override
    public void onOpen(ServerHandshake handshakedata) {
        try {
            JSONObject jo = new JSONObject();
            jo.put("type", "recogStart");
            jo.put("service", "DICTATION");
            jo.put("showFinalOnly", false);
            jo.put("showExtraInfo", false);
            jo.put("recogLongMaxWaitTime", timeOut);
            jo.put("requestId", "QUBER");
            send(jo.toString());
        } catch (Exception e) {
            Log.e(TAG, "onOpen error E : " + e.getMessage());
        }
    }

    //서버 응답 메세지
    @Override
    public void onMessage(String message) {
        try {
            Log.e(TAG, "message: " + message);
            JsonObject jo = JsonParser.parseString(message).getAsJsonObject();
            String value = String.valueOf(jo.get("value"));
            Log.i(TAG, "Message type : " + jo.get("type"));

            if (jo.get("type").getAsString().equals("ready")) {
                Log.i(TAG, " Done Ready !!!!");

                if (STTListener != null) {
                    STTListener.onReady();
                }

                recordThread.start();
            } else if (jo.get("type").getAsString().equals("beginPointDetection")) {
                Log.i(TAG, " beginning speech !!!!");
            } else if (jo.get("type").getAsString().equals("endPointDetection")) {
                Log.i(TAG, " endPointDetection !!!!");
            } else if (jo.get("type").getAsString().equals("partialResult")) {
                Log.i(TAG, " partialResult : " + value);
                //실시간
                String data = value.replace("\"", "");
                if (STTListener != null) {
                    STTListener.onPartialResult(data);
//                    speechListener.onPartialResult("음성을 듣고있습니다.");
                }
            } else if (jo.get("type").getAsString().equals("finalResult")) {
                Log.i(TAG, " Result : " + value);
                JsonObject jsonObject = JsonParser.parseString(value).getAsJsonObject();
                String result = jsonObject.get("text").getAsString();

                if(resultBuffer != null)
                    resultBuffer.append(result);
            } else if (jo.get("type").getAsString().equals("endLongRecognition")) {
                Log.i(TAG, "endLongRecognition");

                //최종 결과
                String data = resultBuffer.toString().replace("\"", "");
                if (STTListener != null) {
                    STTListener.onResult(data);
                }

                //voice stop
                if(status || isRecord) {
                    stop();
                }

            } else if (jo.get("type").getAsString().equals("errorCalled")) {
                //에러메세지 : "Error {code} {message}"
                Pattern pattern = Pattern.compile("([a-zA-Z]+) (\\d+) ([a-zA-Z].+)");
                Matcher matcher = pattern.matcher(value);

                if (matcher.find()) {
                    String code = matcher.group(2);
                    String msg = matcher.group(3);

                    if(STTListener != null) {
                        if (Integer.parseInt(code) == ErrorCode.ERROR_NO_RESULT) {
                            STTListener.onResult("");
                            status = false;
                        }
                        else if(Integer.parseInt(code) == ErrorCode.ERROR_LEPD_RESULT_TIMEOUT) {
                            JSONObject endObj = new JSONObject();
                            endObj.put("type", "recogEnd");
                            send(endObj.toString());

                            status = false;
                        }
                        else {
                            STTListener.onError(Integer.parseInt(code), msg);
                            status = false;
                        }
                    }

                    if (status) {
                        stop();
                    }

                } else {
                    Log.e(TAG, "matcher fail ");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Log.d(TAG, "close code : " + code + " reason : " + reason );
    }

    @Override
    public void onError(Exception ex) {
        Log.e(TAG, "Exception : " + ex.getMessage());
    }

    public void stop() {
        Log.i(TAG, " start to stop !!!");

        isRecord = false;

        if (webSocket != null) {
            webSocket.close();
            webSocket = null;
        }

        STTListener = null;

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        Log.i(TAG, " stopped !!!");
    }

    public static class ErrorCode {
        public static final int ERROR_NONE = 0;
        public static final int ERROR_NETWORK = 2;
        public static final int ERROR_NETWORK_TIMEOUT = 3;
        public static final int ERROR_NO_RESULT = 4;
        public static final int ERROR_SERVER_INTERNAL = 6;
        public static final int ERROR_SERVER_TIMEOUT = 7;
        public static final int ERROR_SERVER_AUTHENTICATION = 8;
        public static final int ERROR_SERVER_UNSUPPORT_SERVICE = 11;
        public static final int ERROR_SERVER_ALLOWED_REQUESTS_EXCESS = 13;
        public static final int ERROR_SERVER_OBSOLETE_SERVICE = 17;
        public static final int ERROR_LEPD_RESULT_TIMEOUT = 18;
        public static final int ERROR_NEWTONE = 50;
    }
}

