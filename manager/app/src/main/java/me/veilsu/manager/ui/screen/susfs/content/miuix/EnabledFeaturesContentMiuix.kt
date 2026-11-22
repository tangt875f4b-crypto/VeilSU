package me.veilsu.manager.ui.screen.susfs.content.miuix

import androidx.compose.runtime.Composable
import me.veilsu.manager.ui.screen.susfs.content.EnabledFeaturesContent
import me.veilsu.manager.ui.screen.susfs.util.EnabledFeature

@Composable
fun EnabledFeaturesContentMiuix(
    enabledFeatures: List<EnabledFeature>,
    onRefresh: () -> Unit,
) = EnabledFeaturesContent(enabledFeatures, onRefresh)
