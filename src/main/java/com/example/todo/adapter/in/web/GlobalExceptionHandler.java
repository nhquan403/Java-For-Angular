package com.example.todo.adapter.in.web;

import com.example.todo.application.common.InvalidPageQueryException;
import com.example.todo.application.common.TooManySubscriptionsException;
import com.example.todo.domain.exception.EmailAlreadyUsedException;
import com.example.todo.domain.exception.ForbiddenOperationException;
import com.example.todo.domain.exception.InvalidCredentialsException;
import com.example.todo.domain.exception.InvalidRefreshTokenException;
import com.example.todo.domain.exception.InvalidTodoException;
import com.example.todo.domain.exception.InvalidUserException;
import com.example.todo.domain.exception.TodoConflictException;
import com.example.todo.domain.exception.TodoNotFoundException;
import com.example.todo.domain.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chuyển exception thành HTTP response chuẩn RFC 9457 (ProblemDetail).
 *
 * Kế thừa ResponseEntityExceptionHandler để Spring tự xử lý các lỗi MVC có sẵn
 * (JSON sai cú pháp, id không phải số, sai method, không có đường dẫn...) với cùng định dạng.
 * Nhờ đó handler "bắt mọi lỗi" bên dưới không biến 404/405/400 thành 500.
 *
 * Lỗi 401 và 403 do Spring Security phát sinh trong filter được xử lý ở ProblemJsonSecurityHandlers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({TodoNotFoundException.class, UserNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({InvalidTodoException.class, InvalidUserException.class, InvalidPageQueryException.class})
    public ProblemDetail handleInvalid(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({TodoConflictException.class, EmailAlreadyUsedException.class})
    public ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Sai email/mật khẩu hoặc refresh token không dùng được: 401 với thông báo chung. */
    @ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class})
    public ResponseEntity<ProblemDetail> handleUnauthorized(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    /** Mở quá nhiều kết nối thời gian thực. Client nên đóng bớt kết nối cũ rồi thử lại. */
    @ExceptionHandler(TooManySubscriptionsException.class)
    public ProblemDetail handleTooMany(TooManySubscriptionsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ProblemDetail handleForbidden(ForbiddenOperationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * Lưới an toàn cuối cùng: ghi log đầy đủ, nhưng không lộ chi tiết nội bộ cho client.
     * Lỗi bảo mật của Spring Security phải được ném lại để filter của nó xử lý (401/403),
     * nếu không sẽ bị nuốt thành 500.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error, please try again later");
    }

    /** Thêm danh sách lỗi theo từng field vào response 400 khi @Valid thất bại. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ex.getBody();
        problem.setDetail("Validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }
}
