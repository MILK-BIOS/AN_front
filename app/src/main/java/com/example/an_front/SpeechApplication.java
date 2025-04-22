package com.example.an_front;

import android.app.Application;

import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;

public class SpeechApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        //   c47e5914  为在开放平台注册的APPID  注意没有空格，直接替换即可，这个=号保留
        SpeechUtility.createUtility(SpeechApplication.this, SpeechConstant.APPID + "=c47e5914");
    }
}
