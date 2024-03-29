package com.zoecll.kvstorage;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.SettableFuture;
import com.zoecll.config.PeerInfo;

import io.grpc.stub.StreamObserver;
import protobuf.KvStorageGrpc;
import protobuf.KvStorageGrpc.KvStorageStub;
import protobuf.KvStorageProto.GetRequest;
import protobuf.KvStorageProto.GetResponse;
import protobuf.KvStorageProto.SetRequest;
import protobuf.KvStorageProto.SetResponse;

public class KvClient {

    private final static Logger logger = LoggerFactory.getLogger(KvClient.class);

    private ArrayList<PeerInfo> peers;
    private int leaderId;

    public KvClient(ArrayList<PeerInfo> peers) {
        this.peers = peers;
        this.leaderId = 0;
    }

    public String getNoRetry(String key) {
        GetRequest request = GetRequest.newBuilder().setKey(key).build();
        final KvStorageStub asyncClient = KvStorageGrpc.newStub(peers.get(0).getKvChannel());
        SettableFuture<GetResponse> responseFuture = SettableFuture.create();
        asyncClient.get(request, new StreamObserver<GetResponse>() {
            @Override
            public void onNext(GetResponse value) {
                responseFuture.set(value);
            }

            @Override
            public void onError(Throwable t) {
                responseFuture.setException(t);
            }

            @Override
            public void onCompleted() {

            }
        });

        try {
            GetResponse response = responseFuture.get();
            if (response.getOk()) {
                return response.getValue();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean setNoRetry(String key, String value) {
        SetRequest request = SetRequest.newBuilder().setKey(key).setValue(value).build();
        final KvStorageStub asyncClient = KvStorageGrpc.newStub(peers.get(leaderId).getKvChannel());
        SettableFuture<SetResponse> responseFuture = SettableFuture.create();
        asyncClient.set(request, new StreamObserver<SetResponse>() {
            @Override
            public void onNext(SetResponse value) {
                responseFuture.set(value);
            }

            @Override
            public void onError(Throwable t) {
                responseFuture.setException(t);
            }

            @Override
            public void onCompleted() {

            }
        });

        try {
            SetResponse response = responseFuture.get();
            if (response.getOk()) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    
    public String get(String key) {
        while (true) {
            String res = getNoRetry(key);
            if (res != null) {
                return res;
            }
            leaderId = (leaderId + 1) % peers.size();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("[KvClient] retry get, key: {}", key);
        }
    }

    public boolean set(String key, String value) {
        while (!setNoRetry(key, value)) {
            leaderId = (leaderId + 1) % peers.size();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("[KvClient] retry set, key: {}, value: {}", key, value);
        }
        return true;
    }
    
}
