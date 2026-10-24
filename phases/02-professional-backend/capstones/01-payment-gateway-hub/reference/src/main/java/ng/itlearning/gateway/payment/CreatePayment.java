package ng.itlearning.gateway.payment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Request to initialise a payment. Amounts are integers in minor units (kobo, cents, pesewas). */
public record CreatePayment(
        @NotNull @Min(100) Long amount,
        @NotBlank @Pattern(regexp = "NGN|GHS|KES|USD", message = "must be one of NGN, GHS, KES, USD") String currency,
        @NotBlank @Email String email) {
}
