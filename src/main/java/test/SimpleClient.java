package test;

import io.grpc.*;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class SimpleClient {

    private final SimpleGrpc.SimpleBlockingStub blockingStub;

    public SimpleClient(Channel channel) {
        blockingStub = SimpleGrpc.newBlockingStub(channel);
    }

    public void exchangeMessage(String message) throws StatusRuntimeException {
        SimpleOuterClass.SimpleMessage simpleMessageRequest = SimpleOuterClass.SimpleMessage.newBuilder().setText(message).build();
        SimpleOuterClass.SimpleMessage simpleMessageResponse;
        simpleMessageResponse = blockingStub.firstRequest(simpleMessageRequest);
        //System.out.println(simpleMessageResponse.getText());
    }

    public static void main(String[] args) throws Exception {
        //standard ca route
        File ca = new File("C:\\Users\\KwS\\repos\\grpc-key-management\\ssl\\ca.crt");
        //preshared key
        File certChain = new File("C:\\Users\\KwS\\repos\\grpc-key-management\\ssl\\server.crt");
        TlsChannelCredentials.Builder tlsBuilder = TlsChannelCredentials.newBuilder()
                .trustManager(ca)
                .keyManager();
        ManagedChannel channel = Grpc.newChannelBuilderForAddress("localhost", 9001, tlsBuilder.build())
                .build();
        SimpleClient client = new SimpleClient(channel);
        long start = System.currentTimeMillis();
        int N = 100000;
        try {
            for (int i = 0; i < N; i++) {
                client.exchangeMessage("message" + i);
            }
        } catch (Exception e) {
            System.out.println("ending");
            e.printStackTrace();
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
        long end = System.currentTimeMillis();
        System.out.println("N = " + N + " requests took " + (end - start) + " milliseconds");
    }

}
