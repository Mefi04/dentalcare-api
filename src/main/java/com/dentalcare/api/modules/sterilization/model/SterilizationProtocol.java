package com.dentalcare.api.modules.sterilization.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sterilization_protocols")
public class SterilizationProtocol {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @Column(nullable = false, unique = true, length = 150) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SterilizationMethod method;
    @Column(length = 500) private String description;
    @Column(nullable = false, length = 2000) private String instructions;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected SterilizationProtocol() {}
    public SterilizationProtocol(UUID id, String name, SterilizationMethod method, String description,
            String instructions, boolean active, Instant createdAt, Instant updatedAt) {
        this.id=id; this.name=name; this.method=method; this.description=description;
        this.instructions=instructions; this.active=active; this.createdAt=createdAt; this.updatedAt=updatedAt;
    }
    public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
    public String getName(){return name;} public void setName(String name){this.name=name;}
    public SterilizationMethod getMethod(){return method;} public void setMethod(SterilizationMethod method){this.method=method;}
    public String getDescription(){return description;} public void setDescription(String description){this.description=description;}
    public String getInstructions(){return instructions;} public void setInstructions(String instructions){this.instructions=instructions;}
    public boolean isActive(){return active;} public void setActive(boolean active){this.active=active;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant createdAt){this.createdAt=createdAt;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant updatedAt){this.updatedAt=updatedAt;}
    @Override public boolean equals(Object o){return this==o || o instanceof SterilizationProtocol p && Objects.equals(id,p.id);}
    @Override public int hashCode(){return Objects.hashCode(id);}
}
