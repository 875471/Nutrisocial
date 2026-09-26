package com.example.nutrisocial

import android.app.Application

class NutriSocialApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
    }
}
