package io.portfolio.urlshortener.auth;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Injects an {@link AuthenticatedUser} into controller methods. The filter
 * ({@link JwtAuthenticationFilter}) stashes the object on the request as an
 * attribute; this resolver pulls it back out. If none is present (e.g., a
 * public endpoint was hit with a bad token), we throw
 * {@link InvalidTokenException} so the GlobalExceptionHandler answers 401.
 *
 * <p>Instantiated directly by {@link WebMvcConfig} — deliberately not a
 * component, so {@code @WebMvcTest} slices work without extra beans.
 */
public class AuthenticatedUserResolver implements HandlerMethodArgumentResolver {

    static final String ATTRIBUTE = "auth.currentUser";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthenticatedUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Object user = webRequest.getAttribute(ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
        if (user == null) {
            throw new InvalidTokenException("authentication required");
        }
        return user;
    }
}
