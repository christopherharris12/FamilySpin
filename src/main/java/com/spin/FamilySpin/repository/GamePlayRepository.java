package com.spin.FamilySpin.repository;

import com.spin.FamilySpin.model.GamePlay;
import com.spin.FamilySpin.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GamePlayRepository extends JpaRepository<GamePlay, Long> {
    List<GamePlay> findByUser(User user);
    List<GamePlay> findBySessionNumber(int sessionNumber);
    Optional<GamePlay> findByUserAndSessionNumber(User user, int sessionNumber);
}
