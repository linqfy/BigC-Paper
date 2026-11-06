package dev.linqfy.bigCasares.modules.tideframe.engine.core;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
public final class OperationGate {
    public record Ticket(UUID owner,long generation){}
    private final AtomicLong sequence=new AtomicLong();
    private final ConcurrentHashMap<UUID,Long> owners=new ConcurrentHashMap<>();
    public Ticket begin(UUID owner){long value=sequence.incrementAndGet();owners.put(owner,value);return new Ticket(owner,value);}
    public boolean current(Ticket ticket){return Long.valueOf(ticket.generation()).equals(owners.get(ticket.owner()));}
    public void invalidate(UUID owner){begin(owner);}
    public void forget(UUID owner){owners.remove(owner);}
    public void clear(){owners.clear();}
}
