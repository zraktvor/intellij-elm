package org.elm.ide.http

import com.intellij.openapi.project.Project
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.builtInWebServer.WebServerPathHandlerAdapter
import org.jetbrains.io.send
import java.io.InputStream

class ElmWebServerPathHandler : WebServerPathHandlerAdapter() {

    // TODO [kl] both of these need to be more dynamic
    private val appHtmlPath = "example/index.html"
    private val appElmPath = "example/src/Main.elm"
    private val appJavascriptPath = "example/build/Main.js"

    override fun process(path: String, project: Project, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
        if (request.method() != HttpMethod.GET)
            return false

        val response = when {
            path == "runtime.js" -> bundledFileResponse("hot/runtime.js", "application/javascript", javaClass.classLoader)
            path == "index.html" -> userFileResponse(appHtmlPath, "text/html", project)
            path == "app.js" -> injectedJavascriptResponse(project)
            path == "stream-Main" -> watcherStreamResponse(project)
            else -> notFoundResponse("unknown route: $path")
        }
        response.send(context.channel(), request)
        return true
    }

    private fun injectedJavascriptResponse(project: Project): HttpResponse {
        val appJsContents = userFile(appJavascriptPath, project)
                ?.toString(Charsets.UTF_8)
                ?: return notFoundResponse("could not find $appJavascriptPath")

        val hmrContents = bundledFileTextStream("hot/hmr.js", javaClass.classLoader)
                ?.reader(Charsets.UTF_8)?.readText()
                ?: return notFoundResponse("could not find hot/hmr.js")

        // splice-in HMR javascript

        val regex = Regex("""(_Platform_export\(.*)(}\(this\)\);)""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(appJsContents)
                ?: return basicResponse(
                        HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        "Compiled JS from the Elm compiler is not valid. Version mismatch? (regex failed to match)")

        if (match.groups.size != 3)
            return basicResponse(
                    HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    "Compiled JS from the Elm compiler is not valid. Version mismatch? (incorrect # of capture groups)")

        val fullyInjectedCode =
                appJsContents.substring(0, match.groups[1]!!.range.start) +
                        match.groups[1]!!.value + "\n\n" +
                        hmrContents + "\n\n" +
                        match.groups[2]!!.value + "\n\n"

        return fileResponse(fullyInjectedCode.toByteArray(Charsets.UTF_8), "application/javascript")
    }

    private fun watcherStreamResponse(project: Project): HttpResponse {
        // TODO [kl] implement me
        return basicResponse(HttpResponseStatus.OK, "TODO TODO TODO")
    }
}


// GENERALLY USEFUL HTTP RESPONSES

private fun bundledFileResponse(path: String, contentType: String, classLoader: ClassLoader): HttpResponse {
    val file = bundledFileTextStream(path, classLoader)
            ?: return notFoundResponse()
    return fileResponse(file.readBytes(), contentType)
}

private fun userFileResponse(path: String, contentType: String, project: Project): HttpResponse {
    val fileContents = userFile(path, project)
            ?: return notFoundResponse()
    return fileResponse(fileContents, contentType)
}

private fun fileResponse(contents: ByteArray, contentType: String): HttpResponse {
    val status = HttpResponseStatus.OK
    val buffer = Unpooled.wrappedBuffer(contents)
    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer)
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
    return response
}

private fun notFoundResponse(message: String = "could not find file on disk") =
        basicResponse(HttpResponseStatus.NOT_FOUND, message)

private fun basicResponse(status: HttpResponseStatus, message: String): HttpResponse {
    val buffer = Unpooled.copiedBuffer(message, Charsets.UTF_8)
    return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer)
}


// UTIL

private fun bundledFileTextStream(path: String, classLoader: ClassLoader): InputStream? {
    val stream: InputStream? = classLoader.getResourceAsStream(path)
    return stream
}

private fun userFile(path: String, project: Project): ByteArray? =
        project.baseDir.findFileByRelativePath(path)?.contentsToByteArray()
