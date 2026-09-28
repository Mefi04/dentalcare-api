package com.dentalcare.api.modules.patients.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Schema(description = "Request to update the authenticated patient's personal contact profile")
public class UpdatePatientProfileRequest {

    @JsonIgnore
    private final Set<String> presentFields = new HashSet<>();

    @Schema(description = "Primary contact phone", example = "5555-1234")
    @Size(max = 30, message = "Phone must not exceed 30 characters")
    private String phone;

    @Schema(description = "Contact email", example = "paciente@example.com")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Schema(description = "Residential address", example = "Zona 1, Ciudad de Guatemala")
    @Size(max = 255, message = "Address must not exceed 255 characters")
    private String address;

    @Schema(description = "Emergency contact full name", example = "Maria Perez")
    @Size(max = 150, message = "Emergency contact must not exceed 150 characters")
    private String emergencyContact;

    @Schema(description = "Emergency contact phone", example = "5555-9999")
    @Size(max = 30, message = "Emergency phone must not exceed 30 characters")
    private String emergencyPhone;

    public UpdatePatientProfileRequest() {
    }

    public UpdatePatientProfileRequest(String phone, String email, String address, String emergencyContact, String emergencyPhone) {
        setPhone(phone);
        setEmail(email);
        setAddress(address);
        setEmergencyContact(emergencyContact);
        setEmergencyPhone(emergencyPhone);
    }

    public String getPhone() {
        return phone;
    }

    @JsonSetter(value = "phone", nulls = Nulls.SET)
    public void setPhone(String phone) {
        this.phone = phone;
        this.presentFields.add("phone");
    }

    public String getEmail() {
        return email;
    }

    @JsonSetter(value = "email", nulls = Nulls.SET)
    public void setEmail(String email) {
        this.email = email;
        this.presentFields.add("email");
    }

    public String getAddress() {
        return address;
    }

    @JsonSetter(value = "address", nulls = Nulls.SET)
    public void setAddress(String address) {
        this.address = address;
        this.presentFields.add("address");
    }

    public String getEmergencyContact() {
        return emergencyContact;
    }

    @JsonSetter(value = "emergencyContact", nulls = Nulls.SET)
    public void setEmergencyContact(String emergencyContact) {
        this.emergencyContact = emergencyContact;
        this.presentFields.add("emergencyContact");
    }

    public String getEmergencyPhone() {
        return emergencyPhone;
    }

    @JsonSetter(value = "emergencyPhone", nulls = Nulls.SET)
    public void setEmergencyPhone(String emergencyPhone) {
        this.emergencyPhone = emergencyPhone;
        this.presentFields.add("emergencyPhone");
    }

    @JsonIgnore
    public boolean isPhonePresent() {
        return presentFields.contains("phone");
    }

    @JsonIgnore
    public boolean isEmailPresent() {
        return presentFields.contains("email");
    }

    @JsonIgnore
    public boolean isAddressPresent() {
        return presentFields.contains("address");
    }

    @JsonIgnore
    public boolean isEmergencyContactPresent() {
        return presentFields.contains("emergencyContact");
    }

    @JsonIgnore
    public boolean isEmergencyPhonePresent() {
        return presentFields.contains("emergencyPhone");
    }

    @JsonIgnore
    public Set<String> getPresentFields() {
        return Collections.unmodifiableSet(presentFields);
    }
}
