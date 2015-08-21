package com.xjeffrose.xio.client;

import io.airlift.log.Logger;
import io.airlift.units.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioSocketChannel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.timeout.ReadTimeoutException;
import org.jboss.netty.handler.timeout.WriteTimeoutException;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

public class XioClientChannelImpl extends SimpleChannelHandler implements XioClientChannel {
  private static final Logger LOGGER = Logger.get(XioClientChannelImpl.class);

  private final Channel nettyChannel;
  private final Map<Integer, Request> requestMap = new HashMap<>();
  //  private volatile TException channelError;
  private final Timer timer;
  private Duration sendTimeout = null;
  // Timeout until the whole request must be received.
  private Duration receiveTimeout = null;
  // Timeout for not receiving any data from the server
  private Duration readTimeout = null;
//  private final TDuplexProtocolFactory protocolFactory;
  private AtomicInteger sequenceId = new AtomicInteger();

  protected XioClientChannelImpl(Channel nettyChannel, Timer timer) {
    this.nettyChannel = nettyChannel;
    this.timer = timer;
  }

  @Override
  public Channel getNettyChannel() {
    return nettyChannel;
  }

  protected ChannelBuffer extractResponse(Object message) {

    return null;
  }

  protected ChannelFuture writeRequest(Object request) {
    return getNettyChannel().write(request);
  }

  public void close() {
    getNettyChannel().close();
  }

  @Override
  public Duration getSendTimeout() {
    return sendTimeout;
  }

  @Override
  public void setSendTimeoutDuration(Duration sendTimeout) {

  }

//  @Override
//  public void setSendTimeout(Duration sendTimeout) {
//    this.sendTimeout = sendTimeout;
//  }

  @Override
  public Duration getReceiveTimeout() {
    return receiveTimeout;
  }

  @Override
  public void setReceiveTimeout(Duration receiveTimeout) {
    this.receiveTimeout = receiveTimeout;
  }

  @Override
  public Duration getReadTimeout() {
    return this.readTimeout;
  }

  @Override
  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout;
  }

  @Override
  public boolean hasError() {
//    return channelError != null;
    return false;
  }


  @Override
  public void executeInIoThread(Runnable runnable) {
    NioSocketChannel nioSocketChannel = (NioSocketChannel) getNettyChannel();
    nioSocketChannel.getWorker().executeInIoThread(runnable, true);
  }

  @Override
  public void sendAsynchronousRequest(final HttpRequest message,
                                      final boolean oneway,
                                      final Listener listener) {
//    final int sequenceId = extractSequenceId(message);

    // Ensure channel listeners are always called on the channel's I/O thread
    executeInIoThread(new Runnable() {
      @Override
      public void run() {
        try {
          final Request request = makeRequest(sequenceId.getAndIncrement(), listener);

          if (!nettyChannel.isConnected()) {
            fireChannelErrorCallback(listener, new XioClientException("Channel closed"));
            return;
          }

          if (hasError()) {
            fireChannelErrorCallback(
                listener,
                new XioClientException("Channel is in a bad state due to failing a previous request"));
            return;
          }

          ChannelFuture sendFuture = writeRequest(message);
          queueSendTimeout(request);

          sendFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
              messageSent(future, request, oneway);
            }
          });
        } catch (Throwable t) {
          // onError calls all registered listeners in the requestMap, but this request
          // may not be registered yet. So we try to remove it (to make sure we don't call
          // the callback twice) and then manually make the callback for this request
          // listener.
//          requestMap.remove(sequenceId);
          fireChannelErrorCallback(listener, t);

          onError(t);
        }
      }
    });
  }

  private void messageSent(ChannelFuture future, Request request, boolean oneway) {
    try {
      if (future.isSuccess()) {
        cancelRequestTimeouts(request);
        fireRequestSentCallback(request.getListener());
        if (oneway) {
          retireRequest(request);
        } else {
          queueReceiveAndReadTimeout(request);
        }
      } else {
//        TTransportException transportException =
//            new TTransportException("Sending request failed",
                future.getCause();
//        onError(transportException);
      }
    } catch (Throwable t) {
      onError(t);
    }
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
    try {
      ChannelBuffer response = extractResponse(e.getMessage());

      if (response != null) {
        onResponseReceived(sequenceId.get(), response);
      } else {
        ctx.sendUpstream(e);
      }
    } catch (Throwable t) {
      onError(t);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent event)
      throws Exception {
    Throwable t = event.getCause();
    onError(t);
  }

  private Request makeRequest(int sequenceId, Listener listener) {
    Request request = new Request(listener);
    requestMap.put(sequenceId, request);
    return request;
  }

  private void retireRequest(Request request) {
    cancelRequestTimeouts(request);
  }

  private void cancelRequestTimeouts(Request request) {
    Timeout sendTimeout = request.getSendTimeout();
    if (sendTimeout != null && !sendTimeout.isCancelled()) {
      sendTimeout.cancel();
    }

    Timeout receiveTimeout = request.getReceiveTimeout();
    if (receiveTimeout != null && !receiveTimeout.isCancelled()) {
      receiveTimeout.cancel();
    }

    Timeout readTimeout = request.getReadTimeout();
    if (readTimeout != null && !readTimeout.isCancelled()) {
      readTimeout.cancel();
    }
  }

  private void cancelAllTimeouts() {
    for (Request request : requestMap.values()) {
      cancelRequestTimeouts(request);
    }
  }

  private void onResponseReceived(int sequenceId, ChannelBuffer response) {
    Request request = requestMap.remove(sequenceId);
    if (request == null) {
      onError(new XioClientException("Bad sequence id in response: " + sequenceId));
    } else {
      retireRequest(request);
      fireResponseReceivedCallback(request.getListener(), response);
    }
  }

  @Override
  public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    if (!requestMap.isEmpty()) {
      onError(new XioClientException("Client was disconnected by server"));
    }
  }

  protected void onError(Throwable t) {
//    Exception wrappedException = wrapException(t);
//
//    if (channelError == null) {
//      channelError = wrappedException;
//    }

    cancelAllTimeouts();

    Collection<Request> requests = new ArrayList<>();
    requests.addAll(requestMap.values());
    requestMap.clear();
    for (Request request : requests) {
      fireChannelErrorCallback(request.getListener(), t);
    }

    Channel channel = getNettyChannel();
    if (nettyChannel.isOpen()) {
      channel.close();
    }
  }

  private void fireRequestSentCallback(Listener listener) {
    try {
      listener.onRequestSent();
    } catch (Throwable t) {
      LOGGER.warn(t, "Request sent listener callback triggered an exception");
    }
  }

  private void fireResponseReceivedCallback(Listener listener, ChannelBuffer response) {
    try {
      listener.onResponseReceived(response);
    } catch (Throwable t) {
      LOGGER.warn(t, "Response received listener callback triggered an exception");
    }
  }

  private void fireChannelErrorCallback(Listener listener, Exception exception) {
    try {
      listener.onChannelError(exception);
    } catch (Throwable t) {
      LOGGER.warn(t, "Channel error listener callback triggered an exception");
    }
  }

  private void fireChannelErrorCallback(Listener listener, Throwable throwable) {
    fireChannelErrorCallback(listener,throwable);
  }

  private void onSendTimeoutFired(Request request) {
    cancelAllTimeouts();
    WriteTimeoutException timeoutException = new WriteTimeoutException("Timed out waiting " + getSendTimeout() + " to send data to server");
    fireChannelErrorCallback(request.getListener(), new XioClientException("", timeoutException));
  }

  private void onReceiveTimeoutFired(Request request) {
    cancelAllTimeouts();
    ReadTimeoutException timeoutException = new ReadTimeoutException("Timed out waiting " + getReceiveTimeout() + " to receive response");
    fireChannelErrorCallback(request.getListener(), new XioClientException("", timeoutException));
  }

  private void onReadTimeoutFired(Request request) {
    cancelAllTimeouts();
    ReadTimeoutException timeoutException = new ReadTimeoutException("Timed out waiting " + getReadTimeout() + " to read data from server");
    fireChannelErrorCallback(request.getListener(), new XioClientException("", timeoutException));
  }


  private void queueSendTimeout(final Request request) throws XioClientException {
    if (this.sendTimeout != null) {
      long sendTimeoutMs = this.sendTimeout.toMillis();
      if (sendTimeoutMs > 0) {
        TimerTask sendTimeoutTask = new IoThreadBoundTimerTask(this, new TimerTask() {
          @Override
          public void run(Timeout timeout) {
            onSendTimeoutFired(request);
          }
        });

        Timeout sendTimeout;
        try {
          sendTimeout = timer.newTimeout(sendTimeoutTask, sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (IllegalStateException e) {
          throw new XioClientException("Unable to schedule send timeout");
        }
        request.setSendTimeout(sendTimeout);
      }
    }
  }

  private void queueReceiveAndReadTimeout(final Request request) throws XioClientException {
    if (this.receiveTimeout != null) {
      long receiveTimeoutMs = this.receiveTimeout.toMillis();
      if (receiveTimeoutMs > 0) {
        TimerTask receiveTimeoutTask = new IoThreadBoundTimerTask(this, new TimerTask() {
          @Override
          public void run(Timeout timeout) {
            onReceiveTimeoutFired(request);
          }
        });

        Timeout timeout;
        try {
          timeout = timer.newTimeout(receiveTimeoutTask, receiveTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (IllegalStateException e) {
          throw new XioClientException("Unable to schedule request timeout");
        }
        request.setReceiveTimeout(timeout);
      }
    }

    if (this.readTimeout != null) {
      long readTimeoutNanos = this.readTimeout.roundTo(TimeUnit.NANOSECONDS);
      if (readTimeoutNanos > 0) {
//        TimerTask readTimeoutTask = new IoThreadBoundTimerTask(this, new ReadTimeoutTask(readTimeoutNanos, request));

//        Timeout timeout;
        try {
//          timeout = timer.newTimeout(readTimeoutTask, readTimeoutNanos, TimeUnit.NANOSECONDS);
        } catch (IllegalStateException e) {
          throw new XioClientException("Unable to schedule read timeout");
        }
//        request.setReadTimeout(timeout);
      }
    }
  }


  /**
   * Used to create TimerTasks that will fire
   */
  private static class IoThreadBoundTimerTask implements TimerTask {
    private final XioClientChannel channel;
    private final TimerTask timerTask;

    public IoThreadBoundTimerTask(XioClientChannel channel, TimerTask timerTask) {
      this.channel = channel;
      this.timerTask = timerTask;
    }

    @Override
    public void run(final Timeout timeout)
        throws Exception {
      channel.executeInIoThread(new Runnable() {
        @Override
        public void run() {
          try {
            timerTask.run(timeout);
          } catch (Exception e) {
            Channels.fireExceptionCaught(channel.getNettyChannel(), e);
          }
        }
      });
    }
  }

  /**
   * Bundles the details of a client request that has started, but for which a response hasn't yet
   * been received (or in the one-way case, the send operation hasn't completed yet).
   */
  private static class Request {
    private final Listener listener;
    private Timeout sendTimeout;
    private Timeout receiveTimeout;

    private volatile Timeout readTimeout;

    public Request(Listener listener) {
      this.listener = listener;
    }

    public Listener getListener() {
      return listener;
    }

    public Timeout getReceiveTimeout() {
      return receiveTimeout;
    }

    public void setReceiveTimeout(Timeout receiveTimeout) {
      this.receiveTimeout = receiveTimeout;
    }

    public Timeout getReadTimeout() {
      return readTimeout;
    }

    public void setReadTimeout(Timeout readTimeout) {
      this.readTimeout = readTimeout;
    }

    public Timeout getSendTimeout() {
      return sendTimeout;
    }

    public void setSendTimeout(Timeout sendTimeout) {
      this.sendTimeout = sendTimeout;
    }
  }

//  private final class ReadTimeoutTask implements TimerTask {
//    private final TimeoutHandler timeoutHandler;
//    private final long timeoutNanos;
//    private final Request request;
//
//    ReadTimeoutTask(long timeoutNanos, Request request) {
//      this.timeoutHandler = TimeoutHandler.findTimeoutHandler(getNettyChannel().getPipeline());
//      this.timeoutNanos = timeoutNanos;
//      this.request = request;
//    }
//
//    public void run(Timeout timeout) throws Exception {
//      if (timeoutHandler == null) {
//        return;
//      }
//
//      if (timeout.isCancelled()) {
//        return;
//      }
//
//      if (!getNettyChannel().isOpen()) {
//        return;
//      }
//
//      long currentTimeNanos = System.nanoTime();
//
//      long timePassed = currentTimeNanos - timeoutHandler.getLastMessageReceivedNanos();
//      long nextDelayNanos = timeoutNanos - timePassed;
//
//      if (nextDelayNanos <= 0) {
//        onReadTimeoutFired(request);
//      } else {
//        request.setReadTimeout(timer.newTimeout(this, nextDelayNanos, TimeUnit.NANOSECONDS));
//      }
//    }
//  }


}
