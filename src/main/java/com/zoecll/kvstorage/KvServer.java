package com.zoecll.kvstorage;

import java.util.HashMap;
import java.util.UUID;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zoecll.persistence.FilePersister;
import com.zoecll.raftrpc.RaftNode;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import lombok.Synchronized;
import protobuf.KvStorageGrpc.KvStorageImplBase;
import protobuf.KvStorageProto.GetRequest;
import protobuf.KvStorageProto.GetResponse;
import protobuf.KvStorageProto.SetRequest;
import protobuf.KvStorageProto.SetResponse;

public class KvServer extends KvStorageImplBase {

    private final static Logger logger = LoggerFactory.getLogger(KvServer.class);

    private RaftNode raftNode;
    private HashMap<String, SimpleEntry<String, String>> data = new HashMap<>();
    private ReentrantLock mutex = new ReentrantLock();

    public KvServer(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        logger.debug("[Raft node {}] Received get request", raftNode.getId());
        GetResponse.Builder builder = GetResponse.newBuilder();

        String key = request.getKey();
        mutex.lock();
        String value = data.get(key).getKey();
        mutex.unlock();

        if (value == null) {
            responseObserver.onNext(builder.setOk(false).build());
            responseObserver.onCompleted();
            return;
        }

        responseObserver.onNext(builder.setOk(true).setValue(value).build());
        responseObserver.onCompleted();
        logger.debug("[Raft node {}] Get request completed", raftNode.getId());
    }

    @Override
    public void set(SetRequest request, StreamObserver<SetResponse> responseObserver) {
        logger.debug("[Raft node {}] Received set request", raftNode.getId());

        SetResponse.Builder builder = SetResponse.newBuilder();

        String key = request.getKey();
        String value = request.getValue();
        String uuid = UUID.randomUUID().toString();
        KvCommand command = new KvCommand(KvCommand.Type.SET, key, value, uuid);
        try {
            String json = new ObjectMapper().writeValueAsString(command);
            if (!raftNode.appendEntry(json)) {
                logger.info("Append entry failed at follower.");
                responseObserver.onNext(builder.setOk(false).build());
                responseObserver.onCompleted();
                return;
            }
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize command: {}", command);
            responseObserver.onNext(builder.setOk(false).build());
            responseObserver.onCompleted();
        }

        mutex.lock();
        SimpleEntry<String, String> res = data.get(key);
        mutex.unlock();
        while (res == null || !res.getValue().equals(uuid)) {
            try {
                Thread.sleep(500);
                mutex.lock();
                res = data.get(key);
                mutex.unlock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        responseObserver.onNext(builder.setOk(true).build());
        responseObserver.onCompleted();
        logger.info("[Raft node {}] Set request completed", raftNode.getId());
    }
    
    public void start() {
        Server kvServer = ServerBuilder.forPort(raftNode.getPeers().get(raftNode.getId()).getKvPort()).addService(this).build();
        try {
            kvServer.start();
            kvServer.awaitTermination();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Synchronized("mutex")
    public void applyLog(String command) {
        try {
            KvCommand kvCommand = new ObjectMapper().readValue(command, KvCommand.class);
            if (kvCommand.getType() == KvCommand.Type.SET) {
                data.put(kvCommand.getKey(), new SimpleEntry<>(kvCommand.getValue(), kvCommand.getUuid()));
            }
            logger.debug("[Raft node {}] Applied log: {}", raftNode.getId(), command);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to deserialize command: {}", command);
            e.printStackTrace();
        }
    }

    @Synchronized("mutex")
    public void reset(FilePersister persister) {
        data.clear();
        persister.readSnapshot();
        ArrayList<String> commands = persister.getCommands();
        for (String command : commands) {
            applyLog(command);
        }
    }
}
