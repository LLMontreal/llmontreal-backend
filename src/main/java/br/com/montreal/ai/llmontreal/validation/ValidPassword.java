package br.com.montreal.ai.llmontreal.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {
    String message() default "Senha deve conter pelo menos uma letra maiúscula, uma minúscula, um número e ter no mínimo 8 caracteres";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
