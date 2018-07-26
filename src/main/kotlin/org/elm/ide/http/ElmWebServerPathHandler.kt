package org.elm.ide.http

import com.intellij.openapi.project.Project
import com.intellij.util.io.addChannelListener
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import io.netty.handler.codec.http.*
import org.jetbrains.builtInWebServer.WebServerPathHandlerAdapter
import org.jetbrains.io.send
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

class ElmWebServerPathHandler : WebServerPathHandlerAdapter() {

    private var prevModified: Long = 0

    // TODO [kl] all of these need to be more dynamic/contextual
    private val appHtmlPath = "example/index.html"
    private val appElmPath = "example/src/Main.elm"
    private val appJavascriptPath = "example/build/Main.js"

    override fun process(path: String, project: Project, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
        if (request.method() != HttpMethod.GET)
            return false

        if (path == "stream-Main") {
            handleWatcherStream(project, context)
        }

        val response = when {
            path == "runtime.js" -> bundledFileResponse("hot/runtime.js", "application/javascript", javaClass.classLoader)
            path == "index.html" -> userFileResponse(appHtmlPath, "text/html", project)
            path == "Main.js" -> injectedJavascriptResponse(project)
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

    private fun handleWatcherStream(project: Project, context: ChannelHandlerContext) {
        val channel = context.channel()
        val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "text/event-stream")

        channel.write(response)
        channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)

        // For same unknown reason, if you don't register this encoder, then the
        // write will fail with error "io.netty.handler.codec.EncoderException [...] unexpected message type".
        // Maybe it's something to do with how JetBrains configured Netty?
        // TODO [kl] is it ok to register this multiple times?
        channel.pipeline().addFirst(object : MessageToByteEncoder<String>() {
            override fun encode(ctx: ChannelHandlerContext, msg: String, out: ByteBuf) {
                out.writeCharSequence(msg, Charsets.UTF_8)
            }
        })

        val jsPath = Paths.get(project.basePath).resolve(appJavascriptPath)

        if (prevModified == 0L)
            prevModified = getLastModifiedTime(jsPath)

        val runnable = Runnable {
            val lastModified = getLastModifiedTime(jsPath)
            if (lastModified == prevModified)
                return@Runnable
            prevModified = lastModified
            val msg = "data: Main.js\n\n"
            channel.writeAndFlush(msg)
                    .addChannelListener {
                        when {
                            it.isSuccess -> println("write succeeded")
                            it.isCancelled -> println("write cancelled")
                            it.isDone && it.cause() != null -> {
                                println("write error: ${it.cause()}")
                                it.cause().printStackTrace()
                            }
                        }
                    }
        }

        // TODO [kl] async file watcher instead of polling
        context.channel().eventLoop().scheduleAtFixedRate(runnable, 1, 1, TimeUnit.SECONDS)
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

private fun getLastModifiedTime(path: Path?): Long {
    val time = Files.readAttributes(path, "lastModifiedTime")["lastModifiedTime"] as FileTime
    return time.toMillis()
}