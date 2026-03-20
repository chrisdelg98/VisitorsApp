package com.eflglobal.visitorsapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.eflglobal.visitorsapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) { Text(text = "Atras") }
                    }
                },
                actions = {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "EFL Global",
                        modifier = Modifier.padding(end = 12.dp).size(32.dp)
                    )
                }
            )
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        content(padding)
    }
}
