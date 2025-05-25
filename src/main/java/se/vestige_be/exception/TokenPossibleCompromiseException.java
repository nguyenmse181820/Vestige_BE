package se.vestige_be.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class TokenPossibleCompromiseException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String familyId;

    public TokenPossibleCompromiseException(String token, String familyId, String message) {
        super(String.format("Security alert for token [%s]: %s", token, message));
        this.familyId = familyId;
    }

    public String getFamilyId() {
        return familyId;
    }
}