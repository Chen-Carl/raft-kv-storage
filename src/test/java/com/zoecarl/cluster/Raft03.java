package com.zoecarl.cluster;

import com.zoecarl.raft.Raft;

public class Raft03 {
    public static void main(String[] args) {
        Raft node = new Raft();
        node.init(2, "./src/test/java/com/zoecarl/cluster/settings.txt");
    }
}