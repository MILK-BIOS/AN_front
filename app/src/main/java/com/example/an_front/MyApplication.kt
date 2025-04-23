package com.example.an_front

import android.app.Application
import com.amap.api.location.AMapLocationClient

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 设置高德隐私协议 - 必须在初始化任何高德SDK前调用
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)
    }
}