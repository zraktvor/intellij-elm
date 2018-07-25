package org.elm.ide.http

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.builtInWebServer.WebServerPathHandler
import org.jetbrains.builtInWebServer.WebServerPathHandlerAdapter
import org.jetbrains.io.send

class ElmWebServerPathHandler: WebServerPathHandlerAdapter() {

    override fun process(path: String, project: Project, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {

        val file = project.baseDir.findFileByRelativePath(path)
        if (file == null) {
            sendStatus(HttpResponseStatus.NOT_FOUND, "could not find file", request, context)
            return true
        }

        val status = HttpResponseStatus.OK
        val buffer = Unpooled.wrappedBuffer(file.contentsToByteArray())
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer)

        val contentType: String? = "text/plain"
        if (contentType != null) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
        }
        response.send(context.channel(), request)
        return true
    }

    private fun sendStatus(status: HttpResponseStatus, message: String, request: FullHttpRequest, context: ChannelHandlerContext) {
        val buffer = Unpooled.copiedBuffer(message, Charsets.UTF_8)
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer)
        response.send(context.channel(), request)
    }
}