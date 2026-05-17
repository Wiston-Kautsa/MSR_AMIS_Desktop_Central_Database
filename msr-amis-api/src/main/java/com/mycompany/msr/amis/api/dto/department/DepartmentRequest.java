package com.mycompany.msr.amis.api.dto.department;

import jakarta.validation.constraints.NotBlank;

public record DepartmentRequest(@NotBlank String name) {
}
