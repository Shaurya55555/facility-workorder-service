package com.shaurya.workorders.config;

import com.shaurya.workorders.user.AppUser;
import com.shaurya.workorders.user.Role;
import com.shaurya.workorders.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the bootstrap ADMIN (so the system is usable on first start) and, when
 * app.seed.demo-users=true, a manager and two technicians for demos. Idempotent.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    static final String DEMO_PASSWORD = "Password123!";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public DataSeeder(UserRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensure(props.seed().adminUsername(), props.seed().adminPassword(), Role.ADMIN);
        if (props.seed().demoUsers()) {
            ensure("manager1", DEMO_PASSWORD, Role.MANAGER);
            ensure("tech1", DEMO_PASSWORD, Role.TECHNICIAN);
            ensure("tech2", DEMO_PASSWORD, Role.TECHNICIAN);
        }
    }

    private void ensure(String username, String rawPassword, Role role) {
        if (users.existsByUsername(username)) {
            return;
        }
        users.save(new AppUser(username, encoder.encode(rawPassword), role));
        log.info("Seeded {} user '{}'", role, username);
    }
}
