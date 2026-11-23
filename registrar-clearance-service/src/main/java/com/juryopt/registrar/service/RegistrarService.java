package com.juryopt.registrar.service;

import com.juryopt.registrar.domain.OfficialDefenseRegistry;
import com.juryopt.registrar.domain.StudentAcademicRecord;
import com.juryopt.registrar.exception.AcademicIneligibilityException;
import com.juryopt.registrar.exception.DuplicateRegistrationException;
import com.juryopt.registrar.exception.StudentNotFoundException;
import com.juryopt.registrar.repository.OfficialDefenseRegistryRepository;
import com.juryopt.registrar.repository.StudentAcademicRecordRepository;
import com.juryopt.registrar.ws.gen.RegisterOfficialDefenseRequest;
import com.juryopt.registrar.ws.gen.RegisterOfficialDefenseResponse;
import com.juryopt.registrar.ws.gen.VerifyStudentEligibilityResponse;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.UUID;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrarService {

    private final StudentAcademicRecordRepository studentRepository;
    private final OfficialDefenseRegistryRepository registryRepository;

    public RegistrarService(StudentAcademicRecordRepository studentRepository,
                            OfficialDefenseRegistryRepository registryRepository) {
        this.studentRepository = studentRepository;
        this.registryRepository = registryRepository;
    }

    @Transactional(readOnly = true)
    public VerifyStudentEligibilityResponse verifyEligibility(String studentMatricule) {
        StudentAcademicRecord student = studentRepository.findById(studentMatricule)
                .orElseThrow(() -> new StudentNotFoundException(studentMatricule));

        if (student.isHasAdminHold()) {
            throw new AcademicIneligibilityException(
                    studentMatricule,
                    "ADMIN_HOLD",
                    student.getCreditsEarned(),
                    student.getRequiredCredits(),
                    "Blocage administratif actif pour l'étudiant " + studentMatricule
            );
        }

        if (student.getCreditsEarned() < student.getRequiredCredits()) {
            throw new AcademicIneligibilityException(
                    studentMatricule,
                    "INSUFFICIENT_CREDITS",
                    student.getCreditsEarned(),
                    student.getRequiredCredits(),
                    "Crédits insuffisants (" + student.getCreditsEarned() + "/" + student.getRequiredCredits() + ")"
            );
        }

        VerifyStudentEligibilityResponse response = new VerifyStudentEligibilityResponse();
        response.setStudentMatricule(student.getStudentMatricule());
        response.setFullName(student.getFullName());
        response.setCreditsEarned(student.getCreditsEarned());
        response.setRequiredCredits(student.getRequiredCredits());
        response.setClearanceToken("CLR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return response;
    }

    @Transactional
    public RegisterOfficialDefenseResponse registerOfficialDefense(RegisterOfficialDefenseRequest request) {
        String matricule = request.getStudentMatricule();

        if (!studentRepository.existsById(matricule)) {
            throw new StudentNotFoundException(matricule);
        }

        registryRepository.findByStudentMatricule(matricule).ifPresent(existing -> {
            throw new DuplicateRegistrationException(matricule, existing.getRegistryCode());
        });

        long nextSequence = registryRepository.count() + 1;
        String registryCode = String.format("REG-%d-%04d", Year.now().getValue(), nextSequence);
        LocalDateTime now = LocalDateTime.now();

        OfficialDefenseRegistry entry = new OfficialDefenseRegistry(
                registryCode,
                matricule,
                request.getClearanceToken(),
                request.getSlotCode(),
                request.getRoomCode(),
                request.getJurySummary(),
                now
        );
        registryRepository.save(entry);

        RegisterOfficialDefenseResponse response = new RegisterOfficialDefenseResponse();
        response.setRegistryCode(registryCode);
        response.setRegisteredAt(toXmlGregorianCalendar(now));
        return response;
    }

    private XMLGregorianCalendar toXmlGregorianCalendar(LocalDateTime dateTime) {
        try {
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(dateTime.toString());
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException("Failed to convert dateTime to XMLGregorianCalendar", e);
        }
    }
}