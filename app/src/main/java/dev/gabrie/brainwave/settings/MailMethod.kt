package dev.gabrie.brainwave.settings

/** How a brainwave's email leaves the phone. */
enum class MailMethod(val label: String) {
    /**
     * The app sends it itself, in the background, over SMTP with the user's own
     * mail account. Fully automatic; needs server details and (for Gmail) an
     * app password. This is the default.
     */
    SMTP("SMTP"),

    /**
     * The app opens the phone's mail app with the message and the recording
     * already filled in, and the user taps Send. Android has no way to send
     * through another app silently — by design — so this is one tap per mail,
     * but it needs no password and no server details.
     */
    MAIL_APP("Mail app"),
}
