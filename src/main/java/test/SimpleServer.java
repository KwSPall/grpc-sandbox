package test;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import test.interceptor.SimpleServerInterceptor;

import java.io.File;
import java.io.IOException;

public class SimpleServer {

    private Server server;

    public SimpleServer() throws IOException {

        File certChain = new File("C:\\Users\\KwS\\repos\\grpc-key-management\\ssl\\server.crt");
        File privateKey = new File("C:\\Users\\KwS\\repos\\grpc-key-management\\ssl\\server.pem");

        ServerCredentials serverCredentials = TlsServerCredentials.create(certChain, privateKey);

        server = Grpc.newServerBuilderForPort(9001, serverCredentials)
                .addService(new SimpleImpl())
                .intercept(new SimpleServerInterceptor())
                .build()
                .start();

        System.out.println("Listening on port 9001");

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                SimpleServer.this.stop();
            }
        });
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final SimpleServer server = new SimpleServer(); //start our grpc server
        server.blockUntilShutdown();
    }

    static class SimpleImpl extends SimpleGrpc.SimpleImplBase {
        @Override
        public void firstRequest(SimpleOuterClass.SimpleMessage req, StreamObserver<SimpleOuterClass.SimpleMessage> responseObserver) {
            SimpleOuterClass.SimpleMessage reply = SimpleOuterClass.SimpleMessage.newBuilder().setText(req.getText() + " mirrored from server").build();
            System.out.println("pinged " + req.getText());
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

}
