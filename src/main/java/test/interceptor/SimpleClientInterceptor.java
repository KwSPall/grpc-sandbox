package test.interceptor;

import io.grpc.*;

public class SimpleClientInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {
        System.out.println("intercepting");
        return new ForwardingClientCall<ReqT, RespT>() {
            private ClientCall<ReqT, RespT> delegate;
            private Metadata headers;
            private Listener<RespT> responseListener;
            private int requestTokens;

            @Override protected ClientCall<ReqT, RespT> delegate() {
                if (delegate == null) {
                    delegate = channel.newCall(methodDescriptor, callOptions);
                }
                return delegate;
            }

            @Override
            public void request(int requests) {
                if (delegate == null) {
                    requestTokens += requests;
                    return;
                }
                super.request(requests);
            }

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                this.headers = headers;
                this.responseListener = responseListener;
            }

            @Override
            public void sendMessage(ReqT message) {

                super.start(new CachingListener<>(responseListener), headers);
                if (requestTokens > 0) {
                    super.request(requestTokens);
                    requestTokens = 0;
                }
                super.sendMessage(message);
            }
        };
    }

    private class CachingListener<RespT> extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {

        public CachingListener(ClientCall.Listener<RespT> delegate) {
            super(delegate);
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            super.onClose(status, trailers);
        }

        @Override
        public void onMessage(RespT message) {
            super.onMessage(message);
        }
    }
}
