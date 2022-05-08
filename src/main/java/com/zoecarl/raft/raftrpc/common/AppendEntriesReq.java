package com.zoecarl.raft.raftrpc.common;

import com.zoecarl.common.LogEntry;

public class AppendEntriesReq extends Request {
    private int term;
    private String leaderId;
    private int prevLogIndex;
    private int prevLogTerm;
    private int leaderCommit;
    private LogEntry[] entries;
    
    public AppendEntriesReq(int term, String serverId) {
        super(serverId);
        this.term = term;
    }

    public LogEntry[] getEntries() {
        return entries;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public int getTerm() {
        return term;
    }

    @Override
    public String toString() {
        return "AppendEntriesReq {\n\tterm=" + term + ", \n\tleaderId=" + leaderId + ", \n\tprevLogIndex=" + prevLogIndex + ", \n\tprevLogTerm=" + prevLogTerm + ", \n\tleaderCommit=" + leaderCommit + ", \n\tentries=" + entries + "\n}";
    }
}
