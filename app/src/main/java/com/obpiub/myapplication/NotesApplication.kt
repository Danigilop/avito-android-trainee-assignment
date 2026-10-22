package com.obpiub.myapplication

import android.app.Application

class NotesApplication : Application() {
    val container by lazy { AppContainer(this) }
}
