package com.workoutrec.setup

import androidx.compose.runtime.Composable
import com.workoutrec.google.ApiError

const val SetupProgressTag = "setup_progress"

enum class GuideStep { ApiSetting, Enable }

@Composable
fun SetupProgressScreen(error: ApiError?, onRetry: () -> Unit) {
}

@Composable
fun RewriteDialog(onAnswer: (Boolean) -> Unit) {
}

@Composable
fun AutomationGuideScreen(step: GuideStep, onOpen: () -> Unit, onContinue: () -> Unit) {
}
