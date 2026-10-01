package dev.gabrie.brainwave.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import dev.gabrie.brainwave.AppContainer
import dev.gabrie.brainwave.BrainwaveApp

/** Pulls the app's dependency graph out of the ViewModel creation extras. */
val CreationExtras.container: AppContainer
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BrainwaveApp).container
