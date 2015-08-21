package com.xjeffrose.xio.processor;

public class XioProcessorAdapter {
//    /**
//     * Adapt a {@link TProcessor} to a standard Thrift {@link NiftyProcessor}. Nifty uses this
//     * internally to adapt the processors generated by the standard Thrift code generator into
//     * instances of {@link NiftyProcessor} usable by {@link com.facebook.nifty.core.NiftyDispatcher}
//     */
//    public static NiftyProcessor processorFromTProcessor ( final TProcessor standardThriftProcessor)
//    {
//      checkProcessMethodSignature();
//
//      return new NiftyProcessor() {
//        @Override
//        public ListenableFuture<Boolean> process(TProtocol in, TProtocol out, RequestContext requestContext) throws TException {
//          return Futures.immediateFuture(standardThriftProcessor.process(in, out));
//        }
//      };
//    }
//
//    /**
//     * Create a {@link NiftyProcessorFactory} that always returns the same {@link NiftyProcessor}
//     * adapted from the given standard Thrift {@link TProcessor}
//     */
//
//  public static NiftyProcessorFactory factoryFromTProcessor(final TProcessor standardThriftProcessor) {
//    checkProcessMethodSignature();
//
//    return new NiftyProcessorFactory() {
//      @Override
//      public NiftyProcessor getProcessor(TTransport transport) {
//        return processorFromTProcessor(standardThriftProcessor);
//      }
//    };
//  }
//
//  /**
//   * Create a {@link NiftyProcessorFactory} that delegates to a standard Thrift {@link
//   * TProcessorFactory} to construct an instance, then adapts each instance to a {@link
//   * NiftyProcessor}
//   */
//  public static NiftyProcessorFactory factoryFromTProcessorFactory(final TProcessorFactory standardThriftProcessorFactory) {
//    checkProcessMethodSignature();
//
//    return new NiftyProcessorFactory() {
//      @Override
//      public NiftyProcessor getProcessor(TTransport transport) {
//        return processorFromTProcessor(standardThriftProcessorFactory.getProcessor
//            (transport));
//      }
//    };
//  }
//
//  /**
//   * Adapt a {@link NiftyProcessor} to a standard Thrift {@link TProcessor}. The {@link
//   * com.facebook.nifty.core.NiftyRequestContext} will always be {@code null}
//   */
//  public static TProcessor processorToTProcessor(final NiftyProcessor niftyProcessor) {
//    return new TProcessor() {
//      @Override
//      public boolean process(TProtocol in, TProtocol out) throws TException {
//        try {
//          return niftyProcessor.process(in, out, null).get();
//        } catch (InterruptedException e) {
//          Thread.currentThread().interrupt();
//          throw new TException(e);
//        } catch (ExecutionException e) {
//          throw new TException(e);
//        }
//      }
//    };
//  }
//
//  /**
//   * Create a standard thrift {@link TProcessorFactory} that always returns the same {@link
//   * TProcessor} adapted from the given {@link NiftyProcessor}
//   */
//  public static TProcessorFactory processorToTProcessorFactory(final NiftyProcessor niftyProcessor) {
//    return new TProcessorFactory(processorToTProcessor(niftyProcessor));
//  }
//
//  /**
//   * Create a standard thrift {@link TProcessorFactory} that delegates to a {@link
//   * NiftyProcessorFactory} to construct an instance, then adapts each instance to a standard Thrift
//   * {@link TProcessor}
//   */
//  public static TProcessorFactory processorFactoryToTProcessorFactory(final NiftyProcessorFactory niftyProcessorFactory) {
//    return new TProcessorFactory(null) {
//      @Override
//      public TProcessor getProcessor(TTransport trans) {
//        return processorToTProcessor(niftyProcessorFactory.getProcessor(trans));
//      }
//    };
//  }
//
//  /**
//   * Catch the mismatch early if someone tries to pass our internal variant of TProcessor with a
//   * different signature on the process() method into these adapters.
//   */
//  private static void checkProcessMethodSignature() {
//    try {
//      TProcessor.class.getMethod("process", TProtocol.class, TProtocol.class);
//    } catch (NoSuchMethodException e) {
//      // Facebook's TProcessor variant needs processor adapters from a different package
//      throw new IllegalStateException("The loaded TProcessor class is not supported by version of the adapters");
//    }
//  }
}
