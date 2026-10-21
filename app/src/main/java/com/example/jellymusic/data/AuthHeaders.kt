package com.example.jellymusic.data

object AuthHeaders {
    @Volatile
    var authorization: String? = null

    @Volatile
    var host: String? = null

    fun clear() {
        authorization = null
        host = null
    }
}
