package org.frawa.elmtest.core.json

import com.google.gson.JsonElement

class CompileErrors {
    var type: String? = null
    var errors: List<Error>? = null
}

class Error {
    var path: String? = null
    var name: String? = null
    var problems: List<Problem>? = null
}

class Problem {
    var title: String? = null
    var region: Region? = null
    var message: List<JsonElement>? = null

    val textMessage: String
        get() {
            return message
                    ?.filter { it.isJsonPrimitive || it.isJsonObject && it.asJsonObject.has("string") }
                    ?.map {
                        if (it.isJsonPrimitive)
                            it.asString
                        else
                            it.asJsonObject.get("string").asString
                    }
                    ?.joinToString("")
                    ?: "UNKNOWN"
        }


}

class Position {
    var line: Int = 0
    var column: Int = 0
}

class Region {
    var start: Position? = null
    var end: Position? = null
}

