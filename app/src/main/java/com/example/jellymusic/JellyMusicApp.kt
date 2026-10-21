package com.example.jellymusic

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.example.jellymusic.data.AuthHeaders
import okhttp3.OkHttpClient

class JellyMusicApp : Application() {
    lateinit var appState: AppState
        private set

    override fun onCreate() {
        super.onCreate()

        val imageClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val authorization = AuthHeaders.authorization
                val host = AuthHeaders.host
                if (authorization != null && host != null && request.url.host == host) {
                    chain.proceed(
                        request.newBuilder()
                            .header("Authorization", authorization)
                            .build(),
                    )
                } else {
                    chain.proceed(request)
                }
            }
            .build()

        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(imageClient)
                .crossfade(true)
                .build(),
        )

        appState = AppState(this)
    }
}