package com.spin.FamilySpin.config;

import com.spin.FamilySpin.model.User;
import com.spin.FamilySpin.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;

    public DataInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        ensureBootstrapAdmin();
    }

    public void ensureBootstrapAdmin() {
        if (userRepository.findByUsername("Daddy Chriss").isEmpty()) {
            User adminUser = new User("Daddy Chriss", "admin@ebenezer.com", "anysie123", "Daddy Chriss");
            adminUser.setAdmin(true);
            userRepository.save(adminUser);
            System.out.println("✓ Admin user 'Daddy Chriss' created successfully");
        }
    }
}
