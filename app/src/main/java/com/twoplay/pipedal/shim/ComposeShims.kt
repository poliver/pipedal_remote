package com.twoplay.pipedal.shim

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.twoplay.pipedal.theme.PiPedalTheme

/**
 * Creates a [ComposeView] for use by this [Fragment].
 *
 * The composition is disposed when the fragment's view lifecycle is destroyed and [content] is
 * hosted within the app's [PiPedalTheme].
 */
fun Fragment.createComposeView(content: @Composable () -> Unit): ComposeView =
    ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        setContent {
            PiPedalTheme {
                content()
            }
        }
    }
