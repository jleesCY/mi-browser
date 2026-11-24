// Scriptlet rules (uBlock Origin's "example.com##+js(name, args…)"): small scripts that run in a page before its own,
// to defuse what network rules and element hiding can't reach (ads delivered with a site's own data, as on YouTube;
// anti-adblock walls; pop-unders). This file reads the rules and answers which run on a site; the scripts themselves
// are assets/js/scriptlets.js. uBlock's lists come with "!#if" sections for other browsers, read here too.
package com.jlees.mibrowser.adblock

/** One scriptlet to run: its full name (aliases resolved) and arguments. */
data class Scriptlet(val name: String, val args: List<String>) {
    /** What exceptions ("#@#+js(…)") are compared by. */
    val key get() = (listOf(name) + args).joinToString(",")
}

/** uBlock Origin's short names, as lists use them, to the full names assets/js/scriptlets.js knows. */
private val ALIASES = mapOf(
    "aopr" to "abort-on-property-read", "aopw" to "abort-on-property-write", "acs" to "abort-current-script",
    "acis" to "abort-current-script", "abort-current-inline-script" to "abort-current-script",
    "aost" to "abort-on-stack-trace", "set" to "set-constant", "trusted-set" to "set-constant",
    "nostif" to "no-setTimeout-if", "prevent-setTimeout" to "no-setTimeout-if", "setTimeout-defuser" to "no-setTimeout-if",
    "nosiif" to "no-setInterval-if", "prevent-setInterval" to "no-setInterval-if", "setInterval-defuser" to "no-setInterval-if",
    "aeld" to "addEventListener-defuser", "prevent-addEventListener" to "addEventListener-defuser",
    "nowoif" to "no-window-open-if", "prevent-window-open" to "no-window-open-if", "window.open-defuser" to "no-window-open-if",
    "nano-sib" to "adjust-setInterval", "nano-setInterval-booster" to "adjust-setInterval",
    "nano-stb" to "adjust-setTimeout", "nano-setTimeout-booster" to "adjust-setTimeout",
    "rmnt" to "remove-node-text", "rpnt" to "replace-node-text", "trusted-rpnt" to "replace-node-text",
    "trusted-replace-node-text" to "replace-node-text", "ra" to "remove-attr", "rc" to "remove-class",
    "no-fetch-if" to "prevent-fetch", "trusted-prevent-fetch" to "prevent-fetch",
    "no-xhr-if" to "prevent-xhr", "trusted-prevent-xhr" to "prevent-xhr",
    "noeval" to "noeval-if", "prevent-eval-if" to "noeval-if",
    "trusted-rpfr" to "replace-fetch-response", "trusted-replace-fetch-response" to "replace-fetch-response",
    "trusted-replace-xhr-response" to "replace-xhr-response",
    "nobab" to "bab-defuser", "nofab" to "fuckadblock-defuser", "fuckadblock.js-3.2.0" to "fuckadblock-defuser",
    "popads.net" to "popads-dummy", "trusted-set-attr" to "set-attr", "trusted-set-cookie" to "set-cookie",
    "trusted-set-local-storage-item" to "set-local-storage-item",
)

fun canonicalScriptlet(name: String) = name.removeSuffix(".js").let { ALIASES[it] ?: ALIASES[name] ?: it }

/**
 * The arguments inside "+js(…)": split on commas not escaped as "\,", trimmed, unescaped, and unquoted when wrapped in
 * matching quotes (lists quote an argument to keep its spaces or commas).
 */
fun scriptletArgs(body: String): List<String> {
    val out = ArrayList<String>()
    val cur = StringBuilder()
    var i = 0
    while (i < body.length) {
        val c = body[i]
        if (c == '\\' && i + 1 < body.length && body[i + 1] == ',') { cur.append(','); i += 2; continue }
        if (c == ',') { out += cur.toString(); cur.clear() } else cur.append(c)
        i++
    }
    out += cur.toString()
    return out.map { a ->
        val t = a.trim()
        if (t.length >= 2 && (t[0] == '\'' || t[0] == '"' || t[0] == '`') && t.last() == t[0]) t.substring(1, t.length - 1) else t
    }
}

/** "name, a, b" (inside +js(…)) as a scriptlet; null when empty ("+js()": an exception for every scriptlet). */
fun parseScriptlet(body: String): Scriptlet? {
    val args = scriptletArgs(body)
    val name = args.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
    return Scriptlet(canonicalScriptlet(name), args.drop(1).dropLastWhile { it.isEmpty() })
}

/**
 * A list with its "!#if …" / "!#else" / "!#endif" sections settled for [env] (what mi is: Chromium on a phone), and
 * its "!#include" lines left out (their files are fetched and read like the list). Conditions are tokens joined by
 * "&&" and "||", each maybe negated with "!"; one mi doesn't know is false.
 */
fun preprocessFilterList(lines: Sequence<String>, env: Set<String> = SCRIPTLET_ENV): Sequence<String> = sequence {
    val stack = ArrayList<Boolean>() // per open !#if: whether its current branch is on
    fun on() = stack.all { it }
    for (line in lines) {
        val t = line.trim()
        when {
            t.startsWith("!#if ") -> stack += evaluate(t.removePrefix("!#if ").trim(), env)
            t == "!#else" -> if (stack.isNotEmpty()) stack[stack.lastIndex] = !stack.last()
            t == "!#endif" -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            t.startsWith("!#include") -> {}
            on() -> yield(line)
        }
    }
}

/** What mi counts as for "!#if": a Chromium engine on a phone, running uBlock-style rules. */
val SCRIPTLET_ENV = setOf("env_chromium", "env_mobile", "ext_ublock", "true")

private fun evaluate(cond: String, env: Set<String>): Boolean =
    cond.split("||").any { part -> part.split("&&").all { tok -> tok.trim().let { if (it.startsWith("!")) it.substring(1).trim() !in env else it in env } } }

/**
 * Every scriptlet rule from the lists, looked up by site. A rule names sites (and their subdomains), "name.*" for a
 * site under any top-level domain, "~site" for where it doesn't run, or nothing / "*" for everywhere. An exception
 * ("site#@#+js(…)") turns one scriptlet off there, or all of them with an empty "+js()".
 */
class ScriptletIndex {
    private class Rule(val scriptlet: Scriptlet, val excluded: Set<String>)
    private val bySite = HashMap<String, MutableList<Rule>>()
    private val everywhere = ArrayList<Rule>()
    private val exceptions = HashMap<String, MutableSet<String>>() // site → scriptlet keys ("" = all)
    var size = 0; private set

    /** A list line; anything but a scriptlet rule (or exception) is ignored. */
    fun add(line: String) {
        val end = line.trimEnd()
        if (!end.endsWith(")")) return
        val ex = end.indexOf("#@#+js(")
        val at = if (ex >= 0) ex else end.indexOf("##+js(").takeIf { it >= 0 } ?: return
        val except = ex >= 0
        val sitesEnd = at
        val body = end.substring(at + if (except) 7 else 6, end.length - 1)
        val sites = line.substring(0, sitesEnd).split(',').map { it.trim().lowercase().removeSuffix(">>") }.filter { it.isNotEmpty() && !it.startsWith("/") }
        val scriptlet = parseScriptlet(body)
        if (except) {
            for (s in sites.filterNot { it.startsWith("~") }) exceptions.getOrPut(s) { HashSet() } += scriptlet?.key ?: ""
            return
        }
        scriptlet ?: return
        val include = sites.filterNot { it.startsWith("~") || it == "*" }
        val rule = Rule(scriptlet, sites.filter { it.startsWith("~") }.map { it.substring(1) }.toSet())
        if (include.isEmpty()) everywhere += rule else include.forEach { bySite.getOrPut(it) { ArrayList(1) } += rule }
        size++
    }

    /** The names a page on [host] is known by in rules: it and its parent domains, and each as "name.*". */
    private fun namesOf(host: String): List<String> {
        val h = host.lowercase().trimEnd('.')
        val labels = h.split('.')
        val names = (0 until labels.size).map { labels.drop(it).joinToString(".") }
        // "google.*" matches google.com, google.co.uk and www.google.de: the host without its public suffix
        val suffix = AdBlock.registrable(h).substringAfter('.', "")
        val bare = if (suffix.isNotEmpty() && h.endsWith(".$suffix")) h.removeSuffix(".$suffix") else null
        val entities = bare?.split('.')?.let { b -> (0 until b.size).map { b.drop(it).joinToString(".") + ".*" } }.orEmpty()
        return names + entities
    }

    /** The scriptlets to run on [host], in list order, each once. */
    fun forHost(host: String): List<Scriptlet> {
        if (host.isEmpty()) return emptyList()
        val names = namesOf(host)
        val off = names.flatMap { exceptions[it].orEmpty() }.toSet()
        if ("" in off) return emptyList()
        val seen = HashSet<String>()
        return (names.flatMap { bySite[it].orEmpty() } + everywhere)
            .filter { r -> names.none { it in r.excluded } && r.scriptlet.key !in off && seen.add(r.scriptlet.key) }
            .map { it.scriptlet }
    }
}
