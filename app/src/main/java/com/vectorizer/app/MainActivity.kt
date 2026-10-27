package com.vectorizer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vectorizer.app.domain.BatchItem
import com.vectorizer.app.ui.screens.CompareScreen
import com.vectorizer.app.ui.screens.HomeScreen
import com.vectorizer.app.ui.theme.AndroidVectorizerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidVectorizerTheme {
                var selectedItemForCompare by remember { mutableStateOf<BatchItem?>(null) }

                if (selectedItemForCompare != null) {
                    CompareScreen(
                        item = selectedItemForCompare!!,
                        onBack = { selectedItemForCompare = null }
                    )
                } else {
                    HomeScreen(
                        onNavigateToCompare = { item ->
                            selectedItemForCompare = item
                        }
                    )
                }
            }
        }
    }
}
