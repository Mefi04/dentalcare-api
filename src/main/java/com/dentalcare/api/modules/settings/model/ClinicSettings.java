package com.dentalcare.api.modules.settings.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinic_settings")
public class ClinicSettings {
    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;
    @Column(name = "trade_name", length = 150) private String tradeName;
    @Column(length = 20) private String nit;
    @Column(length = 30) private String phone;
    @Column(length = 255) private String email;
    @Column(length = 255) private String address;
    @Column(length = 100) private String city;
    @Column(name = "business_hours", length = 255) private String businessHours;
    @Column(name = "receipt_prefix", length = 20) private String receiptPrefix;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ClinicSettings() {}

    public Short getId() { return id; }
    public String getTradeName() { return tradeName; }
    public String getNit() { return nit; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getBusinessHours() { return businessHours; }
    public String getReceiptPrefix() { return receiptPrefix; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setTradeName(String value) { tradeName = value; }
    public void setNit(String value) { nit = value; }
    public void setPhone(String value) { phone = value; }
    public void setEmail(String value) { email = value; }
    public void setAddress(String value) { address = value; }
    public void setCity(String value) { city = value; }
    public void setBusinessHours(String value) { businessHours = value; }
    public void setReceiptPrefix(String value) { receiptPrefix = value; }
    public void setUpdatedBy(UUID value) { updatedBy = value; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}
