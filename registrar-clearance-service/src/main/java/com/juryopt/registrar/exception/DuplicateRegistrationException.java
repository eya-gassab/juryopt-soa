package com.juryopt.registrar.exception;

import com.juryopt.registrar.ws.gen.DuplicateRegistrationFault;

public class DuplicateRegistrationException extends RegistrarFaultException {

    public DuplicateRegistrationException(String matricule, String existingRegistryCode) {
        super("Defense already registered for " + matricule,
                buildFault(matricule, existingRegistryCode));
    }

    private static DuplicateRegistrationFault buildFault(String matricule, String existingRegistryCode) {
        DuplicateRegistrationFault fault = new DuplicateRegistrationFault();
        fault.setStudentMatricule(matricule);
        fault.setExistingRegistryCode(existingRegistryCode);
        fault.setMessage("Soutenance déjà enregistrée sous le code " + existingRegistryCode);
        return fault;
    }
}