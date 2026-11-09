package com.chichu.toolbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private lateinit var model: ToolboxViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ToolboxApplication).container
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ToolboxViewModel(container) as T
        })[ToolboxViewModel::class.java]
        acceptIntent(intent)
        setContent { ToolboxApp(model) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
    }

    private fun acceptIntent(intent: Intent) {
        if (intent.action == "com.chichu.toolbox.intent.action.REQUEST_PERMISSION") {
            intent.getStringExtra("com.chichu.toolbox.extra.PERMISSION_TOKEN")?.let(model::receivePermissionToken)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) model.foreground()
    }
}
