package com.dentalcare.api.modules.settings.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "procedure_catalog_items")
public class ProcedureCatalogItem {
    @Id private UUID id;
    @Column(nullable = false, length = 50) private String code;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, length = 100) private String category;
    @Column(name = "duration_minutes", nullable = false) private Integer durationMinutes;
    @Column(name = "base_price", nullable = false, precision = 12, scale = 2) private BigDecimal basePrice;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ProcedureCatalogItemStatus status;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ProcedureCatalogItem() {}
    public ProcedureCatalogItem(UUID id, String code, String name, String category, Integer durationMinutes,
                                BigDecimal basePrice, ProcedureCatalogItemStatus status, UUID createdBy,
                                UUID updatedBy, Instant createdAt, Instant updatedAt) {
        this.id=id; this.code=code; this.name=name; this.category=category; this.durationMinutes=durationMinutes;
        this.basePrice=basePrice; this.status=status; this.createdBy=createdBy; this.updatedBy=updatedBy;
        this.createdAt=createdAt; this.updatedAt=updatedAt;
    }
    public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;}
    public String getCategory(){return category;} public Integer getDurationMinutes(){return durationMinutes;}
    public BigDecimal getBasePrice(){return basePrice;} public ProcedureCatalogItemStatus getStatus(){return status;}
    public UUID getCreatedBy(){return createdBy;} public UUID getUpdatedBy(){return updatedBy;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public void setCode(String v){code=v;} public void setName(String v){name=v;} public void setCategory(String v){category=v;}
    public void setDurationMinutes(Integer v){durationMinutes=v;} public void setBasePrice(BigDecimal v){basePrice=v;}
    public void setStatus(ProcedureCatalogItemStatus v){status=v;} public void setUpdatedBy(UUID v){updatedBy=v;}
    public void setUpdatedAt(Instant v){updatedAt=v;}
}
