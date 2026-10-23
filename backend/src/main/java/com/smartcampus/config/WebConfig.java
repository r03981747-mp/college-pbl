package com.smartcampus.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.smartcampus.security.AuthInterceptor;

/**
 * MVC configuration of the application.
 *
 * <h2>1. Security filter</h2>
 * Registers {@link AuthInterceptor} for every {@code /api/**} request, so no
 * controller can ever be reached without a valid session token.
 *
 * <h2>2. CORS</h2>
 * The front end is normally opened with the VS Code "Live Server" extension on
 * another port (5500).  CORS tells the browser that calling
 * {@code http://localhost:8080/api/...} from that page is allowed.
 *
 * <h2>3. Static front end (optional convenience)</h2>
 * The {@code frontend/} folder is also published at {@code /}, so
 * {@code http://localhost:8080} alone is enough to run the whole demo - no
 * second web server and no CORS at all.  Opening {@code frontend/login.html}
 * through Live Server works exactly the same way.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Autowired
    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /** Every /api/** request must pass authentication first. */
    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    /** Allow the Live Server origin to call the API. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /** Publishes ../frontend (or ./frontend) as the application's web root. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        resolveFrontendDirectory().ifPresent(directory ->
                registry.addResourceHandler("/**")
                        .addResourceLocations(directory.toUri().toString()));
    }

    /** Works whether the process was started from the project root or backend/. */
    private java.util.Optional<Path> resolveFrontendDirectory() {
        for (String candidate : new String[]{"frontend", "../frontend", "../../frontend"}) {
            Path path = Paths.get(candidate).toAbsolutePath().normalize();
            if (Files.isDirectory(path) && Files.isRegularFile(path.resolve("login.html"))) {
                return java.util.Optional.of(path);
            }
        }
        return java.util.Optional.empty();
    }

    /** Exposed so the home controller can report where the UI was found. */
    @Bean
    public FrontendLocator frontendLocator() {
        return new FrontendLocator(resolveFrontendDirectory().orElse(null));
    }

    /** Small value object describing the static front end location. */
    public record FrontendLocator(Path directory) {
        public boolean isPresent() {
            return directory != null;
        }
    }
}
