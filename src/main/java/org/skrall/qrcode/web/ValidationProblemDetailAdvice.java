package org.skrall.qrcode.web;

import java.util.List;
import java.util.stream.Collectors;
import org.skrall.qrcode.LogoException;
import org.skrall.qrcode.QrEncodingException;
import org.skrall.qrcode.link.InvalidLinkException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns a rejected request into a problem detail that says what was wrong with it.
 *
 * <p>Boot answers a body that fails its constraints with a detail of "Invalid request content."
 * and discards the constraint messages, which are the only part a caller can act on. This puts
 * them back, and gives the two domain failures — an unusable logo and a payload that does not fit
 * — the same shape.
 *
 * <p>The {@code errors} property names the field and the message but deliberately not the
 * rejected value: a logo is megabytes of base64, and echoing it back would make the error
 * document larger than the request.
 *
 * <p>Ordered ahead of Boot's own problem detail handling, which also claims
 * {@code MethodArgumentNotValidException} and answers it with a generic detail. The first advice
 * to claim an exception wins, so without this the messages below would never reach a caller.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ValidationProblemDetailAdvice {

    /**
     * Names a rejected field and what is wrong with it.
     *
     * @param field   the request field that failed
     * @param message why it failed, already phrased for a caller to act on
     */
    public record FieldProblem(String field, String message) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onInvalidRequestBody(MethodArgumentNotValidException e) {
        List<FieldProblem> problems = e.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldProblem(fieldOf(error), error.getDefaultMessage()))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                problems.stream().map(FieldProblem::message).collect(Collectors.joining(" ")));
        problem.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        problem.setProperty("errors", problems);
        return problem;
    }

    @ExceptionHandler({ LogoException.class, QrEncodingException.class })
    public ProblemDetail onUnrenderable(RuntimeException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        return problem;
    }

    /**
     * A destination that is not an absolute http(s) URL, or an expiry that is already past.
     * The message says which, and is phrased for the person who typed it.
     */
    @ExceptionHandler(InvalidLinkException.class)
    public ProblemDetail onInvalidLink(InvalidLinkException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        return problem;
    }

    private static String fieldOf(ObjectError error) {
        return error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
    }
}
