package com.juryopt.orchestrator.exception;

public class DuplicateRegistrationFaultException extends DuplicateResourceException {

    private final String studentMatricule;
    private final String existingRegistryCode;

    public DuplicateRegistrationFaultException(String studentMatricule,
                                               String existingRegistryCode,
                                               String message) {
        super(message);
        this.studentMatricule = studentMatricule;
        this.existingRegistryCode = existingRegistryCode;
    }

    public String getStudentMatricule() {
        return studentMatricule;
    }

    public String getExistingRegistryCode() {
        return existingRegistryCode;
    }
}