package com.openhab.auto

/** A sitemap available on the server, as listed by `GET /rest/sitemaps`. */
data class SitemapInfo(val name: String, val label: String)
