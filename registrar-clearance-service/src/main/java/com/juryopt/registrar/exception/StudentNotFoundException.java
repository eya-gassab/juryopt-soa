package com.juryopt.registrar.exception;

import com.juryopt.registrar.ws.gen.StudentNotFoundFault;

public class StudentNotFoundException extends RegistrarFaultException {

    public StudentNotFoundException(String matricule) {
        super("Student not found: " + matricule, buildFault(matricule));
    }

    private static StudentNotFoundFault buildFault(String matricule) {
        StudentNotFoundFault fault = new StudentNotFoundFault();
        fault.setStudentMatricule(matricule);
        fault.setMessage("Aucun dossier académique trouvé pour le matricule " + matricule);
        return fault;
    }
}