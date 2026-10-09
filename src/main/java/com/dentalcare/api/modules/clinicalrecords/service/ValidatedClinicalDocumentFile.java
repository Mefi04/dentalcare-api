package com.dentalcare.api.modules.clinicalrecords.service;

public record ValidatedClinicalDocumentFile(String fileName, String contentType, long fileSize) { }
