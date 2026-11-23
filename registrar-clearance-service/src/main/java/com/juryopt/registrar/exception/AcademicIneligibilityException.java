package com.juryopt.registrar.exception;

import com.juryopt.registrar.ws.gen.AcademicIneligibilityFault;

public class AcademicIneligibilityException extends RegistrarFaultException {

    public AcademicIneligibilityException(String matricule, String reason,
                                          int creditsEarned, int requiredCredits, String message) {
        super(message, buildFault(matricule, reason, creditsEarned, requiredCredits, message));
    }

    private static AcademicIneligibilityFault buildFault(String matricule, String reason,
                                                         int creditsEarned, int requiredCredits, String message) {
        AcademicIneligibilityFault fault = new AcademicIneligibilityFault();
        fault.setStudentMatricule(matricule);
        fault.setReason(reason);
        fault.setCreditsEarned(creditsEarned);
        fault.setRequiredCredits(requiredCredits);
        fault.setMessage(message);
        return fault;
    }
}