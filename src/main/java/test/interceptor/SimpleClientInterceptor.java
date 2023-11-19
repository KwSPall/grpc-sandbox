package test.interceptor;

import com.google.protobuf.MessageLite;
import io.grpc.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

//https://github.com/grpc/grpc-java/issues/4684
public class SimpleClientInterceptor implements ClientInterceptor {

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
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {
        System.out.println("intercepting");
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(channel.newCall(methodDescriptor, callOptions)) {

            private String token = "4444";

            private boolean started = false;
            private int messagesCount;

            private Listener<RespT> responseListener;
            private Metadata headers;

            @Override
            public void start(Listener <RespT> responseListener, Metadata headers) {
                this.headers = headers;
                this.responseListener = responseListener;

                //put custom header
                /*
                Metadata headersWithHmac = new Metadata();
                Metadata.Key<String> authorizationKey = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
                headersWithHmac.put(authorizationKey, "Bearer " + token);

                headers.merge(headersWithHmac);

                super.start(responseListener, headers);
                */
            }

            @Override
            public void sendMessage(ReqT message) {
                System.out.println("can calculate HMAC for message");
                System.out.println(message);
                //test hmac
                try {
                    token = getHmac(((MessageLite) message).toByteArray(), "123456");
                    System.out.println(token);
                } catch (Exception e) {
                    System.out.println("failed to calculate hmac");
                    //fail message?
                }

                Metadata.Key<String> authorizationKey = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
                headers.put(authorizationKey, "Bearer " + token);

                super.start(responseListener, headers);
                if (!started) {
                    super.request(messagesCount);
                    started = true;
                }

                super.sendMessage(message);
            }

            @Override
            public void request(int numMessages) {
                if (started) {
                    super.request(numMessages);
                } else {
                    this.messagesCount = numMessages;
                }
            }

        };
    }
}
