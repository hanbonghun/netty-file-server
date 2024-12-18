package org.example.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private final String uploadDir;
    private static final HttpDataFactory factory = new DefaultHttpDataFactory(true);
    private HttpPostRequestDecoder decoder;

    public FileServerHandler(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;

            if (request.method() == HttpMethod.GET) {
                handleDownload(ctx, request);
            } else if (request.method() == HttpMethod.POST) {
                decoder = new HttpPostRequestDecoder(factory, request);
                handleUpload(ctx, request);
            }
        }

        if (decoder != null && msg instanceof HttpContent) {
            decoder.offer((HttpContent) msg);

            if (msg instanceof LastHttpContent) {
                writeResponse(ctx, HttpResponseStatus.OK);
                reset();
            }
        }
    }

    private void handleDownload(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
        String uri = request.uri();
        String path = URLDecoder.decode(uri, "UTF-8");

        if (path.contains("../")) {
            writeResponse(ctx, HttpResponseStatus.FORBIDDEN);
            return;
        }

        Path filePath = Paths.get(uploadDir, path);
        if (!Files.exists(filePath)) {
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }

        File file = filePath.toFile();
        long fileLength = file.length();

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(fileLength));
        response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION,
            "attachment; filename=\"" + file.getName() + "\"");

        ctx.write(response);

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = fis.read(buffer)) != -1) {
            ctx.write(Unpooled.wrappedBuffer(buffer, 0, bytesRead));
        }

        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
            .addListener(ChannelFutureListener.CLOSE);

        fis.close();
    }

    private void handleUpload(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
        writeResponse(ctx, HttpResponseStatus.NOT_IMPLEMENTED);
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpResponseStatus status) {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void reset() {
        if (decoder != null) {
            decoder.destroy();
            decoder = null;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.writeAndFlush(new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.INTERNAL_SERVER_ERROR))
            .addListener(ChannelFutureListener.CLOSE);
    }
}