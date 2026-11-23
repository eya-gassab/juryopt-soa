package com.juryopt.registrar.exception;

public abstract class RegistrarFaultException extends RuntimeException {

    private final Object faultDetail;

    protected RegistrarFaultException(String message, Object faultDetail) {
        super(message);
        this.faultDetail = faultDetail;
    }

    public Object getFaultDetail() {
        return faultDetail;
    }
}