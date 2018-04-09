package com.xjeffrose.xio.http;

import com.xjeffrose.xio.tracing.XioTracing;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.PromiseCombiner;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
public class Client {

  private final ClientState state;
  private final ClientChannelInitializer clientChannelInitializer;
  private final ChannelFutureListener connectionListener;
  private final ChannelFutureListener writeListener;
  private Channel channel;
  private ChannelFuture connectionFuture;

  public Client(ClientState state, Supplier<ChannelHandler> appHandler, XioTracing tracing) {
    this.state = state;
    this.clientChannelInitializer = new ClientChannelInitializer(state, appHandler, tracing);

    connectionListener =
        f -> {
          if (f.isDone() && f.isSuccess()) {
            log.debug("Connection succeeded");
          } else {
            log.debug("Connection failed", f.cause());
          }
        };
    writeListener =
        f -> {
          if (f.isDone() && f.isSuccess()) {
            log.debug("Write succeeded");
          } else {
            log.debug("Write failed", f.cause());
            log.debug("pipeline: {}", channel.pipeline());
          }
        };
  }

  private ChannelFuture internalConnect() {
    Bootstrap b = new Bootstrap();
    b.channel(state.channelConfig.channel());
    b.group(state.channelConfig.workerGroup());
    b.handler(clientChannelInitializer);
    ChannelFuture connectFuture = b.connect(state.remote);
    channel = connectFuture.channel();
    return connectFuture;
  }

  public ChannelFuture connectAndWrite(Request request) {
    if (channel == null) {
      ChannelFuture future = internalConnect();
      ChannelPromise promise = channel.newPromise();
      PromiseCombiner combiner = new PromiseCombiner();
      combiner.add(future.addListener(connectionListener));
      combiner.add(channel.writeAndFlush(request).addListener(writeListener));
      combiner.finish(promise);
      return promise;
    } else {
      return channel.writeAndFlush(request).addListener(writeListener);
    }
  }

  public CompletableFuture<ClientChannelResponse> connect() {
    val outerResult = new CompletableFuture<ClientChannelResponse>();
    internalConnect().addListeners(connectionListener, (resultFuture) -> {
      val response = new ClientChannelResponse(resultFuture.isDone(), resultFuture.isSuccess(), resultFuture.cause());
      outerResult.complete(response);
    });
    return outerResult;
  }

  public CompletableFuture<ClientChannelResponse> write(Request request) {
    val outerResult = new CompletableFuture<ClientChannelResponse>();
    if (channel == null) {
      outerResult.complete(new ClientChannelResponse(false, false, new Throwable("No channel exists yet")));
    } else {
      channel.writeAndFlush(request).addListeners(writeListener, (resultFuture) -> {
        val response = new ClientChannelResponse(resultFuture.isDone(), resultFuture.isSuccess(), resultFuture.cause());
        outerResult.complete(response);
      });
    }
    return outerResult;
  }
}
