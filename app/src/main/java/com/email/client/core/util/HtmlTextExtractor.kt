package com.email.client.core.util

object HtmlTextExtractor {

    /**
     * Converts HTML email content to clean, readable plain text for full-text search,
     * previews, and plain-text fallback rendering.
     */
    fun toPlainText(html: String?): String {
        if (html.isNullOrBlank()) return ""

        return html
            // Strip style and script tags and their content
            .replace(Regex("(?is)<style.*?>.*?</style>"), " ")
            .replace(Regex("(?is)<script.*?>.*?</script>"), " ")
            .replace(Regex("(?is)<head.*?>.*?</head>"), " ")
            // Convert structural tags to newlines
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("(?i)</div>"), "\n")
            .replace(Regex("(?i)</li>"), "\n")
            .replace(Regex("(?i)</tr>"), "\n")
            .replace(Regex("(?i)</h1>|</h2>|</h3>|</h4>|</h5>|</h6>"), "\n\n")
            // Strip remaining HTML tags
            .replace(Regex("<[^>]+>"), " ")
            // Unescape common HTML entities
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace("&copy;", "©")
            .replace("&reg;", "®")
            .replace(Regex("&#(\\d+);")) { match ->
                val code = match.groupValues[1].toIntOrNull()
                if (code != null && code in 32..65535) code.toChar().toString() else " "
            }
            // Normalize whitespace and blank lines
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("(?m)^[ \\t]+"), "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
