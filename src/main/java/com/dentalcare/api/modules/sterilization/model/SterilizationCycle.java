package com.dentalcare.api.modules.sterilization.model;

import com.dentalcare.api.modules.inventory.model.InventoryItem;
import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "sterilization_cycles")
public class SterilizationCycle {
    @Id @Column(nullable=false, updatable=false) private UUID id;
    @Column(nullable=false, unique=true, length=50) private String code;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="protocol_id", nullable=false) private SterilizationProtocol protocol;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="responsible_user_id", nullable=false) private User responsible;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private SterilizationCycleStatus status;
    @Column(nullable=false, length=500) private String observations;
    @Column(name="started_at", nullable=false, updatable=false) private Instant startedAt;
    @Column(name="released_at") private Instant releasedAt;
    @ManyToMany(fetch=FetchType.LAZY)
    @JoinTable(name="sterilization_cycle_instruments", joinColumns=@JoinColumn(name="cycle_id"), inverseJoinColumns=@JoinColumn(name="inventory_item_id"))
    private Set<InventoryItem> instruments = new LinkedHashSet<>();

    protected SterilizationCycle() {}
    public SterilizationCycle(UUID id,String code,SterilizationProtocol protocol,User responsible,SterilizationCycleStatus status,
            String observations,Instant startedAt,Instant releasedAt,Set<InventoryItem> instruments){
        this.id=id;this.code=code;this.protocol=protocol;this.responsible=responsible;this.status=status;
        this.observations=observations;this.startedAt=startedAt;this.releasedAt=releasedAt;
        this.instruments=instruments!=null?new LinkedHashSet<>(instruments):new LinkedHashSet<>();
    }
    public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
    public String getCode(){return code;} public void setCode(String code){this.code=code;}
    public SterilizationProtocol getProtocol(){return protocol;} public void setProtocol(SterilizationProtocol protocol){this.protocol=protocol;}
    public User getResponsible(){return responsible;} public void setResponsible(User responsible){this.responsible=responsible;}
    public SterilizationCycleStatus getStatus(){return status;} public void setStatus(SterilizationCycleStatus status){this.status=status;}
    public String getObservations(){return observations;} public void setObservations(String observations){this.observations=observations;}
    public Instant getStartedAt(){return startedAt;} public void setStartedAt(Instant startedAt){this.startedAt=startedAt;}
    public Instant getReleasedAt(){return releasedAt;} public void setReleasedAt(Instant releasedAt){this.releasedAt=releasedAt;}
    public Set<InventoryItem> getInstruments(){return instruments;} public void setInstruments(Set<InventoryItem> instruments){this.instruments=instruments;}
    @Override public boolean equals(Object o){return this==o || o instanceof SterilizationCycle c && Objects.equals(id,c.id);}
    @Override public int hashCode(){return Objects.hashCode(id);}
}
