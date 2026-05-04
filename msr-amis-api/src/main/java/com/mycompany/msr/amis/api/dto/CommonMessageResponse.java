package com.mycompany.msr.amis.api.dto;

public record CommonMessageResponse(
        boolean success,
        String message
) {
}
