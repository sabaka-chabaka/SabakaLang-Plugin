package com.sabakachabaka.sabakalang

/**
 * Single source of truth for all SabakaLang built-in functions.
 * Matches Compiler.cs and VirtualMachine.cs exactly.
 */
object SabakaBuiltins {

    data class BuiltinInfo(
        val name: String,
        val signature: String,
        val returnType: String,
        val description: String,
        val params: List<Pair<String, String>> = emptyList()
    )

    val GLOBAL_FUNCTIONS = listOf(
        BuiltinInfo("print",       "print(value)",              "void",    "Prints value to stdout.",                 listOf("value" to "any")),
        BuiltinInfo("input",       "input()",                   "string",  "Reads a line from stdin."),
        BuiltinInfo("sleep",       "sleep(ms)",                 "void",    "Pauses execution for ms milliseconds.",   listOf("ms" to "int")),
        BuiltinInfo("readFile",    "readFile(path)",            "string",  "Reads entire file as string.",            listOf("path" to "string")),
        BuiltinInfo("writeFile",   "writeFile(path, content)",  "void",    "Writes content to file.",                 listOf("path" to "string", "content" to "string")),
        BuiltinInfo("appendFile",  "appendFile(path, content)", "void",    "Appends content to file.",                listOf("path" to "string", "content" to "string")),
        BuiltinInfo("fileExists",  "fileExists(path)",          "bool",    "Returns true if file exists.",            listOf("path" to "string")),
        BuiltinInfo("deleteFile",  "deleteFile(path)",          "void",    "Deletes a file.",                         listOf("path" to "string")),
        BuiltinInfo("readLines",   "readLines(path)",           "string[]","Reads all lines into array.",             listOf("path" to "string")),
        BuiltinInfo("time",        "time()",                    "int",     "Returns current Unix timestamp (s)."),
        BuiltinInfo("timeMs",      "timeMs()",                  "int",     "Returns current Unix timestamp (ms)."),
        BuiltinInfo("httpGet",     "httpGet(url)",              "string",  "HTTP GET, returns response body.",        listOf("url" to "string")),
        BuiltinInfo("httpPost",    "httpPost(url, body)",       "string",  "HTTP POST with plain-text body.",         listOf("url" to "string", "body" to "string")),
        BuiltinInfo("httpPostJson","httpPostJson(url, json)",   "string",  "HTTP POST with JSON body.",               listOf("url" to "string", "json" to "string")),
        BuiltinInfo("ord",         "ord(ch)",                   "int",     "Returns Unicode code point of char.",     listOf("ch" to "string")),
        BuiltinInfo("chr",         "chr(code)",                 "string",  "Returns char for Unicode code point.",    listOf("code" to "int")),
    )

    /** .length property available on arrays and strings */
    val ARRAY_MEMBERS = listOf(
        BuiltinInfo("length", "length", "int", "Number of elements (array) or characters (string).")
    )

    val GLOBAL_NAMES: Set<String> = GLOBAL_FUNCTIONS.map { it.name }.toHashSet()
    val GLOBAL_BY_NAME: Map<String, BuiltinInfo> = GLOBAL_FUNCTIONS.associateBy { it.name }

    /** All keywords for completion — NO `func` keyword */
    val ALL_KEYWORDS = listOf(
        "int", "float", "bool", "string", "void",
        "return", "if", "else", "while", "for", "foreach", "in",
        "switch", "case", "default",
        "struct", "enum", "class", "interface",
        "new", "override", "super", "import",
        "public", "private", "protected",
        "true", "false"
    )

    val TYPE_KEYWORDS    = listOf("int", "float", "bool", "string", "void")
    val ACCESS_MODIFIERS = listOf("public", "private", "protected")
}
