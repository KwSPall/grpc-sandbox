package test.interceptor;

import com.google.protobuf.MessageLite;
import io.grpc.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class SimpleServerInterceptor implements ServerInterceptor {

    private String getHmac(byte[] data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {

        Metadata.Key<String> authorizationKey = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
        String token = metadata.get(authorizationKey);

        if (null == token) {
            serverCall.close(Status.UNAUTHENTICATED.withDescription("No token"), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        } else if (!token.startsWith("Bearer ")) {
            serverCall.close(Status.UNAUTHENTICATED.withDescription("Bad token"), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }

        ServerCallHandler<ReqT, RespT> newHandler = new ServerCallHandler<ReqT, RespT>() {
            @Override
            public ServerCall.Listener<ReqT> startCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata) {
                serverCall.request(1);
                return new ForwardingServerCallListener<ReqT>() {

                    private final ServerCall.Listener<ReqT> noop = new ServerCall.Listener<ReqT>() {};
                    private ServerCall.Listener<ReqT> delegate = noop;

                    @Override
                    protected ServerCall.Listener<ReqT> delegate() {
                        return delegate;
                    }

                    @Override
                    public void onMessage(ReqT message) {
                        if (delegate == noop) {
                            System.out.println("can calculate HMAC for message");
                            System.out.println(message);
                            //test hmac
                            try {
                                String clientHmac = token.substring("Bearer ".length());
                                String calculatedHmac = getHmac(((MessageLite) message).toByteArray(), "123456");
                                System.out.println(clientHmac);
                                System.out.println(calculatedHmac);
                                if (!clientHmac.equals(calculatedHmac)) {
                                    serverCall.close(Status.UNAUTHENTICATED.withDescription("wrong token"), new Metadata());
                                    return;
                                }
                            } catch (Exception e) {
                                serverCall.close(Status.UNAUTHENTICATED.withDescription("failed to calculate hmac"), new Metadata());
                                System.out.println("failed to calculate hmac");
                                return;
                            }
                            delegate = serverCallHandler.startCall(serverCall, metadata);
                        }
                        super.onMessage(message);
                    }
                };
            }
        };

        return newHandler.startCall(serverCall, metadata);

        //return Contexts.interceptCall(Context.current(), serverCall, metadata, serverCallHandler);
    }
}
