package dev.gabrie.brainwave.mail

import javax.activation.CommandMap
import javax.activation.MailcapCommandMap

/**
 * Android ships a stub `javax.activation` whose default command map has no
 * JavaMail content handlers, so multipart messages fail with
 * "no object DCH for MIME type ..." unless they are registered by hand.
 * Call once, before the first message is built.
 */
object JavaMailInit {

    private var done = false

    @Synchronized
    fun ensure() {
        if (done) return
        done = true
        runCatching {
            val map = CommandMap.getDefaultCommandMap() as? MailcapCommandMap ?: MailcapCommandMap()
            map.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html")
            map.addMailcap("text/xml;; x-java-content-handler=com.sun.mail.handlers.text_xml")
            map.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain")
            map.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed")
            map.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822")
            CommandMap.setDefaultCommandMap(map)
        }
    }
}
