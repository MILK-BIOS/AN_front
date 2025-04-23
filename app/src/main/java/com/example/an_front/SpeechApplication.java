package com.example.an_front;

import android.app.Application;

import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;

public class SpeechApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 禁用自动请求权限，添加参数 engine.tts.autocheck=false
        SpeechUtility.createUtility(SpeechApplication.this, 
            SpeechConstant.APPID + "=c47e5914" + "," 
            + SpeechConstant.ENGINE_MODE + "=plus" + ","
            + "engine.tts.autocheck=false");
    }
}
