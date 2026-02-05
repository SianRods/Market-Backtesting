package com.rods.backtestingstrategies.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationRequest {
    @jakarta.validation.constraints.NotBlank(message = "Username cannot be blank")
    private String username;

    @jakarta.validation.constraints.NotBlank(message = "Password cannot be blank")
    private String password;
}
