package com.cadentia.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.admin.AdminUserRepository;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class DatabaseRbacAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void leavesAnonymousRequestsAnonymousSoTheyCanReceiveUnauthorizedResponse() throws Exception {
        // Arrange
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anonymous);
        AtomicBoolean repositoryLookedUpAnonymousUser = new AtomicBoolean();
        AdminUserRepository repository = (AdminUserRepository) Proxy.newProxyInstance(
                AdminUserRepository.class.getClassLoader(),
                new Class<?>[] {AdminUserRepository.class},
                (proxy, method, args) -> {
                    if ("findByExternalSubject".equals(method.getName())) {
                        repositoryLookedUpAnonymousUser.set(true);
                        return Optional.empty();
                    }
                    return null;
                });
        MockFilterChain chain = new MockFilterChain();

        // Act
        new DatabaseRbacAuthenticationFilter(repository, "goshem")
                .doFilter(new MockHttpServletRequest("GET", "/admin/session"), new MockHttpServletResponse(), chain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(anonymous);
        assertThat(repositoryLookedUpAnonymousUser.get()).isFalse();
        assertThat(chain.getRequest()).isNotNull();
    }
}
