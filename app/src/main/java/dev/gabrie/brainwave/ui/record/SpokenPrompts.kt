package dev.gabrie.brainwave.ui.record

import dev.gabrie.brainwave.settings.NoteLanguage

/**
 * The three things the app says out loud during capture.
 *
 * Only these are translated — the rest of the interface is English. They are
 * the strings that reach you when you are not looking at the screen, which is
 * exactly when a wrong language is most disorienting.
 */
object SpokenPrompts {

    fun dueQuestion(language: NoteLanguage): String = when (language) {
        NoteLanguage.ENGLISH -> "When is this due?"
        NoteLanguage.DUTCH -> "Wanneer moet dit af zijn?"
    }

    fun confirmed(language: NoteLanguage, spokenDate: String): String = when (language) {
        NoteLanguage.ENGLISH -> "Got it. Due $spokenDate."
        NoteLanguage.DUTCH -> "Genoteerd. Uiterlijk $spokenDate."
    }

    fun noDateHeard(language: NoteLanguage): String = when (language) {
        NoteLanguage.ENGLISH -> "I didn't catch a date. You can pick one on screen."
        NoteLanguage.DUTCH -> "Ik hoorde geen datum. Je kunt er een op het scherm kiezen."
    }
}
