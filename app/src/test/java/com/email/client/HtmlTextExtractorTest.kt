package com.email.client

import com.email.client.core.util.HtmlTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextExtractorTest {

    @Test
    fun testHtmlToPlainTextExtraction() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>body { color: red; }</style>
                <script>alert('hello');</script>
            </head>
            <body>
                <h1>Invoice #12345</h1>
                <p>Hello John,<br>Thank you for your order.</p>
                <p>Total amount: &euro;99.99 &amp; tax.</p>
                <div>See attached receipt: contract_2026.pdf</div>
            </body>
            </html>
        """.trimIndent()

        val plainText = HtmlTextExtractor.toPlainText(sampleHtml)

        assertTrue(plainText.contains("Invoice #12345"))
        assertTrue(plainText.contains("Hello John,\nThank you for your order."))
        assertTrue(plainText.contains("Total amount:"))
        assertTrue(plainText.contains("& tax."))
        assertTrue(plainText.contains("contract_2026.pdf"))
        // Styles and scripts must be stripped
        assertTrue(!plainText.contains("color: red"))
        assertTrue(!plainText.contains("alert"))
    }
}
